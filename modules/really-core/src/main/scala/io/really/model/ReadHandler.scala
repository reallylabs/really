/**
 * Copyright (C) 2014-2015 Really Inc. <http://really.io>
 */
package io.really.model

import akka.actor.{ ActorRef, Stash, Actor, ActorLogging }
import akka.pattern.{ AskTimeoutException, ask }
import akka.util.Timeout

import _root_.io.really.json.collection.JSONCollection
import _root_.io.really.model.persistent.ModelRegistry.ModelResult
import _root_.io.really.js.JsResultHelpers
import _root_.io.really.json.BSONFormats.BSONDocumentFormat
import _root_.io.really.model.persistent.ModelRegistry.RequestModel.GetModel
import _root_.io.really.protocol._
import _root_.io.really.Result.{ ReadResult, GetResult }
import io.really._
import _root_.io.really.rql.RQL._
import _root_.io.really.rql.RQLTokens.PaginationToken
import _root_.io.really.rql.writes.mongo.MongoWrites._
import _root_.io.really.gorilla.SubscriptionManager.SubscribeOnQuery
import play.api.libs.json._
import reactivemongo.api.Cursor
import reactivemongo.core.commands.Count
import reactivemongo.core.errors.DatabaseException

import scala.concurrent.Future
import scala.util.control.NonFatal

class ReadHandler(globals: ReallyGlobals) extends Actor with Stash with ActorLogging {

  implicit val ec = context.dispatcher

  implicit val timeout = Timeout(globals.config.CollectionActorConfig.waitForModel)

  private var models: Map[R, Model] = Map.empty

  private def collection(r: R): JSONCollection =
    globals.mongodbConnection.collection[JSONCollection](r.collectionName)

  def receive: Receive = handleRequests

  def handleRequests: Receive = {
    case g @ Request.Get(ctx, r, cmdOpts) =>
      log.info("Get Request: {}", g)
      val requester = sender()
      getModel(r) map {
        case Right(model) =>
          handleGet(model, ctx, r, collection(r), cmdOpts) map (requester ! _.merge)
        case Left(error) => requester ! error
      }
    case Request.Read(ctx, r, cmdOpts, pushChannel) =>
      val requester = sender()
      getModel(r) map {
        case Right(model) =>
          handleRead(model, ctx, r, collection(r), cmdOpts) map {
            result =>
              val readResult = ReadResult(r, result, None)
              if (cmdOpts.subscribe)
                //forward results to subscription manager
                globals.subscriptionManager ! SubscribeOnQuery(requester, ctx, getQuery(r, cmdOpts), readResult)
              else
                requester ! readResult
          } recover {
            case NonFatal(e) =>
              log.error(s"Unexpected error happened during querying resource $r with query ${cmdOpts.query}, error: ", e)
              requester ! CommandError.InternalServerError(s"Unexpected error happened during querying the database, error: $e")
          }
        case Left(error) => requester ! error
      }
  }

  /**
   * Returns the Model object of the provided r if exist or commandError if we couldn't fetch the model object
   * @param r
   * @return
   */
  private def getModel(r: R): Future[Either[CommandError, Model]] =
    models.get(r.skeleton) match {
      case Some(m) => Future successful Right(m)
      case None =>
        fetchModel(r) map {
          case Right(m) =>
            models += (r.skeleton -> m)
            Right(m)
          case left @ Left(error) =>
            left
        }
    }

  private def fetchModel(r: R): Future[Either[CommandError, Model]] = {
    (globals.modelRegistry ? GetModel(r, self)).map {
      case ModelResult.ModelObject(model, _) => Right(model)
      case ModelResult.ModelNotFound => Left(CommandError.ModelNotFound)
      case ModelResult.ModelFetchError(r, reason) =>
        Left(CommandError.InternalServerError(s"An error occurred while fetching the model of $r: $reason"))
    }.
      recover {
        case e: AskTimeoutException =>
          log.debug(s"ReadHandler timed out waiting for the model object of $r")
          Left(CommandError.InternalServerError(s"ReadHandler timed out waiting for the model object of $r"))
        case NonFatal(e) =>
          log.error(e, s"Unexpected error while getting the model instance for $r")
          Left(CommandError.InternalServerError(s"Unexpected error while getting the model instance for $r"))
      }
  }

  /**
   * get object with specific r from Projection DB
   * @param r is represent r for object
   * @return Future[ObjectResult]
   */
  private def handleGet(model: Model, ctx: RequestContext, r: R,
    collection: JSONCollection, cmdOpts: GetOpts): Future[Either[CommandError, GetResult]] = {
    val requestedFields = getRequestFields(cmdOpts.fields, model)
    val filter = getFieldsObj(model, requestedFields)

    val cursor: Cursor[JsObject] = collection.find(Json.obj("_r" -> r), filter).cursor[JsObject]
    cursor.headOption map {
      case Some(doc) if (doc \ "_deleted").asOpt[Boolean].isDefined =>
        //deleted document
        Left(CommandError.ObjectGone(r))
      case Some(doc) =>
        val _r = (doc \ "_r").as[R]
        model.executeOnGet(ctx, globals, doc) match {
          case Right(plan) =>
            val itemData = evaluateCalculatedFields(doc, requestedFields, model)
            Right(GetResult(
              _r,
              addSystemFields(doc, normalizeObject(itemData, requestedFields, plan.hidden)),
              accessibleFields(cmdOpts.fields, plan.hidden)
            ))
          case Left(terminated) =>
            Left(CommandError.JSValidationFailed(_r, terminated.message))
        }
      case None => Left(CommandError.ObjectNotFound(r))
    } recover {
      case e: DatabaseException =>
        log.error(s"Database Exception error happened during getting $r, error: ", e)
        Left(CommandError.InternalServerError(s"Database Exception error happened during getting $r, error: $e"))
      case NonFatal(e) =>
        log.error(s"Unexpected error happened during getting $r, error: ", e)
        Left(CommandError.InternalServerError(s"Unexpected error happened during getting $r, error: $e"))
    }
  }

  /**
   * returns from the database the objects that match specific query
   * @param model
   * @param ctx
   * @param r
   * @param collection
   * @param cmdOpts
   * @return
   */
  private def handleRead(model: Model, ctx: RequestContext, r: R,
    collection: JSONCollection, cmdOpts: ReadOpts): Future[ReadResponseBody] = {
    val newQuery = getQuery(r, cmdOpts)
    val queryWithoutPaginationOrDeleted = getQueryWithNoPagination(r, cmdOpts)
    for {
      items <- queryItems(model, collection, ctx, r, cmdOpts.copy(query = newQuery))
      count <- getTotalCount(collection, cmdOpts.copy(query = queryWithoutPaginationOrDeleted))
    } yield ReadResponseBody(getTokens(items), count, items)
  }

  private def getQueryWithNoPagination(collectionR: R, cmdOpts: ReadOpts): Query = (cmdOpts.query and getInitialQuery(collectionR)).excludeDeleted

  private def getQuery(collectionR: R, cmdOpts: ReadOpts): Query = {
    val query = cmdOpts.query and getInitialQuery(collectionR)
    cmdOpts.paginationToken match {
      //prevToken
      case Some(PaginationToken(id, 0)) =>
        query and SimpleQuery(Term("_r"), Operator.Gt, TermValue(Json.toJson(collectionR / id)))
      //nextToken
      case Some(PaginationToken(id, 1)) =>
        query and SimpleQuery(Term("_r"), Operator.Lt, TermValue(Json.toJson(collectionR / id)))
      case _ =>
        query
    }
  }

  /**
   * Return initial query that parse `r` to get parents indexes
   * @param r
   * @return
   */
  private def getInitialQuery(r: R): Query =
    r.tail.foldLeft(EmptyQuery: Query) {
      (acc, token) =>
        if (!token.isWildcard) SimpleQuery(
          Term(s"_parent${r.tail.indexOf(token)}"),
          Operator.Eq, TermValue(JsString(token.toString))
        )
        else acc
    }

  /**
   * Return list of items that match a specific query
   * @param model
   * @param collection
   * @param ctx
   * @param r
   * @param cmdOpts
   * @return
   */
  private def queryItems(model: Model, collection: JSONCollection,
    ctx: RequestContext, r: R, cmdOpts: ReadOpts): Future[List[ReadItem]] = {
    val requestedFields = getRequestFields(cmdOpts.fields, model)
    val filter = getFieldsObj(model, requestedFields)
    val orderBy = Json.obj("_id" -> (if (cmdOpts.ascending) 1 else -1))
    val cursor = collection.find(cmdOpts.query, filter).sort(orderBy).cursor[JsObject]
    cursor.collect[List](cmdOpts.limit).map { docs =>
      docs.collect {
        case doc: JsObject if (doc \ "_deleted").asOpt[Boolean].isEmpty =>
          model.executeOnGet(ctx, globals, doc) match {
            case Right(plan) =>
              val itemData = evaluateCalculatedFields(doc, requestedFields, model)
              Some(ReadItem(
                addSystemFields(doc, normalizeObject(itemData, requestedFields, plan.hidden)),
                Json.obj("fields" -> accessibleFields(cmdOpts.fields, plan.hidden))
              ))
            case Left(terminated) => None
          }
      }.flatten
    } recover {
      case e: Throwable =>
        log.error(e, s"Unexpected error happened during querying data with ${cmdOpts.query}")
        throw e
    }
  }

  /**
   * return all the model fields if the requested fields is empty
   * @param cmdFields
   * @param model
   * @return
   */
  private def getRequestFields(cmdFields: Set[FieldKey], model: Model): Set[FieldKey] =
    if (cmdFields.isEmpty) model.fields.keySet else cmdFields

  private def accessibleFields(requested: Set[FieldKey], hidden: Set[FieldKey]) = requested -- hidden

  private def evaluateCalculatedFields(doc: JsObject, fields: Set[FieldKey], model: Model): JsObject = {
    val modelFields = model.fields
    val jsResults = fields.map {
      case f if modelFields.get(f).isDefined && modelFields(f).isInstanceOf[CalculatedField[_]] =>
        modelFields(f).read(JsPath(), doc)
      case f if modelFields.get(f).isDefined =>
        JsSuccess(Json.obj(f -> (doc \ f)))
    }.toList

    JsResultHelpers.merge(jsResults) match {
      case JsSuccess(obj: JsObject, _) => obj
      case error: JsError =>
        log.warning(s"An error occurred while evaluating Calculated fields of $doc")
        doc
    }
  }

  /**
   * Return total count that match specific query
   * @param collection
   * @param cmdOpts
   * @return
   */
  private def getTotalCount(collection: JSONCollection, cmdOpts: ReadOpts): Future[Option[Int]] = {
    if (!cmdOpts.includeTotalCount) return Future.successful(None)
    val query = BSONDocumentFormat.partialReads(Json.toJson(cmdOpts.query).as[JsObject]).asOpt
    // query shouldn't include the token or the _deleted to get accurate results (it token is included)
    val command = Count(collection.name, query, None)
    globals.mongodbConnection.command(command).map(Option[Int])
  }

  /**
   * Return the pagination tokens (nextToken, prevToken)
   * @param items
   * @return
   */
  private def getTokens(items: List[ReadItem]): Option[ReadTokens] =
    if (items.isEmpty) None
    else {
      val nextToken = PaginationToken((items.last.body \ "_r").as[R].head.id.get, 1)
      val prevToken = PaginationToken((items.head.body \ "_r").as[R].head.id.get, 0)
      Some(ReadTokens(nextToken, prevToken))
    }

  /**
   * Remove hidden fields from the object
   * @param obj
   * @param fields requested fields list
   * @param hiddenFields
   * @return object without including hidden fields
   */
  private def normalizeObject(obj: JsObject, fields: Set[FieldKey], hiddenFields: Set[FieldKey]): JsObject =
    fields.foldLeft(Json.obj()) {
      case (newObj, key) if !hiddenFields.contains(key) => newObj + (key -> (obj \ key))
      case (newObj, key) => newObj
    }

  private def addSystemFields(storedDoc: JsObject, normalizedObject: JsObject): JsObject =
    normalizedObject ++ Json.obj("_r" -> (storedDoc \ "_r"), "_rev" -> (storedDoc \ "_rev").as[Long])

  /**
   * Get fields that will be queried from the Projection DB
   * @param model
   * @param fields
   * @return
   */
  private def getFieldsObj(model: Model, fields: Set[FieldKey]): JsObject = {
    fields.foldLeft(Json.obj("_r" -> 1, "_rev" -> 1, "_deleted" -> 1, "_metaData" -> 1)) {
      case (fieldsObj, field) =>
        model.fields.get(field) match {
          case Some(f: ActiveField[_]) => fieldsObj ++ Json.obj(field -> 1)
          case Some(f: CalculatedField[_]) => fieldsObj ++ f.dependsOn.foldLeft(Json.obj()) {
            case (acc, v) => acc ++ Json.obj(v -> 1)
          }
          //todo handle reference fields
          case _ => fieldsObj
        }
    }
  }

}
/**
 * Copyright (C) 2014-2015 Really Inc. <http://really.io>
 */
package io.really

import javax.script.{ ScriptException, ScriptContext, Invocable }
import _root_.io.really.js.JsTools
import _root_.io.really.model.ModelExceptions.{ InvalidSubCollectionR, InvalidCollectionR }
import play.api.libs.json._
import jdk.nashorn.api.scripting.NashornScriptEngineFactory
import scala.util.control.NonFatal

package object model {
  type ModelVersion = Long
  type JsScript = String
  type FieldKey = String

  case class CollectionMetadata(version: ModelVersion)

  case class FieldProtectionPlan(hidden: Set[FieldKey])

  /**
   * - Validate (cancel) – called before create and update
   * - OnGet (cancel/hide) – called on get and query/read
   * - PreUpdate (prev, after) -- cancel –– called before saving the update
   * - PreDelete (cancel)
   * - PostCreate
   * - PostUpdate
   * - PostDelete
   * @param onValidate
   */
  case class JsHooks(
    onValidate: Option[JsScript],
    preGet: Option[JsScript],
    preUpdate: Option[JsScript],
    preDelete: Option[JsScript],
    postCreate: Option[JsScript],
    postUpdate: Option[JsScript],
    postDelete: Option[JsScript]
  )

  case class MigrationPlan(scripts: Map[ModelVersion, JsScript]) {
    def migrate(from: ModelVersion, to: ModelVersion): JsObject => JsObject = ???
  }

  /**
   * Model Class defines and declares the schema and validators of a certain model
   * @param r
   * @param collectionMeta
   * @param fields
   * @param jsHooks
   * @param migrationPlan
   */
  case class Model(
      r: R,
      collectionMeta: CollectionMetadata,
      fields: Map[FieldKey, Field[_]],
      jsHooks: JsHooks,
      migrationPlan: MigrationPlan,
      subCollections: List[R]
  ) {

    if (!r.isCollection) throw new InvalidCollectionR(r)
    subCollections.foreach {
      r =>
        if (!(r.isCollection && r.tailR == this.r)) {
          throw new InvalidSubCollectionR(r)
        }
    }

    val factory = new NashornScriptEngineFactory
    val executeValidator: Option[Validator] = jsHooks.onValidate.map { onValidateCode =>
      val validateEngine = factory.getScriptEngine(Array("-strict", "--no-java", "--no-syntax-extensions"))
      JsTools.injectSDK(validateEngine.getContext.getBindings(ScriptContext.ENGINE_SCOPE))

      val codeTemplate =
        s"""
        | function validate (value) {
        |   var input = JSON.parse(value);
        |   value = undefined;
        |   ${onValidateCode}
        | }
      """.stripMargin
      validateEngine.eval(codeTemplate)
      //return the Invocable
      validateEngine.asInstanceOf[Invocable].getInterface(classOf[Validator])
    }

    val executeOnGetValidator: Option[OnGet] = jsHooks.preGet.map { onGetCode =>
      val jsEngine = JsTools.newEngineWithSDK()
      val code: String = s"""
        | function onGet (_authValue, _objValue, _underlyingHide) {
        |  var hide = function() {
        |    var fields = Array.prototype.slice.call(arguments);
        |    _underlyingHide(fields);
        |  }
        |
        |   var auth = JSON.parse(_authValue);
        |   _authValue = undefined;
        |   var obj = JSON.parse(_objValue);
        |   _objValue = undefined;
        |   ${onGetCode}
        | }
      """.stripMargin
      jsEngine.eval(code)

      jsEngine.asInstanceOf[Invocable].getInterface(classOf[OnGet])
    }

    def executeValidate(context: RequestContext, globals: ReallyGlobals, input: JsObject): ModelHookStatus = {
      executeValidator match {
        case Some(validator: Validator) =>
          try {
            validator.validate(input.toString)
            ModelHookStatus.Succeeded
          } catch {
            case te: ModelHookStatus.JSValidationError => te.terminated
            case se: ScriptException =>
              //TODO Log the error
              println("Validation Script Execution Error: " + se)
              ModelHookStatus.Terminated(500, "Validation script throws a runtime error")
          }
        case None => ModelHookStatus.Succeeded
      }
    }

    def executeOnGet(context: RequestContext, globals: ReallyGlobals, input: JsObject): Either[ModelHookStatus.Terminated, FieldProtectionPlan] = {
      executeOnGetValidator match {
        case Some(onGetValidator: OnGet) =>
          try {
            val hiddenFields = new HiddenFields
            onGetValidator.onGet(Json.toJson(context.auth).toString(), input.toString(), hiddenFields.hide)
            val protectedFields = hiddenFields.getHiddenFields.filter(fields.keySet.contains)
            Right(FieldProtectionPlan(protectedFields.toSet))
          } catch {
            case e: ArrayStoreException =>
              Left(ModelHookStatus.Terminated(9000, "preGet contain hide function with invalid args."))
            case se: ScriptException =>
              globals.logger.error(s"Script Exception happen during execute `preGet` for Model with r: ${r}, error: ${se.getMessage}")
              Left(ModelHookStatus.Terminated(500, "preGet script throws a runtime error"))
            case NonFatal(e) =>
              globals.logger.error(s"Script Exception happen during execute `preGet` for Model with r: ${r}, error: ${e.getMessage}")
              Left(ModelHookStatus.Terminated(500, "preGet script throws a runtime error"))
          }
        case None =>
          Right(FieldProtectionPlan(Set.empty))
      }

    }

    def executePreUpdate(context: RequestContext, globals: ReallyGlobals, prev: JsObject, after: JsObject, fieldsChanged: Set[FieldKey]): ModelHookStatus = ???

    def executePreDelete(context: RequestContext, globals: ReallyGlobals, obj: JsObject): ModelHookStatus = ???

    //Async hooks, does not block the rest of the execution plan
    //config and globals are passed to allow us to send a native "JavaScript SDK" to the script that may require to access other external systems
    def executePostCreate(config: ReallyConfig, globals: ReallyGlobals, context: RequestContext, created: JsObject): Unit = ???

    def executePostUpdate(config: ReallyConfig, globals: ReallyGlobals, context: RequestContext, updated: JsObject): Unit = ???

    def executePostDelete(config: ReallyConfig, globals: ReallyGlobals, context: RequestContext, updated: JsObject): Unit = ???
  }

  object Model {

    val RField = "_r"
    val RevisionField = "_rev"
    val DeletedField = "_deleted"

  }

}

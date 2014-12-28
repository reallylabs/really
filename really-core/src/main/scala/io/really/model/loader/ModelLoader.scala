/**
 * Copyright (C) 2014-2015 Really Inc. <http://really.io>
 */
package io.really.model.loader

import java.io.{ File, FileInputStream }
import java.nio.file.{ Path, Paths }
import java.util
import java.util.LinkedHashMap

import akka.actor.ActorSystem
import _root_.io.really.model._
import _root_.io.really.R
import org.yaml.snakeyaml.Yaml

import scala.collection.JavaConversions._
import scala.collection.immutable.TreeMap
import scala.io.Source

case class InvalidField(reason: String) extends Exception
case class InvalidReferenceField(reason: String) extends Exception
case class InvalidModelFile(reason: String) extends Exception

class ModelLoader(dir: String, actorSystem: ActorSystem) {

  private val yaml = new Yaml

  private val modelFileName = "model.yaml"

  private val directoryPath = Paths.get(dir)
  private val mainDirectoryFile = new File(dir)

  private val log = akka.event.Logging(actorSystem, "ModelLoader")

  private val nameRegx = """\p{javaJavaIdentifierStart}[\p{javaJavaIdentifierPart}-]*""".r
  private val migrationFileNameRegx = """evolution-\d+.\w+$"""

  /**
   * Load the models
   * @return list of models objects
   */

  private val modelsRegistry: Map[R, ModelInfo] = walkFilesTree(mainDirectoryFile).toMap

  val models: List[Model] = modelsRegistry.values.map {
    modelInfo =>
      Model(
        modelInfo.r,
        modelInfo.collectionMeta,
        getFields(modelInfo.fields),
        modelInfo.jsHooks,
        modelInfo.migrationPlan,
        modelInfo.subCollectionsR
      )

  }.toList

  /**
   * Walk through a directory and return modelInfo
   * @param file
   * @return
   */
  private def walkFilesTree(file: File): Iterable[(R, ModelInfo)] = {
    val children = new Iterable[File] {
      def iterator = if (file.isDirectory) file.listFiles.iterator else Iterator.empty
    }
    if (file.isFile && file.getName == modelFileName) {
      val modelInfo = readModelFile(file)
      Seq((modelInfo.r, modelInfo))
    } else if (file.isDirectory) children.flatMap(walkFilesTree(_))
    else Seq.empty
  }

  /**
   * Parse yaml file to get the model Info
   * @param file yaml file
   * @return model info
   */
  def readModelFile(file: File): ModelInfo = {
    //list list of subCollectionsR. It helps me to know what are the children of this collection
    val subCollectionPaths = file.toPath.getParent.toFile
      .listFiles.filter(_.isDirectory).map(f => getR(f.toPath)).toList
    try {
      val obj = yaml.load(new FileInputStream(file)).asInstanceOf[LinkedHashMap[String, AnyRef]]
      val fields = obj.get("fields").asInstanceOf[LinkedHashMap[String, Object]]
      val version = obj.get("version").toString.toLong
      val parentPath = file.toPath.getParent
      ModelInfo(getR(file.toPath.getParent), CollectionMetadata(version),
        fields, getJsHooks(parentPath),
        getMigrationPlan(parentPath),
        subCollectionPaths)
    } catch {
      case e: Exception =>
        log.error(e, s"Invalid yaml file; An error occurred while parsing this file $file")
        throw new InvalidModelFile(s"Invalid yaml file; An error occurred while parsing this file $file")
    }
  }

  /**
   * takes full path of a file and returns the R that represent this path
   * @param modelFilePath
   * @return
   */
  private def getR(modelFilePath: Path): R = {
    val relativePath = modelFilePath.toString.split(directoryPath.toString)(1)
    R(relativePath.replaceAll("(?!^)/", "/*/"))
  }

  /**
   * returns jsHooks object
   * @param parent
   * @return
   */
  private def getJsHooks(parent: Path): JsHooks = {
    def file(name: String): Option[File] = {
      val files = parent.toFile.listFiles.filter(_.getName.matches(name))
      if (!files.isEmpty) Some(files(0)) else None
    }
    JsHooks(
      readJsFile(file("""on-validate.\w+""")),
      readJsFile(file("""pre-get.\w+""")),
      readJsFile(file("""/pre-delete.\w+""")),
      readJsFile(file("""/pre-update.\w+""")),
      readJsFile(file("""/post-create.\w+""")),
      readJsFile(file("""/post-update.\w+""")),
      readJsFile(file(""""/post-delete.\w+"""))
    )
  }

  /**
   * reads javascript file and returns it as string
   * @param file
   * @return option of JsScript
   */
  private def readJsFile(file: Option[File]): Option[JsScript] =
    file match {
      case Some(f) => Some(Source.fromFile(f).mkString)
      case None => None
    }

  private def getMigrationPlan(parent: Path): MigrationPlan = {
    val evolutionFiles = parent.toFile.listFiles.filter(_.getName.matches(migrationFileNameRegx)).iterator
    val scripts = evolutionFiles map {
      file =>
        val matches = """\d+""".r.findAllIn(file.getName).matchData
        val version = matches.mkString
        readJsFile(Some(file)) match {
          case Some(f) => Some((version.toLong, f))
          case None => None
        }
    }
    MigrationPlan(scripts.flatten.toMap)
  }

  private def getFields(fieldsMap: LinkedHashMap[String, Object]): Map[FieldKey, Field[_]] = {
    val fields = fieldsMap.partition {
      case (fieldKey, value) =>
        val field = value.asInstanceOf[LinkedHashMap[String, String]]
        isValueField(field.get("type"))
    }
    val valueFields = parseValueFields(fields._1.toMap)

    val otherFields = fields._2.map {
      case (key, value) if (key.matches(nameRegx.toString)) =>
        (key, getFieldObject(valueFields, key, value.asInstanceOf[LinkedHashMap[String, String]]))
      case (key, value) =>
        throw new InvalidField(s"Field name $key didn't match $nameRegx")
    }
    valueFields ++ TreeMap(otherFields.toArray: _*)(Ordering.by(_.toLowerCase))
  }

  private lazy val dataTypes: Map[String, DataType[_]] = Map(
    "string" -> DataType.RString,
    "double" -> DataType.RDouble,
    "long" -> DataType.RLong,
    "boolean" -> DataType.RBoolean
  )

  private def isValueField(kind: String) = dataTypes.contains(kind.toLowerCase)

  private def parseValueFields(fields: Map[String, Object]): Map[FieldKey, ValueField[_]] = {
    val valueFields = fields map {
      case (k, v) =>
        if (k.matches(nameRegx.toString)) {
          val field = v.asInstanceOf[LinkedHashMap[String, String]]
          val required = field.get("required").asInstanceOf[Boolean]
          val default = Option(field.get("default"))
          val validation = Option(field.get("validation"))
          val kind = field.get("type")
          (k, ValueField(k, dataTypes(kind.toLowerCase), validation, default, required))
        } else
          throw new InvalidField(s"Field name $k didn't match $nameRegx")
    }
    TreeMap(valueFields.toArray: _*)(Ordering.by(_.toLowerCase))
  }

  /**
   * Validate the reference field data; make sure that the reference collection is exist
   * and validate that the fields are exist too
   * @param r
   * @param fields
   * @return
   */
  private def isValidReference(r: R, fields: List[String]): Boolean = {
    modelsRegistry.get(r) match {
      case Some(modelInfo) =>
        modelInfo.fields.keySet().containsAll(fields)
      case None =>
        log.error(s"Invalid collectionR $r value for the reference field")
        false
    }
  }

  /**
   * Read the field's data and returns Field object
   * @param valueFields All of Value Fields objects, required to generate calculated fields
   * @param fieldKey the Key name of this field
   * @param field the field's data as Map
   * @return Field Object
   */
  protected def getFieldObject(valueFields: Map[FieldKey, ValueField[_]], fieldKey: String,
    field: LinkedHashMap[String, String]): Field[_] = {
    val required = field.get("required").asInstanceOf[Boolean]

    field.get("type").toLowerCase match {
      case "reference" =>
        val fields = field.get("fields").asInstanceOf[util.ArrayList[String]].toList
        val r = R(field.get("collectionR"))

        if (isValidReference(r, fields))
          ReferenceField(fieldKey, required, r, fields)
        else
          throw new InvalidReferenceField(s"Invalid Reference field $fieldKey, please check that you have provided" +
            " a valid schema for this field")

      case "calculated" =>
        getCalculatedField(valueFields, fieldKey, field)

      case other =>
        throw new DataTypeException(s"Unsupported data type [$other] for $fieldKey field")

    }
  }

  /**
   * parse field data and return calculated field
   * @param valueFields
   * @param fieldKey
   * @param field
   * @return
   */
  private def getCalculatedField(valueFields: Map[FieldKey, ValueField[_]], fieldKey: String,
    field: LinkedHashMap[String, String]): Field[_] = {
    val dependencies = field.get("dependsOn").split(",")
    dependencies.length match {
      case 1 =>
        val dep1 = valueFields(dependencies(0).trim)
        CalculatedField1(fieldKey, dataTypes(field.get("valueType").toLowerCase), field.get("value"), dep1)
      case 2 =>
        val dep1 = valueFields(dependencies(0).trim)
        val dep2 = valueFields(dependencies(1).trim)
        CalculatedField2(fieldKey, dataTypes(field.get("valueType").toLowerCase), field.get("value"), dep1, dep2)
      case 3 =>
        val dep1 = valueFields(dependencies(0).trim)
        val dep2 = valueFields(dependencies(1).trim)
        val dep3 = valueFields(dependencies(2).trim)
        CalculatedField3(fieldKey, dataTypes(field.get("valueType").toLowerCase), field.get("value"), dep1, dep2, dep3)
      case _ =>
        throw new DataTypeException(s"Un supported type of calculated field; Maximum length " +
          s"of field's dependencies is 3")
    }
  }
}

/**
 * Define the model info
 * @param r
 * @param collectionMeta
 * @param fields
 * @param jsHooks
 * @param migrationPlan
 * @param subCollectionsR
 */
case class ModelInfo(
  r: R,
  collectionMeta: CollectionMetadata,
  fields: LinkedHashMap[String, Object],
  jsHooks: JsHooks,
  migrationPlan: MigrationPlan,
  subCollectionsR: List[R]
)

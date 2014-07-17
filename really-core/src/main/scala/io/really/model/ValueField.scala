package io.really.model

import javax.script.{ScriptException, Invocable}
import io.really.js.JsTools
import play.api.data.validation.ValidationError
import play.api.libs.json._
import java.lang.{Boolean => JBoolean}
import scala.util.{Failure, Success}

case class ValueField[T](
                          key: FieldKey,
                          dataType: DataType[T],
                          validationExpression: Option[JsScript],
                          default: Option[JsScript],
                          required: Boolean
                          ) extends ActiveField[T] {

  private[this] val validateExecutor: Option[Invocable] = validationExpression.map { onValidateCode =>
    val validateEngine = JsTools.newEngineWithSDK
    val codeTemplate =
      s"""
        | function validate ($key) {
        |   return (${onValidateCode});
        | }
      """.stripMargin
    validateEngine.eval(codeTemplate)
    validateEngine.asInstanceOf[Invocable]
  }

  private[this] val defaultExecutor: Option[Invocable] = default.map { defaultCode =>
    val engine = JsTools.newEngineWithSDK()
    val codeTemplate =
      s"""
        | function defaultFn () {
        |   return (${defaultCode});
        | }
      """.stripMargin
    engine.eval(codeTemplate)
    engine.asInstanceOf[Invocable]
  }

  protected def readDefault(path: JsPath): JsResult[JsValue] =
    defaultExecutor match {
      case Some(exec) =>
        dataType.writeJsValue(exec.invokeFunction("defaultFn")) match {
          case Success(v) => JsSuccess(v, path)
          case Failure(e) =>
            JsError((path, ValidationError("field.default.invalid_return_type")))
        }
      case None => JsSuccess(JsNull, path)
    }

  protected def runThroughValidator(path: JsPath, in: JsValue): JsResult[JsObject] =
    try {
      //notice that we are (semi-safely) performing .get on the Option given that it has been checked by validateInput
      validateExecutor match {
        case Some(v) =>
        v.invokeFunction("validate", dataType.valueAsOpt(in).get.asInstanceOf[Object]) match {
          case b: JBoolean if b =>
            JsSuccess(Json.obj(key -> in), path)
          case b: JBoolean =>
            JsError((path, ValidationError("validation.custom.failed")))
          case _ => //returned type is not Boolean as expected
            JsError((path, ValidationError("validation.custom.invalid_return_type")))
        }
        case None => JsSuccess(Json.obj(key -> in), path)
      }
    } catch {
      case se: ScriptException =>
        //TODO Log the error
        println("Validation Script Execution Error: " + se)
        JsError((path, ValidationError("validation.custom.runtime_error")))
    }
}
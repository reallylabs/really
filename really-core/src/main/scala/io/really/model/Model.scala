package io.really

import javax.script.{ScriptException, ScriptContext, Invocable}
import io.really.js.JsTools
import play.api.libs.json._
import jdk.nashorn.api.scripting.NashornScriptEngineFactory

package object model {
  type ModelVersion = Long
  type JsScript = String
  type FieldKey = String

  trait ModelException extends Exception


  case class CollectionMetadata(version: ModelVersion)

  case class FieldProtectionPlan(hidden: Set[FieldKey])

  /**
   * - Validate (cancel) – called before create and update
      - OnGet (cancel/hide) – called on get and query/read
      - PreUpdate (prev, after) -- cancel –– called before saving the update
      - PreDelete (cancel)
      - PostCreate
      - PostUpdate
      - PostDelete
   * @param onValidate
   */
  case class JsHooks(onValidate: Option[JsScript],
                     preGet: Option[JsScript],
                     preUpdate: Option[JsScript],
                     preDelete: Option[JsScript],
                     postCreate: Option[JsScript],
                     postUpdate: Option[JsScript],
                     postDelete: Option[JsScript])


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
  case class Model(r: R,
                   collectionMeta: CollectionMetadata,
                   fields: Map[FieldKey, Field[_]],
                   jsHooks: JsHooks,
                   migrationPlan: MigrationPlan) {

    val factory = new NashornScriptEngineFactory
    val executeValidator:Option[Validator] = jsHooks.onValidate.map { onValidateCode =>
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

    def executeValidate(context: RequestContext, input: JsObject): ModelHookStatus = {
      executeValidator match {
        case Some(validator: Validator) =>
          try {
            validator.validate(input.toString)
            ModelHookStatus.Succeeded
          }
          catch {
            case te: ModelHookStatus.ValidationError => te.terminated
            case se: ScriptException =>
              //TODO Log the error
              println("Validation Script Execution Error: " + se)
              ModelHookStatus.Terminated(500, "Validation script throws a runtime error")
          }
        case None => ModelHookStatus.Succeeded
      }
    }

    def executeOnGet(context: RequestContext, input: JsObject): Either[FieldProtectionPlan, ModelHookStatus.Terminated] = ???

    def executePreUpdate(context: RequestContext, prev: JsObject, after: JsObject, fieldsChanged: Set[FieldKey]): ModelHookStatus = ???

    def executePreDelete(context: RequestContext, obj: JsObject): ModelHookStatus = ???

    //Async hooks, does not block the rest of the execution plan
    //config and globals are passed to allow us to send a native "JavaScript SDK" to the script that may require to access other external systems
    def executePostCreate(config: ReallyConfig, globals: ReallyGlobals, context: RequestContext, created: JsObject): Unit = ???

    def executePostUpdate(config: ReallyConfig, globals: ReallyGlobals, context: RequestContext, updated: JsObject): Unit = ???

    def executePostDelete(config: ReallyConfig, globals: ReallyGlobals, context: RequestContext, updated: JsObject): Unit = ???
  }

}


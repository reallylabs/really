package io.really.model.loader

import akka.actor.ActorSystem
import akka.testkit.{TestKit}
import io.really.R
import io.really.model._
import org.scalatest.{FlatSpecLike, BeforeAndAfterAll, Matchers}


class ModelLoaderSpec(_system: ActorSystem) extends TestKit(_system)
with FlatSpecLike with Matchers with BeforeAndAfterAll {

  def this() = this(ActorSystem("ModelLoaderSpec"))

  val source = getClass.getResource("/samples").getPath

  val modelLoader = new ModelLoader(source, system)

  it should "returns list of valid models" in {
    val result = modelLoader.models
    result.length shouldBe 5
    val rs = List(R("/users"), R("/users/*/boards/*/"), R("/products"), R("/products/*/tags/*/"),
      R("/users/*/boards/*/posts/*/"))
    result.foreach {
      model =>
        rs.contains(model.r) shouldBe true
    }
  }

  it should "load the supported model fields" in {
    val result = modelLoader.models
    result.length shouldBe 5
    val usersModel = result.filter(_.r == R("/users"))(0)
    usersModel.fields.keySet shouldEqual Set("lastName", "firstName", "fullName", "age", "locale")
    val firstNameField = usersModel.fields.get("FirstName").get
    firstNameField.dataType shouldBe DataType.RString
    firstNameField.key shouldBe "firstName"

    val lastNameField = usersModel.fields.get("lastName").get
    lastNameField.dataType shouldBe DataType.RString
    lastNameField.key shouldBe "lastName"

    val fullName = usersModel.fields.get("fullName").get
    fullName.isInstanceOf[CalculatedField2[_, _, _]] shouldBe true
    val fullNameField = fullName.asInstanceOf[CalculatedField2[String, String, String]]
    fullNameField.key shouldBe "fullName"
    fullNameField.dataType shouldBe DataType.RString
    fullNameField.calculatorExpression shouldBe """this.firstName + " " + this.lastName"""
    fullNameField.dep1 shouldBe firstNameField
    fullNameField.dep2 shouldBe lastNameField

    val ageField = usersModel.fields.get("age").get
    ageField.dataType shouldBe DataType.RLong
    ageField.key shouldBe "age"

    val locale = usersModel.fields.get("locale").get
    locale.isInstanceOf[ValueField[_]] shouldBe true
    val localeField = locale.asInstanceOf[ValueField[String]]
    localeField.dataType shouldBe DataType.RString
    localeField.key shouldBe "locale"
    localeField.default shouldBe Some("en")
    localeField.required shouldBe false
    localeField.validationExpression shouldBe Some("value.length >= 0")

  }

  it should "return js hooks" in {
    val result = modelLoader.models
    result.length shouldBe 5
    val usersModel = result.filter(_.r == R("/users"))(0)
    usersModel.jsHooks.onValidate.isDefined shouldBe true
    usersModel.jsHooks.onValidate.get shouldBe """var age = input.age;if(age <20) {cancel(401, "down Age!")}"""
    usersModel.jsHooks.postUpdate.isDefined shouldBe false
  }

  it should "return the model version" in {
    val result = modelLoader.models
    result.length shouldBe 5
    val usersModel = result.filter(_.r == R("/users"))(0)
    usersModel.collectionMeta.version shouldBe 1L
  }

  it should "parse and return sub collections" in {
    val result = modelLoader.models
    result.length shouldBe 5
    val usersModel = result.filter(_.r == R("/users"))(0)
    usersModel.subCollections.length shouldBe 1
    val boardsR = usersModel.subCollections(0)
    boardsR shouldEqual R("/users/*/boards")
    val boardsModel = result.filter(_.r == boardsR)(0)
    boardsModel.subCollections.length shouldBe 1
    val postsR = boardsModel.subCollections(0)
    postsR shouldEqual R("/users/*/boards/*/posts")
    val postsModel = result.filter(_.r == postsR)(0)
    postsModel.subCollections.length shouldBe 0
  }

  it should "read migration scripts" in {
    val result = modelLoader.models
    result.length shouldBe 5
    val usersModel = result.filter(_.r == R("/users"))(0)
    val scripts = usersModel.migrationPlan.scripts
    scripts.size shouldBe 2
    scripts.get(222).get shouldEqual "//Migration script for version 222"
    scripts.get(3).get shouldEqual "//Migration script for version 3"

    val productModel = result.filter(_.r == R("/products"))(0)
    val productScripts = productModel.migrationPlan.scripts
    productScripts.size shouldBe 0
  }

  it should "parse reference fields" in {
    val result = modelLoader.models
    result.length shouldBe 5
    val productsModel = result.filter(_.r == R("/products"))(0)
    productsModel.fields.keySet shouldEqual Set("name", "description", "creator")
    val creator = productsModel.fields.get("creator").get
    creator.isInstanceOf[ReferenceField[_]] shouldBe true
    val creatorField = creator.asInstanceOf[ReferenceField[_]]
    creatorField.key shouldBe "creator"
    creatorField.dataType shouldEqual DataType.Reference
    creatorField.collectionR shouldEqual  R("/users")
    creatorField.fields shouldEqual List("firstName", "lastName")
  }

  it should "fail if there is invalid models" in {
    val invalidModelsDir = getClass.getResource("/invalidSamples").getPath

    intercept[DataTypeException] {
      val modelLoader2 = new ModelLoader(invalidModelsDir, system)
      modelLoader2.models
    }
  }

  override def afterAll() {
    TestKit.shutdownActorSystem(system)
  }


}

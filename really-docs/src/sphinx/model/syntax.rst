Model File Syntax
=================
Models Tree
###########
We define the model schema in a `yaml <http://www.yaml.org/>`_ file. First you should add a file called **model.yaml** in a directory with the **same name** of your model.
For example to define users model, you should create a directory with a name "users" and add model.yaml file that will define your users model schema.
You can define nested models using nested directories like that.

Example
*******

.. code-block:: javascript

  .really/models
  |______ users
           |_______ model.yaml   #the R of this model will equal "/users/"
           |_______ posts
                      |________ model.yaml       #the R of this model will equal "/users/posts"
  |_______ tags
            |_______ model.yaml    #the R of this model will equal "/tags/"


Model.yaml Syntax
*****************
First you should assign version number to your model, this number should be Long
After that you should define the model fields like that

.. warning::
  You should update the model version if you updated anything in the model, we can't detect that the model has been changed without changing the version number

Example Schema
**************

.. code-block:: javascript

    version: 1
    fields:
      firstName:
        type: String
        required : true
      lastName:
        type: String
      fullName:
        type: Calculated
        valueType: String
        dependsOn: firstName, lastName
        value: this.firstName + " " + this.lastName
      age:
        type: Long
      locale:
        type: String
        default: en
        validation: |
          value.length >= 0

Field Types
***********
Value Field
-----------
To define a value field you should define these attributes

* type (**required**)
* required (**required**)
* default (**optional**)
* validation (**optional**) a string that represent a javascript validation code on the value of this field

.. note::

  **Allowed types**

  String, Double, Long, Boolean

Example
^^^^^^^
.. code-block:: javascript

  fields:
  locale:
    type: String
    default: en
    required : true
    validation: value.length == 2

Reference Field
---------------
To define a reference field you should define these attributes

* type (**required**) should be reference field
* collectionR (**required**) defines the collection R that this field is reference to it
* fields (**required**) define list of fields that you are interested in of the reference model

Example
^^^^^^^
.. code-block:: javascript

  creator:
    type: reference
    collectionR: /users
    fields:
      - firstName
      - lastName

Javascript Hooks
****************
You can define the javascript hooks in a separate files in the model directory
these file should have these names

* on-validate.js (**optional**)
* pre-get.js (**optional**)
* pre-delete.js (**optional**)
* pre-update.js (**optional**)
* post-create.js (**optional**)
* post-update.js (**optional**)
* post-delete.js (**optional**)

Migration Scripts
*****************
To define a migration script file, you should create a javascript file with this name "evolution-<MODEL_VERSION>"


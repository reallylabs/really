Client API
**********

Introduction
------------
really is a namespace under which group of methods you can use to interact with server using Really API

Interacting with any method is as easy as passing the R(migrated) ton any of the method and other required parameters.

Really Object methods
---------------------

.. code:: bash

  really.object.METHOD_NAME

Parameters
^^^^^^^^^^

+-----------+-------------------------------------------+---------+-----------+
| Name      | Description                               | Type    | Required? |
+===========+===========================================+=========+===========+
| res       | The R for object you are interacting with | String  | Yes       |
+-----------+-------------------------------------------+---------+-----------+
| options   | The required options for the method       | Options | No        |
+-----------+-------------------------------------------+---------+-----------+

Methods
-------

* get
* update
* delete

get
---

Description
^^^^^^^^^^^

Retrieves a snapshot of the object at a certain moment in time, you will not be receiving any updates on this object, but if you are interested in getting an object and monitor the updates you should be using subscribe command instead.

.. Hint:: get(R, options) returns Really Promise object

Parameters
^^^^^^^^^^^

**res: [String] the resource for object Required**

**Options: [Object] optional**

+---------------+-----------------------------------------------------------------------------------------------------------------------+---------------+-----------+
| Name          | Description                                                                                                           | Type          | Required? |
+===============+=======================================================================================================================+===============+===========+
| fields        | Array of specific fields you want to retrieve; you will retrieve the whole object if you didn't provide the fields    | Array[String] | No        |
+---------------+-----------------------------------------------------------------------------------------------------------------------+---------------+-----------+
| onSuccess     | A function to be called if the request succeeds. The function receives data returned by the server                    | Function      | No        |
+---------------+-----------------------------------------------------------------------------------------------------------------------+---------------+-----------+
| onError       | A function to be called if the request fails. The function receives Error Object as argument                          | Function      | No        |
+---------------+-----------------------------------------------------------------------------------------------------------------------+---------------+-----------+
| onComplete    | A function to be called when the request finishes (after onSuccess and onError callbacks are executed).               | Function      | No        |
|               | The function receives status string as argument categorizing the status of the request (success, error, timeout).     |               |           |
+---------------+-----------------------------------------------------------------------------------------------------------------------+---------------+-----------+

**Example**

.. code-block:: coffeescript

  options =
    fields: ["author", "@card"]

    onSuccess: (data) ->
      console.log data

    onError: (error) ->
      console.log error.code // 403
      console.log error.message // forbidden

  really.object.get '/users/123', options

**Output**

.. code:: bash

  TODO

Delete Operation
----------------

Description
^^^^^^^^^^^

Delete object

.. Hint:: delete(R, options) returns Really Promise object

Parameters
^^^^^^^^^^

Option [Object] optional:

+---------------+-----------------------------------------------------------------------------------------------------------------------+---------------+-----------+
| Name          | Description                                                                                                           | Type          | Required? |
+===============+=======================================================================================================================+===============+===========+
| onSuccess     | A function to be called if the request succeeds. The function gets passed the data returned by the server             | Function      |           |
+---------------+-----------------------------------------------------------------------------------------------------------------------+---------------+-----------+
| onError       | A function to be called if the request fails. The function receives Error Object as argument                          | Function      |           |
+---------------+-----------------------------------------------------------------------------------------------------------------------+---------------+-----------+
| onComplete    | A function to be called when the request finishes (after onSuccess and onError callbacks are executed).               | Function      |           |
|               | The function receives status string as argument categorizing the status of the request (success, error, timeout).     |               |           |
+---------------+-----------------------------------------------------------------------------------------------------------------------+---------------+-----------+

**Example**

.. code-block:: coffeescript

  options =
      onSuccess: (data) ->
          console.log data
      onError: (error) ->
          console.log error.code // 403
          console.log error.message // forbidden
      onComplete: (data) ->
          console.log data
  really.object.delete('stories.1337845', options)

**Example**

.. code-block:: coffeescript

  really.object.delete('stories.1337845').done(data) ->
    console.log data

Update Operation
----------------

.. Hint:: update(R, options) returns Really Promise object

Description
^^^^^^^^^^^

Used to execute commands on fields of object, to change the values of these fields.

Commands are sent in form of **operations (ops)**, the skeleton of each operation is like:

+-----------+---------------------------------------------------------------------+-----------+
| Name      | Description                                               | Type    | Required? |
+===========+===========================================================+=========+===========+
| op        | The name of operations possible values (set, add-number)  | String  |           |
+-----------+-----------------------------------------------------------+---------+-----------+
| key       | The name of field                                         | String  |           |
+-----------+-----------------------------------------------------------+---------+-----------+
| value     | The value you want to operate the command on              | String  |           |
+-----------+-----------------------------------------------------------+---------+-----------+
| opArgs    | arguments of the operation if needed                      |         |           |
+-----------+-----------------------------------------------------------+---------+-----------+

.. warning:: The update operations will be executed as a transaction

**Example**

.. code-block:: javascript

  ops: [
        {
          op: 'set',
          key: 'friends',
          value: 'Ahmed'
        },
        {
          op: 'set', //operation
          opArgs: undefined, //arguments of the operation if needed (JsObject)
          key: 'picture.xlarge', //{picture: {xlarge: "http://koko.co/toto.png"}}
          value: 'http://koko.com/toto.png' //type is dependent on the key, can be JsObject or JsArray too
        },
        { //setting an element in the array (value is EmbeddedDocument)
          op: 'set',
          key: 'picture[0]', // {picture: [{xlarge: "http://koko.toto.png"}]}
          value: {"xlarge": "http://koko.toto.png"}
        },
        {
            op: "addNumber",
            key: 'age',
           value: 1 //adding one, can be negative too
        }
      ]

Parameters
^^^^^^^^^^

Options [Object] optional:

+---------------+-----------------------------------------------------------------------------------------------------------------------+---------------+
| Name          | Description                                                                                                           | Type          |
+===============+=======================================================================================================================+===============+
| ops           | array of operations                                                                                                   | Array of ops  |
+---------------+-----------------------------------------------------------------------------------------------------------------------+---------------+
| onSuccess     | A function to be called if the request succeeds. The function gets passed the data Object returned by the server      |               |
+---------------+-----------------------------------------------------------------------------------------------------------------------+---------------+
| onError       | A function to be called if the request fails. The function receives Error Object as argument                          |               |
+---------------+-----------------------------------------------------------------------------------------------------------------------+---------------+
| onComplete    | A function to be called when the request finishes (after onSuccess and onError callbacks are executed).               |               |
|               | The function receives status string as argument categorizing the status of the request (success, error, timeout).     |               |
+---------------+-----------------------------------------------------------------------------------------------------------------------+---------------+

data Object

+---------------+-----------------------------------------------------------------------------------------------------------------------+---------------+-----------+
| Name          | Description                                                                                                           | Type          | Required? |
+===============+=======================================================================================================================+===============+===========+
| NameOfField   | Actually this field is dynamic, it contains names of changed field e.g(firstName, avatar, ...)                        | String        |           |
+---------------+-----------------------------------------------------------------------------------------------------------------------+---------------+-----------+
| op            | A function to be called if the request fails. The function receives Error Object as argument                          | String        | No        |
+---------------+-----------------------------------------------------------------------------------------------------------------------+---------------+-----------+
| opValue       | The value used in this operation ex:                                                                                  | Any           |           |
|               |                                                                                                                       |               |           |
|               |  .. code-block:: javascript                                                                                           |               |           |
|               |                                                                                                                       |               |           |
|               |   "age": {                                                                                                            |               |           |
|               |     "value": 36,                                                                                                      |               |           |
|               |     "op": "addNumber",                                                                                                |               |           |
|               |     "opValue": 123                                                                                                    |               |           |
|               |     }                                                                                                                 |               |           |
|               |                                                                                                                       |               |           |
|               | This means the current value of field age is 36 because add-number happened by adding 1 to it its old values          |               |           |
+---------------+-----------------------------------------------------------------------------------------------------------------------+---------------+-----------+
| opBy          | A function to be called when the request finishes (after onSuccess and onError callbacks are executed).               | ANY           |           |
+---------------+-----------------------------------------------------------------------------------------------------------------------+---------------+-----------+

**Example**

.. code-block:: coffeescript

  onSuccess = (data) -> console.log data
  options =
  onSuccess: onSuccess
  ops: [
      {
        key: "friends"
        op: "set"
        value: "Ahmed"
      }
      {
        key: "picture.xlarge"
        op: "set"
        value: "http://koko.com/toto.png"
      }
      {
        key: "picture[0]"
        op: "set"
        value:
          xlarge: "http://koko.toto.png"
      }
      {
        key: "age"
        op: "addNumber"
        value: 1
      }
    ]
  really.object.update '/stories/123', options

**Output**

.. code-block:: javascript

  {
    "friends": {
      "op": "set",
      "opValue": "Ahmed",
      "opBy": {"userR": '/users/1'}
    },
    "picture.xlarge": {
      "op": "set",
      "opValue": "http://koko.com/toto.png",
      "opBy": {"userR": '/users/1'}
    },
    "picture[0]": {
      "op": "set",
      "opValue": {
        "medium": "http://koko.toto.png"
      },
      "opBy": {"userR": '/users/1'}
    },
    "age": {
      "op": "addNumber",
      "value": 16,
      "opValue": 1,
      "opBy": {"userR": '/users/1'}
    }
  }

.. Note:: in the previous example this means age was 15 and get incremented by 1 opValue so it becomes 16 value

Really Collection Methods
=========================

Methods
-------

* create
* read

Create Operation
----------------

.. Note:: create(R, options) returns Really Promise object

Description
^^^^^^^^^^^

Create new object and add it to collection on server, this operation is done only on collection

Parameters
^^^^^^^^^^^

+---------------+-----------------------------------------------------------------------------------------------------------------------+---------------+-----------+
| Name          | Description                                                                                                           | Type          | Required? |
+===============+=======================================================================================================================+===============+===========+
| body          | The object you want to create                                                                                         | Object        | No        |
+---------------+-----------------------------------------------------------------------------------------------------------------------+---------------+-----------+
| onSuccess     | A function to be called if the request succeeds. The function gets passed the data returned by the server             |               | No        |
+---------------+-----------------------------------------------------------------------------------------------------------------------+---------------+-----------+
| onError       | A function to be called if the request fails. The function receives Error Object as argument                          |               | No        |
+---------------+-----------------------------------------------------------------------------------------------------------------------+---------------+-----------+
| onComplete    | A function to be called when the request finishes (after onSuccess and onError callbacks are executed).               |               | No        |
|               | The function receives status string as argument categorizing the status of the request (success, error, timeout).     |               |           |
+---------------+-----------------------------------------------------------------------------------------------------------------------+---------------+-----------+

**Example**

.. code-block:: coffeescript

  userPromise = user.create
    name: "Ihab"
    age: "99"
  userPromise.done (data) ->
    console.log data

**Output**

.. code-block:: coffeescript

  {
    "name": "Ihab"
    "age": "99"
  }

Read Operation
--------------

Description
~~~~~~~~~~~

Fetch the default set of objects for this collection on server

.. Note:: read(R, options) returns Really Promise object

Parameters
~~~~~~~~~~~

Options [Object]

+-------------------+-----------------------------------------------------------------------------------------------------------------------+-------------------+-----------+
| Name              | Description                                                                                                           | Type              | Required? |
+===================+=======================================================================================================================+===================+===========+
| fields            | List of objects fields names you want to retrieve                                                                     | Array of Strings  | No        |
+-------------------+-----------------------------------------------------------------------------------------------------------------------+-------------------+-----------+
| query.filter      | Your query which matches RQL ex:                                                                                      |  Strings          | No        |
|                   |                                                                                                                       |                   |           |
|                   | .. code-block:: javascript                                                                                            |                   |           |
|                   |                                                                                                                       |                   |           |
|                   |   filter: "name = $name and age > $age"                                                                               |                   |           |
+-------------------+-----------------------------------------------------------------------------------------------------------------------+-------------------+-----------+
| query.values      | Values that matches query parameters in query.filter ex:                                                              |  Object           | No        |
|                   |                                                                                                                       |                   |           |
|                   | .. code-block:: javascript                                                                                            |                   |           |
|                   |                                                                                                                       |                   |           |
|                   |   values: {"name": "Ahmed", "age": 5}                                                                                 |                   |           |
+-------------------+-----------------------------------------------------------------------------------------------------------------------+-------------------+-----------+
| limit             | A number specifies the maximum number of results returned                                                             |                   |           |
+-------------------+-----------------------------------------------------------------------------------------------------------------------+-------------------+-----------+
| skip              | Skip specific number of results returned                                                                              |                   |           |
+-------------------+-----------------------------------------------------------------------------------------------------------------------+-------------------+-----------+
| sort              | You can specify the field you want to sort the results by the default is sorting by R                                 |                   |           |
+-------------------+-----------------------------------------------------------------------------------------------------------------------+-------------------+-----------+
| token             | This token enables you to paginate between the result of query, you can use nextToken and prevToken returned          |                   |           |
|                   | in response of read command                                                                                           |                   |           |
+-------------------+-----------------------------------------------------------------------------------------------------------------------+-------------------+-----------+
| includeTotalCount | If you want the size of the entire result set (this may not be accurate)                                              | Boolean           |           |
|                   | (only works if the query counter feature is enabled or not per collection)                                            |                   |           |
+-------------------+-----------------------------------------------------------------------------------------------------------------------+-------------------+-----------+
| subscribe         | Subscribe on **insertions** if true                                                                                   | Boolean           |           |
+-------------------+-----------------------------------------------------------------------------------------------------------------------+-------------------+-----------+
| onSuccess         | A function to be called if the request succeeds. The function gets passed the **data** Object                         |                   |           |
+-------------------+-----------------------------------------------------------------------------------------------------------------------+-------------------+-----------+
| onError           | A function to be called if the request fails. The function receives Error Object as argument  | Object                |                   |           |
+-------------------+-----------------------------------------------------------------------------------------------------------------------+-------------------+-----------+
| onComplete        | A function to be called when the request finishes (after onSuccess and onError callbacks are executed).               |                   |           |
|                   | The function receives status string as argument categorizing the status of the request (success, error, timeout).     |                   |           |
+-------------------+-----------------------------------------------------------------------------------------------------------------------+-------------------+-----------+

**data** Object

+---------------+---------------------------------------------------------------------------+
| Name          | Description                                                     | Type    |
+===============+=================================================================+=========+
| items         | List of returned objects                                        |         |
+---------------+-----------------------------------------------------------------+---------+
| nextToken     | Use this to navigate to next collection of result               |         |
+---------------+-----------------------------------------------------------------+---------+
| prevToken     | Use this to navigate to previous collection of result           |         |
+---------------+-----------------------------------------------------------------+---------+
| subscription  | The id of your query so you can use it later for unsubscription |         |
+---------------+-----------------------------------------------------------------+---------+
| totalResults  | The total number of results in server                           |         |
+---------------+-----------------------------------------------------------------+---------+

.. Note::

  Note about sort field

  The default order of soring is ascending you can prepend - to the name of key to sort descending

  **Example**

  .. code-block:: coffeescript

    sort: 'name' // sort results ascending by name
    sort: '-name' // sort results descending by name

**Example**

.. code-block:: coffeescript

  really.colllection.read '/stories/*',
    fields: ["firstname", "lastname", "avatar"]
    query:
        filter: "name = {1} and age > {2}"
        values: ["Ahmed", 5]
    limit: 10
    sort: '-name'
    token: '23423423:1'
    skip: 1
    includeTotalCount: false
    subscribe: true

Events
------

when you subscribe on events on specific object/collection you are listening for data changes on this object/collection, basically we have 3 methods to subscribe on events:

* really.collection.onCreated(R, callback)
* really.object.onDeleted(R, callback)
* really.object.onUpdated(R, callback)

onCreated(R, callback)
^^^^^^^^^^^^^^^^^^^^^^

callback is a function to be called if the event happened. The function receives data as sole arguments

data:

+-------------------+---------------------------------------------------------------------------------------------+---------+
| Name              | Description                                                                                 | Type    |
+===================+=============================================================================================+=========+
| body              | Contains the newly created object                                                           | Object  |
+-------------------+---------------------------------------------------------------------------------------------+---------+
| meta              | contains meta information about this events                                                 | Object  |
+-------------------+---------------------------------------------------------------------------------------------+---------+
| meta.subscription | "ID" if this came due to subscription on read which you can use later to **unsubscribe**    | String  |
+-------------------+---------------------------------------------------------------------------------------------+---------+

**Example**

.. code-block:: coffeescript

  really.object.onCreated '/stories/123', (data) ->
    console.log data

**Output**

.. code-block:: coffeescript

  {
    "meta": {
      "subscription": "1240a0e"
    },
    "body": {
      "firstName": "Ihab",
      "lastName": "Khattab"
    }
  }

onUpdated(R, callback)
^^^^^^^^^^^^^^^^^^^^^^

callback is a function to be called if the event happened. The function receives data as sole arguments

**data:**

+-------+-------------------------------------------------------------------------------------+---------+-----------+
| Name  | Description                                                                         | Type    | Required? |
+=======+=====================================================================================+=========+===========+
| body  | Contains the names of fields changed and their values,                              | Object  | Yes       |
|       | the value of each field gives you information about current value of this field     |         |           |
+-------+-------------------------------------------------------------------------------------+---------+-----------+
| op    | The name of operation done on this field to change its value ex: (set, add-number)  | String  | No        |
+-------+-------------------------------------------------------------------------------------+---------+-----------+
| opBy  | The owner of this change e.g: {"userR": '/users/1'}                                 | Object  | No        |
+-------+-------------------------------------------------------------------------------------+---------+-----------+

**Example**

.. code-block:: coffeescript

  really.object.onUpdated '/stories/123', (data) ->
   console.log data

**Output**

.. code-block:: javascript

  {
     "body": {
       "firstName": {
         "op": "set",
         "opValue": "Ahmed",
         "opBy": {"userR": '/users/2234562'}
       },
       "tags": { //value snapshot
         "op": "push",
         "opValue": [ //those were added
           "posts",
           "things"
         ],
         "opBy": {"userR": '/users/2234562'}
       }
     }
   }

onDeleted(R, callback)
^^^^^^^^^^^^^^^^^^^^^^

Callback is a function to be called if the event happened. The function receives data as sole arguments

**Data:**

+-------------------+-----------------------------------------------+---------+
| Name              | Description                                   | Type    |
+===================+=========================================================+
| meta.deletedBy    | the **R** of object who deleted this object   | Object  |
+-------------------+---------------------------------------------------------+

**Example**

.. code-block:: coffeescript

  really.object.onDeleted '/stories/123', (data) ->
    console.log data

**Output**

.. code-block:: javascript

  {
    "meta": {"deletedBy": "/users/007"}
  }

off(R, eventType)
^^^^^^^^^^^^^^^^^

**parameters**

+-----------+---------------------------------------------------+---------+
| Name      | Description                                       | Type    |
+===========+===================================================+=========+
| eventType | the name of event you want to stop listening to   | String  |
+-----------+---------------------------------------------------+---------+

**Example**

.. code-block:: coffeescript

  really.collection.off(R, 'created')
  really.object.off(R, 'updated')
  really.object.off(R, 'deleted')

Subscriptions
=============

Subscribe Operation
-------------------

Description
^^^^^^^^^^^

used to subscribe on objects to receive any future updates

.. Note:: Really.subscribe(options) returns Really Promise object


Parameters
^^^^^^^^^^

options [Object]:

+---------------+---------------------------------------------------------------------------------------------------------------------+-------------------+
| Name            | Description                                                                                                       | Type              |
+=================+===================================================================================================================+===================+
| subscriptions   | array of **subscription** objects                                                                                 | Array of **ops**  |
+-----------------+-------------------------------------------------------------------------------------------------------------------+-------------------+
| onSuccess       | A function to be called if the request succeeds. The function gets passed the data returned by the server         |                   |
+-----------------+-------------------------------------------------------------------------------------------------------------------+-------------------+
| onError         | A function to be called if the request fails. The function receives Error Object as argument                      |                   |
+-----------------+-------------------------------------------------------------------------------------------------------------------+-------------------+
| onComplete      | A function to be called when the request finishes (after onSuccess and onError callbacks are executed).           |                   |
|                 | The function receives status string as argument categorizing the status of the request (success, error, timeout). |                   |
+-----------------+-------------------------------------------------------------------------------------------------------------------+-------------------+

+---------+---------------------------------------------+---------+----------+
| Name    | Description                                 | Type    | Required |
+=========+=============================================+=========+==========+
| r       | The **R** of Object you are subscribing to  | String  |          |
+---------+---------------------------------------------+---------+----------+
| fields  | Array of fields you want to subscribe to    | String  |          |
+---------+---------------------------------------------+---------+----------+

.. code-block:: coffeescript

  Really.subscribe
    subscriptions: [
        "r": "/users/"
        "fields": ["name", "avatar"]
      ,
        "r": "/users/34"
        "fields": ["avatar"]
      ]

Unsubscribe Operation
---------------------

Description
^^^^^^^^^^^

Used to unsubscribe from objects you are not interested in receiving any future updates on them

.. Note:: unsubscribe(options) returns Really Promise object

Parameters
^^^^^^^^^^^

option [Object] optional:

+---------------+---------------------------------------------------------------------------------------------------------------------+-------------------+
| Name            | Description                                                                                                       | Type              |
+=================+===================================================================================================================+===================+
| subscriptions   | Array of **subscription** objects you want to unsubscribe from                                                    | Array of **ops**  |
+-----------------+-------------------------------------------------------------------------------------------------------------------+-------------------+
| onSuccess       | A function to be called if the request succeeds. The function gets passed the data returned by the server         |                   |
+-----------------+-------------------------------------------------------------------------------------------------------------------+-------------------+
| onError         | A function to be called if the request fails. The function receives Error Object as argument                      |                   |
+-----------------+-------------------------------------------------------------------------------------------------------------------+-------------------+
| onComplete      | A function to be called when the request finishes (after onSuccess and onError callbacks are executed).           |                   |
|                 | The function receives status string as argument categorizing the status of the request (success, error, timeout). |                   |
+-----------------+-------------------------------------------------------------------------------------------------------------------+-------------------+

**subscription** Object

+---------+---------------------------------------------------+--------------------+
| Name    | Description                                       | Type    | Required |
+=========+===================================================+=========+==========+
| r       | The **R** of Object you want to unsubscribe from  | String  |          |
+---------+---------------------------------------------------+---------+----------+
| fields  | Array of fields you want to unsubscribe from      | Array   |          |
+---------+---------------------------------------------------+---------+----------+

.. Note::

  Note about subscription

  If you are going to unsubscribe from query on collection in this case r would be the R of this query not the whole collection, please see the example below

**Example**

.. code-block:: coffeescript

  storiesPromise = stories.read '/stories/*',
    fields: ["content", "author"]
    query:
        filter: "author = {1} and age > {2}"
        values: ["Ahmed", 5]
    limit: 10
    sort: '-name'
    token: '23423423:1'
    skip: 1
    includeTotalCount: false
    subscribe: true

  storiesPromise.done (data) ->
    console.log data.subscription # '/_/subscriptions/a043e09'

  Really.unsubscribe
    subscriptions: [
      r: "/_/subscriptions/a043e09"
      fields: ["name"]
  ]

**Example**

.. code-block:: coffeescript

  userPromise = Really.subscribe
      subscriptions: [
          r: '/users/3214'
          fields: ['name', 'avatar']
      ]
  userPromise.done (data) ->
      console.log fields for fields in data.subscriptions # ['name', 'avatar']
  userPromise = Really.unsubscribe
      subscriptions: [
          r: '/users/3214'
          fields: ['name', 'avatar']
      ]
  userPromise.done (data) ->
      console.log fields for fields in data.subscriptions # ['avatar'] # you are still subscribed to changes on avatar fields
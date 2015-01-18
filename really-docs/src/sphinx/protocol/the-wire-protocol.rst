The Wire Protocol
=================

.. warning::
   This document is draft and is subject to change, if you are interested you watch the document updates in Confluence to be notified.

General Notes
#############
* The error codes strive to be as close as possible to the standard HTTP Status code http://www.w3.org/Protocols/rfc2616/rfc2616-sec6.html#sec6.1.1

Request Skeleton Definition
###########################
.. code-block:: javascript

  {
      tag: 1, //integer, optional
      traceId: "tag", //optional, for debugging purposes
      cmd: "string", //required (get, subscribe, delete, update, read, count)
      cmdOpts: {}, //optional, options for the command (enable/disable things for example)
      r: '/_system/tools', //R is the resource you are talking to
      rev: 42, // integer, the revision of this R
      body: {} //optional
  }

RESPONSE or PUSH SKELETON
#########################
.. code-block:: javascript

  {
      tag: 1, //optional if push
      meta: {}, //optional
      evt: "string", //event in case of PUSH (only set if tag is not set)
      r: '/_system/tools',
      rev: 42, // integer, the revision of this R
      body: {
      },
      error: {
          errors: {
              'obj.email': ['required'],
              'obj.firstname': ['required', 'validation.failed']
          }
          code: 512,
          message: "Failed Validation"
      } //contains the error details if any
  }

Supported Crud Operations
#########################
* create
* get
* update
* delete

Get Command Definition
**********************
When you get an object you basically retrieve a snapshot of the object at a certain moment in time,
you will not be receiving an updates on this object,
if you are interested in getting an object and monitor the updates you should be using `subscribe` command instead

Request
-------
.. code-block:: javascript

  {
      tag: 1,
      cmd: "get",
      cmdOpts: {
        fields: ["firstname", "@displayInfo"] // if not specified, the get will retrieve all fields that you have access to
      },
      r: '/users/114243'
  }

Response
--------

Success
^^^^^^^
.. code-block:: javascript

  {
    tag: 1,
    meta: {
        fields: ["firstname", "lastname", "avatar"]
        //avatar is not set for the user, so we are not receiving it, but he has access to.
    },
    r: '/users/114243', //always without revision (important for client-side event-bus system)
    body: {
        "_r": "/users/114243/",
        "_rev": 323 //must have a revision
        "firstname": "Ahmed",
        "lastname": "Soliman",
        "creator": {
           value: "/users/234234",
           ref: { //reference field -- dereferenced
               "_r": "/users/234234/",
                "_rev": 20 //revision must be set
               "name": "Hamada Imam",
                }
         }
    }
  }

Error
^^^^^
.. code-block:: javascript

  {
    tag: 1,
    r: '/users/114243'
    error: {
        code: 403,
        message: "forbidden"
    }
  }

Deleted
^^^^^^^
.. code-block:: javascript

  {
    tag: 1,
    r: '/users/114243'
    error: {
        code: 410,
        message: "gone"
    }
  }

Update Command Definition
*************************
Update one or more fields in a specific object with a specific R.

.. note::
   * R should represent a single object. You can't execute update command on R of type collection
   * Revision is required here
   * The update operations will be executed as a transaction

Supported operations
--------------------
* set
* addNumber
* push
* addToSet
* insertAt
* pull
* removeAt

Request
-------
.. code-block:: javascript

  {
      tag: 1,
      cmd: "update",
      cmdOpts: {
      },
      r: "/users/66123/",
      rev: 42, // integer, the revision of this R
      body: {
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
            value: 'http://koko.com/toto.png' //type is dependant on the key, can be JsObject or JsArray too
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
      }
  }

Response
--------
Success
^^^^^^^
.. code-block:: javascript

  {
      tag: 1
      r: '/_users/114243/',
      rev: 42
  }

Error
^^^^^
.. code-block:: javascript

  {
      r: '/_users/114243',
      error: {
          code: 512,
          message: "Failed Validation"
      } //contains the error details if any
 }

Read Command Definition
***********************
Request
-------
.. code-block:: javascript

  {
      tag: 1,
      cmd: "read",
      cmdOpts: {
        fields: ["firstname", "lastname", "avatar"],
        query: {
            filter: "name = $name and age > $age",
            values: {"name": "Ahmed", "age": 5}
        },
        ascending: true, //default is false
        limit: 10,
        paginationToken: '23423423:1' //only if sortedBy is not set
        skip: 1, //default is 0
        includeTotalCount: false, //true if you want the size of the entire result set (this may not be accurate) (only works if the query counter feature is enabled or not per collection)
        subscribe: true, //subscribe on insertions
      }
      r: "/users/*", //this can be skeleton /users/*/boards/*
  }

Response
--------
Success
^^^^^^^
.. code-block:: javascript

  {
    tag: 1,
    meta: {
        subscription: 'ID', //if subscribe = true
    }
    r: "/users/*",
    body: {
    tokens: { //only if sort is unset or not _r
       nextToken: "1139234:0",
       prevToken: "1112342:1"
    },
    totalResults: 3452345,
        items: [
          {
              meta: {fields: ["firstname", "lastname"]} //as get
              body: { //as get
                  _r: "/users/234234/"
                  _rev: 29
                  "firstname": "Ahmed"
              }
          }
        ]
    }
  }

Create Command Definition
*************************
Request
-------
.. code-block:: javascript

  {

      tag: 1,
      traceId: "tag",
      cmd: "create",
      r: '/users/',
      body: {
        name: "Ahmed",
        age: 18

      }
  }

Response
--------
.. code-block:: javascript

  {

      tag: 1,
      r: '/users/',
      body: {
        _r: "/users/123567888756/",
        _rev: 1,
        name: "Ahmed",
        age: 18

      }
  }

Delete Command Definition
*************************
Request
-------
.. code-block:: javascript

  {
  tag: 1,
  cmd: "delete",
  r: '/users/134354366465'
 }

Response
--------
Success
-------
.. code-block:: javascript

  {
      tag: 1,
      r: '/users/134354366465'
  }

Subscription / Push
*******************
Subscribe
---------
Request
^^^^^^^
.. code-block:: javascript

  {   "tag": 1,
      "cmd": "subscribe",
      "body": {
        "subscriptions": [
          {
            "r": "/users/123",
            "rev": 12,
            "fields": [
              "firstname",
              "@displayInfo",
              "age"
            ] //if no fields are specified, this means all "possible" fields please
          }
        ]
      }
  }

Response
^^^^^^^^
.. code-block:: javascript

  {
      "tag": 1,
      "body": {
        "subscriptions": ["/users/123"]  //List of Rs that you are successfully subscribed on
      }
  }

Unsubscribe
-----------
Request
^^^^^^^
.. code-block:: javascript

  {
      "tag": 1,
      "cmd": "unsubscribe",
      "body": {
        "subscriptions": ["/users/123"]
      }
  }

Response
^^^^^^^^
.. code-block:: javascript

  {
      "tag": 1,
      "body": {
        "subscriptions": ["/users/123"]  //List of Rs that you are successfully unsubscribed on
      }
  }

Push Operations
***************
After you subscribe on a resource (collection – or object) you will be receiving one of those events (evt):

* created (r is the collection)
* deleted (r is the object)
* updated (r is the object)
* snapshot(r is the object)
* schemaUpdated

Examples
--------
Ex1:
^^^^
.. code-block:: javascript

  {
      r: '/users/*',
      evt: "created",
      body: {
        "_r": "/users/144123/",
        "_rev": "1"
        "firstname": "Ahmed",
        "lastname": "Soliman",
      }
  }

Someone deleted an object you are subscribed to. This message does not come if you are subscribed on the collection or `read`, only subscriptions on objects will do.

Ex2:
^^^^
.. code-block:: javascript

  {
  r: '/users/144123',
  evt: "deleted"
  meta: {
	opBy:{
 		authType: "anonymous",
 		uid: "234567890",
 		data:{}
 		}
	}
  }

Ex3:
^^^^
.. code-block:: javascript

  {
      r: '/users/144123/',
      rev: 324,
      evt: "updated",
      body: {
        "firstName": {
          op: "set",
          opValue: "Ahmed"
        },
        "tags": {
          op: "push",
          opValue: ["posts", "things"] //those were added
        }
      },
      meta: {
        opBy:{
            authType: "anonymous",
            uid: "234567890",
            data:{}
            }
        }
  }

If the server has no updates starting from the revision sent, it will send a snapshot for the current object, then a sequence of updates if any

.. code-block:: javascript

  {
      r: '/users/144123',
      evt: "snapshot",
      body: {
        "_rev": "23"
        "firstname": "Ahmed",
        "lastname": "Refaey",
      }
  }

schemaUpdated
^^^^^^^^^^^^^
.. code-block:: javascript

  {
    r: '/users/144123',
    evt: "schemaUpdated" //The client should reload when he receive this message
  }
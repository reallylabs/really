Deletion Strategy
=================

This document describes how objects should be deleted, and how this affects other referenced/referencing objects.

.. note::

  **Ids won't get deleted**
  When Really receives a request to delete an object. It should update the object to remove all fields except it's R and rev, then add a flag to indicate it's psudo-deletion.

Example
-------
Object before deletion
^^^^^^^^^^^^^^^^^^^^^^
.. code-block:: javascript

  {
    "_r": "/users/114243/",
    "_rev": 323 //must have a revision
    "firstname": "Tamer",
    "lastname": "Abdulradi",
  }

Object after deletion
^^^^^^^^^^^^^^^^^^^^^
.. code-block:: javascript

  {
    "_r": "/users/114243/",
    "_rev": 324 //rev is incremented
    "_deleted": true,
  }

Effect on Get
-------------
In case a user tried to Get an already deleted object, he should get a 410 Gone error.

Effect on Create
----------------
This case shouldn't concern end users. But in case CollectionActor received a request with the same R of a deleted object, he should reply with `AlreadyExists` error.
End user should receive an Internal Server Error in this case.

Effect on Update
----------------
Updating a deleted object is not possible. You can't revive deleted objects. Users should receive `AlreadyDeleted` error.

Effect on Delete
----------------
Deleting a deleted object should result `AlreadyDeleted` error. Notifying the end developer that other user has deleted this object.
He is free to report the error to the current user, or hide it, and treat the operation as successful.

Handling references
-------------------
Objects referencing deleted objects
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
User should receive a 401 Gone error inside the meta of the referenced object.

.. code-block:: javascript

  {
    tag: 1,
    r: '/users/114243',
    body: {
        "_r": "/users/114243/",
        "_rev": 323 //must have a revision
        "creator": {
           value: "/users/234234",
           error: {
               code: 410,
               message: "Referenced object is Gone"
           },
           ref: {
               "_r": "/users/234234/",
               "_rev": 20, //revision must be set
               _deleted: true
           }
        }
    }
  }
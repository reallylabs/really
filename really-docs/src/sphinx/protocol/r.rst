R
=
R is a string that references to an object or collection. It is compatible with URI specs, so that it can be included in the URL to do REST operations.

Rules
#####
* Starts with a leading slash /
* May end with a trailing slash
* Supports asterisk as wildcard id. (Wild card collection name is not supported)
* Asterisk is optional if it is the last element in the path
* First components must be a collection, next component must be an Id, next component must be a collection (and so on...)

Types
#####
Collection
**********
+------------------------------------+-------------------------------------------------------------------+
| Example                            | Notes                                                             |
+====================================+===================================================================+
| /users/*                           | refers to a top level collection                                  |
+------------------------------------+-------------------------------------------------------------------+
| /users/                            | Asterisk is optional                                              |
+------------------------------------+-------------------------------------------------------------------+
| /users/123/posts/                  | Nested collections must be under an object                        |
+------------------------------------+-------------------------------------------------------------------+
| /users/123/posts/456/comments      | Trailing slash is optional                                        |
+------------------------------------+-------------------------------------------------------------------+
| /users/123/posts/456/comments/*    | Asterisk is optional                                              |
+------------------------------------+-------------------------------------------------------------------+
| /users/123/favourite-quotes        | dash is a valid collection name char                              |
+------------------------------------+-------------------------------------------------------------------+

Object
******
+---------------------------------------+-------------------------------------------------------------------+
| Example                               | Notes                                                             |
+=======================================+===================================================================+
| /users/123/                           | refers to a top level collection                                  |
+---------------------------------------+-------------------------------------------------------------------+
| /users/123                            | Asterisk is optional                                              |
+---------------------------------------+-------------------------------------------------------------------+
| /users/123/posts/456                  | Nested collections must be under an object                        |
+---------------------------------------+-------------------------------------------------------------------+
| /users/123/posts/456/comments/123     | Trailing slash is optional                                        |
+---------------------------------------+-------------------------------------------------------------------+

Wildcards
#########
Correct
*******
+---------------------------------------+---------------------------------------------------------------------+
| Example                               | Notes                                                               |
+=======================================+=====================================================================+
| /users/\*/posts/\*                    | This refers to all posts, under all users                           |
+---------------------------------------+---------------------------------------------------------------------+
| /users/123/posts/\*/comments          | This refers to all comments, under any post, under a specific user  |
+---------------------------------------+---------------------------------------------------------------------+

Wrong
*****
+---------------------------------------+---------------------------------------------------------------------------+
| Example                               | Notes                                                                     |
+=======================================+===========================================================================+
| /users/\*/followers/123               | You can't specify an id after asterisk, this is called **loophole** path  |
+---------------------------------------+---------------------------------------------------------------------------+

.. warning::

  The following is just a proposal, it has not been accepted in the roadmap yet.

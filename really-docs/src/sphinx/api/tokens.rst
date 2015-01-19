Really Tokens API
=================
Supported Operations
####################
* Create Account
* Create Token
* Revoke Token

Create Account
**************
Request
-------
+-------------+----------------------------------------------------------------------------------+-------------+-------------------------+
| Name        | Description                                                                      | Type        | Required?               |
+=============+==================================================================================+=============+=========================+
| secret      | Represent really secret key, this secret will be generated                       |             |                         |
|             | when creating an application with really                                         | String      | yes                     |
+-------------+----------------------------------------------------------------------------------+-------------+-------------------------+
| data        | This is an object that represent the data that will be stored in the users model | String      | yes                     |
+-------------+----------------------------------------------------------------------------------+-------------+-------------------------+

Response
--------

+-------------+-------------------------------------------------------------------+--------------+
| Name        | Description                                                       | Required?    |
+=============+===================================================================+==============+
| _r          | Represent a unique identifier for this persisted user             | yes          |
+-------------+-------------------------------------------------------------------+--------------+

Create Token
************
Request
-------
+-------------+----------------------------------------------------------------------------------+--------------------------------+
| Name        | Description                                                                      | Required?                      |
+=============+==================================================================================+================================+
| secret      | Represent really secret key, this secret will be generated                       |                                |
|             | when creating an application with really                                         | yes                            |
+-------------+----------------------------------------------------------------------------------+--------------------------------+
| authType    | The authentication method used to generate this token,                           |                                |
|             | currently supported methods is ("password", "anonymous")                         | yes                            |
+-------------+----------------------------------------------------------------------------------+--------------------------------+
| uid         | This a unique identifier that identify a specific user.                          |                                |
|             | Even anonymous users has an uid                                                  | yes                            |
+-------------+----------------------------------------------------------------------------------+--------------------------------+
| expires     | Expiration Date/Time in ISO format                                               | no - if missing,               |
|             |                                                                                  | it's assumed to be never expire|
+-------------+----------------------------------------------------------------------------------+--------------------------------+
| _r          | Represent a unique identifier for this persisted user                            | not required in case of        |
|             |                                                                                  |anonymous login                 |
+-------------+----------------------------------------------------------------------------------+--------------------------------+
| data        | The token can contains other info about the user                                 |                                |
|             | which depends on the authentication method                                       | no                             |
+-------------+----------------------------------------------------------------------------------+--------------------------------+

Response
********
Response will be a JWT encoded token, you can use it later to initialize connection with Really or access Really's APIs.

.. code-block:: javascript

  {"accessToken":"1563ab77a8ec29450aa268ecfb956ahhd58"}

Revoke Token
************
//TODO
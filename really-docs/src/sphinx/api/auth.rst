Authentication API
==================

Supported Operation
###################
* addUser
* login
* logout

Add New User
************
Really Authentication support creating new users accounts as the following

.. code-block:: javascript

  POST /<RestfulAuthenticationEndPoint>/<authType>/signup/ -H "Content-Type: application/json"
  -d <USER_DATA>

.. note::

  * The authType can be any of this list

    - password (Email/password)
    - //TODO facebook

  * Each authType has his own parameters

Email/Password Authentication
-----------------------------
**Parameters**

+-------------+---------------------------------------------------------+-------------+-------------------------+
| Name        | Description                                             | Type        | Required?               |
+=============+=========================================================+=============+=========================+
| email       | Expiration Date/Time in ISO format                      | String      | yes                     |
+-------------+---------------------------------------------------------+-------------+-------------------------+
| password    | This a unique identifier that identify a specific user. | String      | yes                     |
+-------------+---------------------------------------------------------+-------------+-------------------------+

Request
^^^^^^^
.. code-block:: javascript

  POST /<RestfulAuthenticationEndPoint>/userpass/signup/ -H "Content-Type: application/json"
  -d {"email": "amal@amal.com", "password": "redhat"}

Response
^^^^^^^^
The signup response we contains only the newly created user, you should send a separate login request after that to generate the auth token.

.. code-block:: javascript

  {
     "authType":"password",
     "user": {
        "_r": "/users/123456780222",
        "email": "amal@amal.com",
     }
  }

Login
*****
Client should send this request to generate an auth token.

.. code-block:: javascript

  POST /<RestfulAuthenticationEndPoint>/<authType>/login/ -H "Content-Type: application/json"

.. note::

  1. The authType can be any of this list

    * anonymous
    * password (Email/password)

  2. Each authType has his own parameters

Anonymous Authentication
------------------------
To create anonymous token for a user you can send request like that

Request
^^^^^^^
.. code-block:: javascript

  POST /<RestfulAuthenticationEndPoint>/anonymous/login/ -H "Content-Type: application/json" -d {"nickname": "amal"}

.. warning::

  Any data sent within the request, will encoded in the token and this data is optional

Response
^^^^^^^^
.. code-block:: javascript

  {"authType":"anonymous","accessToken":"1563ab77a8ec29450aa268ecfb956ahhd58"}

Email/Password Authentication
-----------------------------
**Parameters**

+-------------+------------------------------------------------------------------+-------------+-------------------------+
| Name        | Description                                                      | Type        | Required?               |
+=============+==================================================================+=============+=========================+
| email       | The user's email address                                         | String      | yes                     |
+-------------+------------------------------------------------------------------+-------------+-------------------------+
| password    | The user's password that has been used in the signup process	 | String      | yes                     |
+-------------+------------------------------------------------------------------+-------------+-------------------------+

Request
^^^^^^^
.. code-block:: javascript

  POST /<RestfulAuthenticationEndPoint>/password/login/ -H "Content-Type: application/json"
  -d {"email": "amal@amal.com", "password": "redhat"}

Response
^^^^^^^^
.. code-block:: javascript

  {"authType":"password","accessToken":"4563ab77a8ec29450aa268ecfb956ahhd58"}

Logout
******
Clients can invalidate a user's auth token and get them out of the application by clearing stored auth token on the client side.

.. note::

  The generated tokens are valid for 1 day by default. You can change it from the dashboard
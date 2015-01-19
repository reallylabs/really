Access Token
############
Access Token Construction
*************************
Really token is a secure token that is based on JSON Web Tokens (`JWTs <http://jwt.io/>`_).
By decoding this token you can get this info

+-------------+---------------------------------------------------------+-------------+-------------------------------------------------+
| Name        | Description                                             | Type        | Required?                                       |
+=============+=========================================================+=============+=================================================+
| expires     | Expiration Date/Time in ISO format                      | String      | no (if missing, it's assumed to be never expire)|
+-------------+---------------------------------------------------------+-------------+-------------------------------------------------+
| uid         | This a unique identifier that identify a specific user. | String      | yes                                             |
|             | Even anonymous users has an uid                         |             |                                                 |
+-------------+---------------------------------------------------------+-------------+-------------------------------------------------+
| authType    | The authentication method used to generate this token,  | String      | yes                                             |
|             | currently supported methods is ("password", "anonymous")|             |                                                 |
+-------------+---------------------------------------------------------+-------------+-------------------------------------------------+
| _r          | Represent a unique identifier for this user             | String      | no                                              |
+-------------+---------------------------------------------------------+-------------+-------------------------------------------------+
| data        | The token can contains other info about the user        | Json Object | no                                              |
|             |  which depends on the authentication method             |             |                                                 |
+-------------+---------------------------------------------------------+-------------+-------------------------------------------------+

Passing Access Token
********************
An access token must be passed to any API call to Really. For RESTful APIs there are multiple options to choose from

* Passing `access_token` query string argument in URL

  ```https://.../users/?access_token=???```

* Passing the access token on HTTP headers in Authorization
  ```Authorization: Bearer $TOKEN?``` where `$TOKEN?` is the actual token encoded string

You are free to choose the way you like, however, generally the second way is preferred to avoid having intermediate web proxies logging your access token in their log files.


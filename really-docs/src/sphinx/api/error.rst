Error Object
============

+----------+------------------------------------------------------------------------------------+----------+----------+
| Name     | Description                                                                        | Type     | Required |
+==========+====================================================================================+==========+==========+
| code     | The error codes strive to be as close as possible to the standard HTTP Status code | Number   |          |
|          | http://www.w3.org/Protocols/rfc2616/rfc2616-sec6.html#sec6.1.1                     |          |          |
+----------+------------------------------------------------------------------------------------+----------+----------+
| message  |  Short message to describe error happened ex: (Service Unavailable, Bad Gateway)   | String   | String   |
+----------+------------------------------------------------------------------------------------+----------+----------+
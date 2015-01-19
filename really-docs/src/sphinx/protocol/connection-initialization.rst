Connection initialization
=========================
First you should establish a connection to the web-socket channel; you should provide the the ProtocolVersion in the request url.

``wss://<clientid>.api.really.io/v<ProtocolVersion>/socket``

.. note::

  if you are running really as a standalone service, you don't need to specify the *clientid* and replace it with the server ip:port

Example
-------
``wss://a6bcc.api.really.io/v0.1/socket``

The server will validate the protocol version and create a channel for this client. If the **protocolVersion** was invalid, the client will receive an error.

Error
-----
.. code-block:: javascript

  400 Bad Request
  Content-Type: application/json
  {code: 400, reason: "protocol version is not supported"}

Sending the first message
-------------------------
After establishing a connection, the first thing you need to do is to send the init message, you must do this before sending any message on the channel.
The purpose of this message is to synchronize the server with the client so that the server will not be sending any messages before the client is fully ready to receive messages.

Request
-------
.. code-block:: javascript

  {tag: 1, "cmd": "initialize", accessToken: "Ac66bf"}

Access Tokens
^^^^^^^^^^^^^
You **MUST** provide the access token, even anonymous users should have auth token. See :doc:`../auth/access-token` for details on how to pass the access token in your requests.
If you didn't specify an access token or an invalid access token, the server response will be

.. code-block:: javascript

  {
    tag: 1,
    error: {
      code: 401,
      message: "invalid/incorrect/expired access token"
    }
  }

Response
^^^^^^^^
Also the response will have user object, that's because the clients will not be able to read the accessToken info

.. code-block:: javascript

  {
    "tag": 1,
    "evt": "initialized",
    "body": {
      "connection-handle": "667bbaF",

      "user": { //User Info, this is a sample response not reference.
                _r: "/users/1142",
              }
      ...
    }
  }

Token Revoked
^^^^^^^^^^^^^
If your token was revoked or expired for any reason, the server will push this event and the client must handle it appropriately at any time

.. code-block:: javascript

  {
    "evt" : "token-expired",
  }

the server will be disconnecting the socket immediately after sending the `token-expired` message, to avoid

Connection Activity and Timeouts
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
The server will respond to any `ping` command with a `pinged` response like following

.. code-block:: javascript

  {
    tag: 2,
    cmd: "ping"
  }

Response will be

.. code-block:: javascript

  {
    tag: 2,
    evt: "pinged"
  }

.. warning::

  The server will automatically terminate your connection after 120 seconds of idle time after sending "kicked" event

  .. code-block:: javascript

    {
      "evt": "kicked"
    }

  and instantly close the web socket connection

Errors
------
Malformed JSON Message
^^^^^^^^^^^^^^^^^^^^^^
Sending a malformed JSON during a WebSocket connection will return the following message

.. code-block:: javascript

  {error: { code: 400, message: "json.malformed"} }

And the server will terminate the websocket connection immediately after sending that response


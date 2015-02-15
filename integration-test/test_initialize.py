from __future__ import print_function
import websocket
import unittest
import json
from helper import Helper


class TestInitializeFunction(unittest.TestCase):
    def test_invalid_connect(self):
        # websocket.enableTrace(True)
        self.assertRaises(websocket.WebSocketException, websocket.create_connection, "ws://127.0.0.1:9000/v0.2/socket")

    def test_initialize_without_token(self):
        ws = websocket.create_connection(Helper.really_server)
        ws.send("""{
            "tag": 123,
            "traceId" : "@trace123",
            "cmd":"initialize"
            }""")
        result = ws.recv()
        ws.close()
        self.assertEqual(result, """{"r":null,"error":{"code":400,"message":"initialize.invalid"}}""")

    def test_initialize_invalid_token(self):
        ws = websocket.create_connection(Helper.really_server)
        ws.send("""{
            "tag": 123,
            "traceId" : "@trace123",
            "cmd":"initialize",
            "accessToken":"eyJhbG"
            }""")
        result = ws.recv()
        ws.close()
        self.assertEqual(result, """{"r":null,"error":{"code":401,"message":"token.invalid"},"tag":123}""")

    def test_command_without_initialize(self):
        ws = websocket.create_connection(Helper.really_server)
        ws.send("""{
            "tag": 123,
            "traceId" : "@trace123",
            "cmd":"init",
            "accessToken":"eyJhbGciOiJIbWFjU0hBMjU2IiwidHlwIjoiSldUIn0.eyJ1aWQiOiIxMjM0NTY3ODkwIiwiYXV0aFR5cGUiOiJhbm9ueW1vdXMiLCJleHBpcmVzIjoxNDIxNzQ0OTM4NDMyLCJkYXRhIjp7fX0.77-9VTlu77-977-977-977-977-9NGjvv73vv73vv70g77-977-977-977-9Me-_ve-_vRDvv73vv73vv73vv70TQl3vv71h"
            }""")
        result = ws.recv()
        ws.close()
        self.assertEqual(result, '{"r":null,"error":{"code":400,"message":"initialize.required"},"tag":123}')


    def test_initialize_expired_token(self):
        ws = websocket.create_connection(Helper.really_server)
        print("Sending Initialize ")
        ws.send("""{
            "tag": 123,
            "traceId" : "@trace123",
            "cmd":"initialize",
            "accessToken":"eyJhbGciOiJIbWFjU0hBMjU2IiwidHlwIjoiSldUIn0.eyJ1aWQiOiIxMjM0NTY3ODkwIiwiYXV0aFR5cGUiOiJhbm9ueW1vdXMiLCJleHBpcmVzIjoxNDIxNzQ0OTM4NDMyLCJkYXRhIjp7fX0.77-9VTlu77-977-977-977-977-9NGjvv73vv73vv70g77-977-977-977-9Me-_ve-_vRDvv73vv73vv73vv70TQl3vv71h"
            }""")
        result = ws.recv()
        ws.close()
        self.assertEqual(result, """{"r":null,"error":{"code":401,"message":"token.expired"},"tag":123}""")

    def test_valid_initialize(self):
        ws = websocket.create_connection(Helper.really_server)
        request = {
            "tag": 123,
            "traceId": "@trace123",
            "cmd": "initialize",
            "accessToken": Helper.get_anonymous_token()
        }
        ws.send(json.dumps(request))
        result_data = ws.recv()
        ws.close()
        result = json.loads(result_data)
        self.assertEqual(result["tag"] ,123)
        self.assertEqual(result["error"] , None)
        self.assertEqual(result["evt"] ,"initialized")
        self.assertEqual(result["body"]["authType"] ,"anonymous")
        self.assertEqual(result["evt"] ,"initialized")

if __name__ == '__main__':
    unittest.main()
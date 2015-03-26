from __future__ import print_function
import websocket
import unittest
import json
from helper import Helper

class TestCreateFunction(unittest.TestCase):
    #
    # def test_create_no_initialize(self):
    #     ws = websocket.create_connection(Helper.really_server)
    #     ws.send("""{
    #         "tag": 1,
    #         "traceId" : "trace123",
    #         "cmd":"create",
    #         "accessToken":"Ac66bf"
    #         }""")
    #
    #     result= ws.recv()
    #     self.assertEqual(result,"""{"r":null,"error":{"code":400,"message":"initialize.required"},"tag":1}""")
    #     ws.close()


    def test_valid_create(self):
        ws = websocket.create_connection(Helper.really_server)
        init_request = {
            "tag": 123,
            "traceId": "@trace123",
            "cmd": "initialize",
            "accessToken": Helper.get_anonymous_token()
        }
        ws.send(json.dumps(init_request))
        init_data = ws.recv()
        init_result = json.loads(init_data)
        self.assertEqual(init_result["evt"], "initialized")

        ws.send("""{
              "tag" : 123,
              "traceId" : "@trace123",
              "cmd" : "create",
              "r" : "/users/",
              "body" : {
                "firstName": "Ahmed",
                "age": 21
               }
            }""")
        create_data= ws.recv()
        create_result = json.loads(create_data)
        print(create_result)
        self.assertEqual(create_result["body"]["firstName"], "Ahmed")


if __name__ == '__main__':
    unittest.main()
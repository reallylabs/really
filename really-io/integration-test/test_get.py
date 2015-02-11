from __future__ import print_function
import websocket
import unittest
import json
from helper import Helper

class TestGetFunction(unittest.TestCase):


    def test_valid_get(self):
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
                "firstName": "Hatem",
                "lastName" : "AlSum"
               }
            }""")
        create_data= ws.recv()
        create_result = json.loads(create_data)
        self.assertEqual(create_result["body"]["firstName"], "Hatem")

        get_request = {
            "tag": 1,
            "cmd": "get",
            "cmdOpts": {
                "fields": []
            },
            "r": create_result["r"]
        }
        ws.send(json.dumps(get_request))

        get_data= ws.recv()
        ws.close()
        get_result = json.loads(get_data)
        self.assertEqual(get_result["body"]["firstName"], "Hatem")
        self.assertEqual(get_result["body"]["lastName"], "AlSum")


if __name__ == '__main__':
    unittest.main()
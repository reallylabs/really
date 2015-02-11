from __future__ import print_function
import websocket
import unittest
import json
import time
from helper import Helper

class TestUpdateFunction(unittest.TestCase):

    def test_valid_update_one_ops(self):
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

        update_request = {
            "tag": 1,
            "cmd": "update",
            "cmdOpts": {
            },
            "r": create_result["r"],
            "rev": 1,
            "body": {
                "ops": [
                    {
                        "op": "set",
                        "key": "firstName",
                        "value": "Nadeen"
                    }
                ]
            }
        }
        ws.send(json.dumps(update_request))
        update_data= ws.recv()
        update_result=json.loads(update_data)
        self.assertEqual(update_result["r"] ,create_result["r"])
        self.assertEqual(update_result["rev"] ,2)

        #TODO :  sleep should be removed
        time.sleep(5)
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
        print("Read Result {}".format(get_data))
        ws.close()
        get_result = json.loads(get_data)
        self.assertEqual(get_result["body"]["firstName"], "Nadeen")
    

    def test_valid_update_n_ops(self):
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

        update_request = {
            "tag": 1,
            "cmd": "update",
            "cmdOpts": {
            },
            "r": create_result["r"],
            "rev": 1,
            "body": {
                "ops": [
                    {
                        "op": "set",
                        "key": "firstName",
                        "value": "Nadeen"
                    },
                    {
                        "op": "set",
                        "key": "lastName",
                        "value": "Hatem"
                    }
                ]
            }
        }
        ws.send(json.dumps(update_request))
        update_data= ws.recv()
        update_result=json.loads(update_data)
        self.assertEqual(update_result["r"] ,create_result["r"])
        self.assertEqual(update_result["rev"] ,2)

        #TODO :  sleep should be removed 
        time.sleep(5)


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
        self.assertEqual(get_result["body"]["firstName"], "Nadeen")
        self.assertEqual(get_result["body"]["lastName"], "Hatem")


if __name__ == '__main__':
    unittest.main()
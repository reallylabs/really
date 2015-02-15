#
# Copyright (C) 2014-2015 Really Inc. <http://really.io>
#
from __future__ import print_function
import websocket
import unittest
import json
import time
from helper import Helper

class TestUpdateFunction(unittest.TestCase):

    def test_create_no_initialize(self):
        ws = websocket.create_connection(Helper.really_server)
        ws.send("""{
            "tag": 1,
            "traceId" : "trace123",
            "cmd":"create",
            "accessToken":"Ac66bf"
            }""")

        result= ws.recv()
        self.assertEqual(result,"""{"r":null,"error":{"code":400,"message":"initialize.required"},"tag":1}""")
        ws.close()


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
                "firstName": "Hatem",
                "lastName" : "AlSum"
               }
            }""")
        create_data= ws.recv()
        print("Creation Result {}".format(create_data))
        create_result = json.loads(create_data)
        self.assertEqual(create_result["body"]["firstName"], "Hatem")
        #
        # update_request = {
        #     "tag": 1,
        #     "cmd": "update",
        #     "cmdOpts": {
        #     },
        #     "r": create_result["r"],
        #     "rev": 1,
        #     "body": {
        #         "ops": [
        #             {
        #                 "op": "set",
        #                 "key": "firstName",
        #                 "value": "Nadeen"
        #             }
        #         ]
        #     }
        # }
        # ws.send(json.dumps(update_request))
        # update_data= ws.recv()
        # print("Update Result {}".format(update_data))
        # update_result=json.loads(update_data)
        # self.assertEqual(update_result["r"] ,create_result["r"])
        # self.assertEqual(update_result["rev"] ,2)
        #
        # #TODO :  sleep should be removed
        # time.sleep(5)
        subscribe_request = {
            "tag": 123,
            "cmd": "subscribe",
            "body": {
                "subscriptions": [
                    {
                    "r":  str(create_result["r"]),
                    "rev": 1,
                    "fields": ["firstName"]
                    }
                    ]
            }
        }
        print(subscribe_request)
        ws.send(json.dumps(subscribe_request))

        get_data= ws.recv()
        print("subscribe Result {}".format(get_data))
        ws.close()
        subscribe_result = json.loads(get_data)
        self.assertEqual(subscribe_result["body"]["subscriptions"], [ str(create_result["r"])] )


if __name__ == '__main__':
    unittest.main()
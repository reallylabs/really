from __future__ import print_function
import websocket
import unittest
import jwt
import datetime
import time
import json
import requests


class Helper(object):
    really_server = 'ws://127.0.0.1:9000/v0.1/socket'
    really_auth   = 'http://127.0.0.1:9000/auth/anonymous/'
    @staticmethod
    def get_token_outside():
        secret_key = "acee443"
        datetimeobj = datetime.datetime.now() + datetime.timedelta(2)
        date_time_milis = int(time.mktime(datetimeobj.timetuple()) * 1000 + datetimeobj.microsecond / 1000)
        payload = {
            'uid': '1234567890',
            'authType': 'anonymous',
            'expires': date_time_milis,
            'data': {}
        }
        return jwt.encode(payload, secret_key)

    @staticmethod
    def get_anonymous_token():
        url = Helper.really_auth
        headers = {'content-type': 'application/json'}
        result = requests.post(url)
        return json.loads(result.content)['accessToken']

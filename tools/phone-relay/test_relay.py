import importlib.util
import json
import secrets
import tempfile
import threading
import unittest
import time
from pathlib import Path
from urllib.error import HTTPError
from urllib.request import Request, urlopen

spec = importlib.util.spec_from_file_location("phone_relay", Path(__file__).with_name("relay.py"))
relay = importlib.util.module_from_spec(spec)
spec.loader.exec_module(relay)


class RelayTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.database = Path(self.temp.name) / "relay.sqlite3"
        self.start()
        self.alice, self.bob, self.mallory = (secrets.token_urlsafe(32) for _ in range(3))
        self.a, self.b, self.m = (self.register(s) for s in (self.alice, self.bob, self.mallory))

    def start(self):
        self.server = relay.PhoneRelay(("127.0.0.1", 0), self.database)
        self.thread = threading.Thread(target=self.server.serve_forever, daemon=True)
        self.thread.start()
        self.base = f"http://127.0.0.1:{self.server.server_port}"

    def tearDown(self):
        self.server.shutdown()
        self.server.server_close()
        self.thread.join(5)
        self.temp.cleanup()

    def request(self, path, body=None, secret=None):
        headers = {"Content-Type": "application/json"}
        if secret:
            headers["Authorization"] = "Bearer " + secret
        req = Request(self.base + path, data=None if body is None else json.dumps(body).encode(), headers=headers)
        with urlopen(req, timeout=5) as response:
            return json.load(response)

    def register(self, secret):
        return self.request("/v1/register", {"secret": secret, "name": "Local fixture"})["number"]

    def send(self, secret, number, text="Hello", kind="text", url="", key=None):
        return self.request("/v1/messages", {"client_id": key or secrets.token_hex(16), "to": number, "text": text, "kind": kind, "url": url}, secret)["message"]

    def test_numbers_survive_restart(self):
        self.assertEqual(len({self.a, self.b, self.m}), 3)
        self.server.shutdown(); self.server.server_close(); self.thread.join(5)
        self.start()
        self.assertEqual(self.register(self.alice), self.a)
        self.assertEqual(self.register(self.bob), self.b)

    def test_private_offline_inbox_idempotency_and_cursor(self):
        key = secrets.token_hex(16)
        sent = self.send(self.alice, self.b, "Hi Bob", key=key)
        duplicate = self.send(self.alice, self.b, "Hi Bob", key=key)
        self.assertEqual(sent["id"], duplicate["id"])
        inbox = self.request("/v1/messages?after=0", secret=self.bob)
        self.assertEqual(len(inbox["messages"]), 1)
        self.assertEqual(inbox["messages"][0]["sender"], self.a)
        self.assertEqual(self.request("/v1/messages?after=0", secret=self.mallory)["messages"], [])
        self.assertEqual(self.request("/v1/messages?after=" + str(inbox["cursor"]), secret=self.bob)["messages"], [])
        with self.assertRaises(HTTPError) as error:
            self.request("/v1/messages?after=0")
        self.assertEqual(error.exception.code, 401)

    def test_playlist_validation_and_blocking(self):
        link = "https://open.spotify.com/playlist/37i9dQZF1DXcBWIGoYBM5M"
        self.assertEqual(self.send(self.alice, self.b, "Favorites", "playlist", link)["url"], link)
        with self.assertRaises(HTTPError) as error:
            self.send(self.alice, self.b, "Bad link", "playlist", "https://example.com/")
        self.assertEqual(error.exception.code, 400)
        self.request("/v1/block", {"number": self.a, "blocked": True}, self.bob)
        with self.assertRaises(HTTPError) as error:
            self.send(self.alice, self.b)
        self.assertEqual(error.exception.code, 403)
        self.request("/v1/block", {"number": self.a, "blocked": False}, self.bob)
        self.assertEqual(self.send(self.alice, self.b)["recipient"], self.b)

    def test_limits_missing_recipient_and_invalid_input(self):
        for message in ("", "x" * 1001):
            with self.assertRaises(HTTPError) as error:
                self.send(self.alice, self.b, message)
            self.assertEqual(error.exception.code, 400)
        with self.assertRaises(HTTPError) as error:
            self.send(self.alice, "2999999999")
        self.assertEqual(error.exception.code, 404)
        for _ in range(30):
            try:
                self.send(self.alice, self.b)
            except HTTPError:
                break
        with self.assertRaises(HTTPError) as error:
            self.send(self.alice, self.b)
        self.assertEqual(error.exception.code, 429)

    def test_unicode_backlog_is_bounded_and_paginated(self):
        with self.server.db() as db:
            db.executemany("INSERT INTO messages(client_id,sender,recipient,kind,text,url,created) VALUES(?,?,?,?,?,?,?)",
                           [(f"{i:032x}", self.a, self.b, "text", "🌌" * 1000, "", int(time.time()*1000)) for i in range(51)])
        first = self.request("/v1/messages?after=0", secret=self.bob)
        self.assertEqual(len(first["messages"]), 50)
        self.assertLess(len(json.dumps(first, ensure_ascii=False).encode()), 262144)
        second = self.request("/v1/messages?after=" + str(first["cursor"]), secret=self.bob)
        self.assertEqual(len(second["messages"]), 1)


if __name__ == "__main__":
    unittest.main(verbosity=2)

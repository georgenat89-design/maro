import concurrent.futures
import hashlib
import http.client
import json
import tempfile
import threading
import unittest
from pathlib import Path

from server import CommunityServer, MAX_BODY


def preset(module="Example", amount=5):
    return {"format": 1, "module": module, "minecraft": "1.21.11", "client": "test",
            "settings": {"Amount": amount, "Enabled Thing": True}, "extra": {}}


class CommunityTests(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.db = Path(self.temporary.name) / "community.sqlite"
        self.start()
        self.alice = self.identity("Alice")
        self.bob = self.identity("Bob")

    def start(self):
        self.server = CommunityServer(("127.0.0.1", 0), self.db, "test-admin-token")
        self.thread = threading.Thread(target=self.server.serve_forever, daemon=True)
        self.thread.start()

    def stop(self):
        self.server.shutdown()
        self.server.server_close()
        self.thread.join(2)

    def tearDown(self):
        self.stop()
        self.temporary.cleanup()

    def call(self, method, path, data=None, identity=None, code=200, raw=None):
        connection = http.client.HTTPConnection("127.0.0.1", self.server.server_port, timeout=5)
        headers = {"Content-Type": "application/json"}
        if identity:
            headers["Authorization"] = "Bearer " + identity["token"]
        body = raw if raw is not None else json.dumps(data).encode() if data is not None else None
        connection.request(method, path, body, headers)
        response = connection.getresponse()
        result = json.loads(response.read())
        connection.close()
        self.assertEqual(response.status, code, result)
        return result

    def identity(self, name):
        return self.call("POST", "/api/v1/identity", {"name": name})

    def publish(self, config=None, title="Smooth settings", identity=None):
        return self.call("POST", "/api/v1/posts", {"title": title, "description": "Test preset", "config": config or preset()}, identity or self.alice)

    def test_publish_scope_search_and_summaries(self):
        post = self.publish()
        self.publish(preset("Other module"))
        found = self.call("GET", "/api/v1/posts?module=Example&q=SMOOTH", identity=self.alice)["posts"]
        self.assertEqual([p["id"] for p in found], [post["id"]])
        self.assertTrue(found[0]["own"])
        self.assertIsNone(found[0]["config"])
        self.assertEqual(found[0]["settingCount"], 2)
        self.assertEqual(self.call("GET", "/api/v1/posts/" + post["id"])["config"], preset())
        duplicate = self.publish(title="Changed title")
        self.assertEqual(duplicate["id"], post["id"])

    def test_votes_are_idempotent_and_top_is_ranked(self):
        first = self.publish(preset(amount=4), "Older liked")
        second = self.publish(preset(amount=6), "Newer unliked")
        path = "/api/v1/posts/" + first["id"] + "/like"
        for _ in range(2):
            liked = self.call("PUT", path, {"liked": True}, self.bob)
            self.assertEqual(liked["likes"], 1)
            self.assertTrue(liked["liked"])
        self.call("PUT", path, {"liked": True}, self.alice, code=403)
        self.call("PUT", path, {"liked": True}, code=401)
        self.call("PUT", path, {"liked": 1}, self.bob, code=400)
        ranked = self.call("GET", "/api/v1/posts?module=Example&sort=top")["posts"]
        self.assertEqual(ranked[0]["id"], first["id"])
        new = self.call("GET", "/api/v1/posts?module=Example&sort=new")["posts"]
        # Millisecond ties have a stable ID tie-breaker.
        self.assertEqual({p["id"] for p in new}, {first["id"], second["id"]})
        self.assertEqual(self.call("PUT", path, {"liked": False}, self.bob)["likes"], 0)

    def test_concurrent_votes_do_not_duplicate(self):
        post = self.publish()
        path = "/api/v1/posts/" + post["id"] + "/like"
        with concurrent.futures.ThreadPoolExecutor(max_workers=8) as pool:
            votes = list(pool.map(lambda _: self.call("PUT", path, {"liked": True}, self.bob), range(16)))
        self.assertTrue(all(v["likes"] == 1 for v in votes))

    def test_author_and_owner_moderation(self):
        post = self.publish()
        path = "/api/v1/posts/" + post["id"]
        self.call("DELETE", path, identity=self.bob, code=403)
        self.call("DELETE", path, code=403)
        self.call("DELETE", path, identity={"token": "test-admin-token"})
        self.call("GET", path, code=404)
        second = self.publish(preset(amount=6))
        self.call("DELETE", "/api/v1/posts/" + second["id"], identity=self.alice)

    def test_tokens_are_hashed_and_restart_preserves_data(self):
        post = self.publish()
        self.call("PUT", "/api/v1/posts/" + post["id"] + "/like", {"liked": True}, self.bob)
        with self.server.db() as db:
            hashes = [r[0] for r in db.execute("SELECT secret_hash FROM identities")]
        self.assertIn(hashlib.sha256(self.alice["token"].encode()).hexdigest(), hashes)
        self.assertNotIn(self.alice["token"], hashes)
        self.stop()
        self.start()
        restored = self.call("GET", "/api/v1/posts/" + post["id"], identity=self.bob)
        self.assertTrue(restored["liked"])
        self.assertEqual(restored["likes"], 1)
        self.call("DELETE", "/api/v1/posts/" + post["id"], identity=self.alice)

    def test_rejects_unbounded_or_executable_root_data(self):
        for extra in ({"enabled": True}, {"commands": ["/pay someone 100"]}, {"files": "../secret"}):
            config = preset() | extra
            self.call("POST", "/api/v1/posts", {"title": "Invalid", "config": config}, self.alice, code=400)
        self.call("POST", "/api/v1/posts", {"title": "Invalid", "config": preset(amount=float("nan"))}, self.alice, code=400)
        self.call("POST", "/api/v1/posts", identity=self.alice, raw=b"x" * (MAX_BODY + 1), code=413)
        self.call("GET", "/api/v1/posts?offset=not-a-number", code=400)
        self.call("GET", "/api/v1/posts?module=%27%20OR%201=1--")

    def test_pages_are_bounded(self):
        # Seed beyond the publishing rate limit to exercise pagination itself.
        with self.server.db() as db:
            for i in range(51):
                config = json.dumps(preset(amount=i))
                db.execute("INSERT INTO posts VALUES(?,?,?,?,?,?,?,?)",
                           (f"{i:032x}", self.alice["id"], "Example", f"Page {i}", "", config, str(i), i))
        first = self.call("GET", "/api/v1/posts?module=Example")
        self.assertEqual(len(first["posts"]), 50)
        self.assertEqual(first["next"], 50)
        last = self.call("GET", "/api/v1/posts?module=Example&offset=50")
        self.assertEqual(len(last["posts"]), 1)
        self.assertIsNone(last["next"])

    def test_registration_and_post_rate_limits(self):
        for i in range(10):
            self.identity(f"Person{i}")
        self.call("POST", "/api/v1/identity", {"name": "Too many"}, code=429)
        for i in range(8):
            self.publish(preset(amount=i))
        self.call("POST", "/api/v1/posts", {"title": "Too many", "config": preset(amount=10)}, self.alice, code=429)


if __name__ == "__main__":
    unittest.main()

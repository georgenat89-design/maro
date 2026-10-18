"""Maro module-config community API. Python 3.11+, SQLite, no third-party dependencies."""
import argparse
import hashlib
import hmac
import json
import math
import os
import re
import secrets
import sqlite3
import threading
import time
import uuid
from collections import OrderedDict
from contextlib import contextmanager
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import parse_qs, urlsplit

MAX_BODY = 96 * 1024


class ApiError(Exception):
    def __init__(self, code, message):
        self.code, self.message = code, message


def text(value, name, maximum, required=True):
    if not isinstance(value, str) or any(ord(c) < 32 for c in value):
        raise ApiError(400, f"Invalid {name}")
    value = value.strip()
    if (required and not value) or len(value) > maximum:
        raise ApiError(400, f"Invalid {name}")
    return value


def bounded(value, depth=0):
    if depth > 6:
        raise ApiError(400, "Config is too deeply nested")
    if isinstance(value, dict):
        if len(value) > 512:
            raise ApiError(400, "Too many settings")
        for k, v in value.items():
            text(k, "setting name", 120)
            bounded(v, depth + 1)
    elif isinstance(value, list):
        if len(value) > 256:
            raise ApiError(400, "Config list is too large")
        for v in value:
            bounded(v, depth + 1)
    elif isinstance(value, str):
        text(value, "setting value", 256, False)
    elif isinstance(value, (int, float)) and not isinstance(value, bool):
        if not math.isfinite(value) or abs(value) > 1e15:
            raise ApiError(400, "Invalid number")
    elif value is not None and not isinstance(value, bool):
        raise ApiError(400, "Invalid setting")


def validate_config(config):
    if not isinstance(config, dict) or config.get("format") != 1:
        raise ApiError(400, "Unsupported config format")
    if set(config) - {"format", "module", "minecraft", "client", "settings", "extra"}:
        raise ApiError(400, "Unexpected config fields")
    text(config.get("module"), "module", 120)
    text(config.get("minecraft"), "Minecraft version", 40)
    text(config.get("client"), "client version", 80)
    if not isinstance(config.get("settings"), dict) or not isinstance(config.get("extra", {}), dict):
        raise ApiError(400, "Invalid module settings")
    bounded(config)
    return config


class CommunityServer(ThreadingHTTPServer):
    daemon_threads = True

    def __init__(self, address, database, admin_token=""):
        self.database, self.admin_token = str(database), admin_token
        self.rates, self.rate_lock = OrderedDict(), threading.Lock()
        self.slots = threading.BoundedSemaphore(32)
        with self.db() as db:
            db.executescript("""
                PRAGMA journal_mode=WAL;
                CREATE TABLE IF NOT EXISTS identities(id TEXT PRIMARY KEY, secret_hash TEXT UNIQUE NOT NULL, name TEXT NOT NULL);
                CREATE TABLE IF NOT EXISTS posts(id TEXT PRIMARY KEY, owner TEXT NOT NULL REFERENCES identities(id),
                    module TEXT NOT NULL, title TEXT NOT NULL, description TEXT NOT NULL, config TEXT NOT NULL,
                    content_hash TEXT NOT NULL, created INTEGER NOT NULL, UNIQUE(owner,content_hash));
                CREATE TABLE IF NOT EXISTS likes(post TEXT NOT NULL REFERENCES posts(id) ON DELETE CASCADE,
                    identity TEXT NOT NULL REFERENCES identities(id), PRIMARY KEY(post,identity));
                CREATE INDEX IF NOT EXISTS module_posts ON posts(module,created DESC);
            """)
        super().__init__(address, Handler)

    @contextmanager
    def db(self):
        db = sqlite3.connect(self.database, timeout=8)
        db.row_factory = sqlite3.Row
        db.execute("PRAGMA foreign_keys=ON")
        try:
            with db:
                yield db
        finally:
            db.close()

    def limit(self, key, maximum, seconds):
        with self.rate_lock:
            now = time.monotonic()
            start, count = self.rates.pop(key, (now, 0))
            if now - start >= seconds:
                start, count = now, 0
            self.rates[key] = (start, count + 1)
            while len(self.rates) > 4096:
                self.rates.popitem(last=False)
            if count >= maximum:
                raise ApiError(429, "Too many requests; try again later")

    def process_request(self, request, address):
        if not self.slots.acquire(blocking=False):
            request.sendall(b"HTTP/1.1 503 Service Unavailable\r\nContent-Length: 0\r\nConnection: close\r\n\r\n")
            self.shutdown_request(request)
            return
        try:
            super().process_request(request, address)
        except Exception:
            self.slots.release()
            raise

    def process_request_thread(self, request, address):
        try:
            super().process_request_thread(request, address)
        finally:
            self.slots.release()


class Handler(BaseHTTPRequestHandler):
    def setup(self):
        super().setup()
        self.connection.settimeout(8)

    def log_message(self, *_):
        pass

    def body(self):
        try:
            size = int(self.headers.get("Content-Length", "0"))
        except ValueError:
            raise ApiError(400, "Invalid request length")
        if size < 1 or size > MAX_BODY:
            raise ApiError(413, "Request exceeds 96 KB")
        if self.headers.get("Content-Type", "").split(";")[0] != "application/json":
            raise ApiError(415, "Use application/json")
        try:
            data = json.loads(self.rfile.read(size))
        except (ValueError, UnicodeDecodeError):
            raise ApiError(400, "Invalid JSON")
        if not isinstance(data, dict):
            raise ApiError(400, "Expected an object")
        return data

    def identity(self, db, required=False):
        header = self.headers.get("Authorization", "")
        token = header[7:] if header.startswith("Bearer ") else ""
        row = db.execute("SELECT * FROM identities WHERE secret_hash=?", (hashlib.sha256(token.encode()).hexdigest(),)).fetchone() if token else None
        if required and row is None:
            raise ApiError(401, "Connect your community identity first")
        return row

    def post(self, db, id, identity, details=True):
        row = db.execute("""SELECT p.*,i.name AS author,(SELECT COUNT(*) FROM likes WHERE post=p.id) AS likes
                            FROM posts p JOIN identities i ON i.id=p.owner WHERE p.id=?""", (id,)).fetchone()
        if row is None:
            raise ApiError(404, "Config no longer exists")
        config = json.loads(row["config"])
        return {"id": row["id"], "module": row["module"], "title": row["title"], "description": row["description"],
                "author": row["author"], "authorId": row["owner"][:8], "created": row["created"], "likes": row["likes"],
                "liked": bool(identity and db.execute("SELECT 1 FROM likes WHERE post=? AND identity=?", (id, identity["id"])).fetchone()),
                "own": bool(identity and identity["id"] == row["owner"]), "minecraft": config["minecraft"],
                "client": config["client"], "settingCount": len(config["settings"]), "config": config if details else None}

    def route(self):
        path = urlsplit(self.path)
        ip = self.client_address[0]
        # Enable only when this API is reachable exclusively through your reverse proxy.
        if os.getenv("TRUST_PROXY") == "1":
            ip = self.headers.get("X-Real-IP", ip)[:64]
        self.server.limit("ip:" + ip, 300, 60)
        if path.path == "/health" and self.command == "GET":
            return {"ok": True, "api": 1}
        with self.server.db() as db:
            identity = self.identity(db)
            if path.path == "/api/v1/identity" and self.command == "POST":
                self.server.limit("register:" + ip, 12, 3600)
                data = self.body()
                name = text(data.get("name"), "display name", 32)
                secret, id = secrets.token_urlsafe(32), uuid.uuid4().hex
                db.execute("INSERT INTO identities VALUES(?,?,?)", (id, hashlib.sha256(secret.encode()).hexdigest(), name))
                return {"id": id, "token": secret}
            if path.path == "/api/v1/posts" and self.command == "POST":
                identity = self.identity(db, True)
                self.server.limit("post:" + identity["id"], 8, 3600)
                self.server.limit("publish:" + ip, 40, 3600)
                data = self.body()
                title = text(data.get("title"), "title", 80)
                description = text(data.get("description", ""), "description", 600, False)
                config = validate_config(data.get("config"))
                if db.execute("SELECT COUNT(*) FROM posts").fetchone()[0] >= 10000:
                    raise ApiError(503, "Community storage is full; contact its owner")
                packed = json.dumps(config, sort_keys=True, separators=(",", ":"), allow_nan=False)
                digest = hashlib.sha256(packed.encode()).hexdigest()
                old = db.execute("SELECT id FROM posts WHERE owner=? AND content_hash=?", (identity["id"], digest)).fetchone()
                id = old[0] if old else uuid.uuid4().hex
                if not old:
                    db.execute("INSERT INTO posts VALUES(?,?,?,?,?,?,?,?)", (id, identity["id"], config["module"], title, description, packed, digest, int(time.time()*1000)))
                return self.post(db, id, identity)
            if path.path == "/api/v1/posts" and self.command == "GET":
                query = parse_qs(path.query)
                module, search = query.get("module", [""])[0], query.get("q", [""])[0]
                if len(module) > 120 or len(search) > 80:
                    raise ApiError(400, "Search is too long")
                try:
                    offset = max(0, min(10000, int(query.get("offset", ["0"])[0])))
                except ValueError:
                    raise ApiError(400, "Invalid page")
                where, args = "WHERE 1=1", []
                if module:
                    where += " AND module=?"; args.append(module)
                if search:
                    where += " AND (instr(lower(title),lower(?))>0 OR instr(lower(description),lower(?))>0)"; args += [search, search]
                order = "created DESC,id" if query.get("sort", ["top"])[0] == "new" else "votes DESC,created DESC,id"
                rows = db.execute(f"SELECT id,(SELECT COUNT(*) FROM likes WHERE post=p.id) AS votes FROM posts p {where} ORDER BY {order} LIMIT 50 OFFSET ?", [*args, offset]).fetchall()
                return {"posts": [self.post(db, row["id"], identity, False) for row in rows], "next": offset+50 if len(rows) == 50 else None}
            match = re.fullmatch(r"/api/v1/posts/([a-f0-9]{32})(/like)?", path.path)
            if match:
                id, like = match.groups()
                existing = self.post(db, id, identity)
                if self.command == "GET" and not like:
                    return existing
                if self.command == "PUT" and like:
                    identity = self.identity(db, True)
                    self.server.limit("vote:" + identity["id"], 120, 60)
                    if existing["own"]:
                        raise ApiError(403, "You cannot like your own config")
                    data = self.body()
                    if type(data.get("liked")) is not bool:
                        raise ApiError(400, "liked must be true or false")
                    if data["liked"]:
                        db.execute("INSERT OR IGNORE INTO likes VALUES(?,?)", (id, identity["id"]))
                    else:
                        db.execute("DELETE FROM likes WHERE post=? AND identity=?", (id, identity["id"]))
                    return self.post(db, id, identity)
                if self.command == "DELETE" and not like:
                    token = self.headers.get("Authorization", "")[7:]
                    admin = bool(self.server.admin_token and hmac.compare_digest(token, self.server.admin_token))
                    if not existing["own"] and not admin:
                        raise ApiError(403, "Only the author or server owner can delete this config")
                    db.execute("DELETE FROM posts WHERE id=?", (id,))
                    return {"ok": True}
        raise ApiError(404, "Unknown endpoint")

    def dispatch(self):
        try:
            body, code = self.route(), 200
        except ApiError as error:
            body, code = {"error": error.message}, error.code
        except (sqlite3.Error, ValueError, TypeError, OverflowError, RecursionError):
            body, code = {"error": "Request could not be completed"}, 400
        data = json.dumps(body, allow_nan=False, separators=(",", ":")).encode()
        self.send_response(code)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(data)))
        self.send_header("Cache-Control", "no-store")
        self.send_header("X-Content-Type-Options", "nosniff")
        self.end_headers()
        self.wfile.write(data)

    do_GET = do_POST = do_PUT = do_DELETE = dispatch


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--host", default="127.0.0.1")
    parser.add_argument("--port", type=int, default=8787)
    parser.add_argument("--db", default="community.sqlite")
    args = parser.parse_args()
    server = CommunityServer((args.host, args.port), args.db, os.getenv("COMMUNITY_ADMIN_TOKEN", ""))
    print(f"Maro community API listening on {args.host}:{server.server_port}", flush=True)
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        pass
    finally:
        server.server_close()

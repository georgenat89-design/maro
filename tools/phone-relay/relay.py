"""Maro phone test relay. Python 3.11+, standard library only; SQLite persists numbers/messages."""
import argparse
import hashlib
import json
import re
import secrets
import sqlite3
import threading
import time
import os
from contextlib import contextmanager
from collections import OrderedDict
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from urllib.parse import parse_qs, urlsplit, unquote

NUMBER = re.compile(r"[2-9][0-9]{9}\Z")
SECRET = re.compile(r"[A-Za-z0-9_-]{43}\Z")
PLAYLIST = re.compile(r"https://open\.spotify\.com/playlist/[A-Za-z0-9]{22}\Z")
VIDEO = re.compile(r"/v1/videos/([a-f0-9]{32})\.mp4\Z")
MAX_VIDEO = 32 * 1024 * 1024


class PhoneRelay(ThreadingHTTPServer):
    daemon_threads = True
    def __init__(self, address, database):
        self.database = str(database)
        self.media = Path(str(database) + ".videos")
        self.media.mkdir(parents=True, exist_ok=True)
        self.rates = OrderedDict()
        self.rate_lock = threading.Lock()
        with self.db() as db:
            db.executescript("""
                PRAGMA journal_mode=WAL;
                CREATE TABLE IF NOT EXISTS phones (
                    secret_hash TEXT PRIMARY KEY, number TEXT NOT NULL UNIQUE, name TEXT NOT NULL);
                CREATE TABLE IF NOT EXISTS messages (
                    id INTEGER PRIMARY KEY AUTOINCREMENT, client_id TEXT NOT NULL,
                    sender TEXT NOT NULL, recipient TEXT NOT NULL, kind TEXT NOT NULL,
                    text TEXT NOT NULL, url TEXT NOT NULL, created INTEGER NOT NULL,
                    UNIQUE(sender, client_id));
                CREATE INDEX IF NOT EXISTS inbox ON messages(recipient, id);
                CREATE INDEX IF NOT EXISTS sent ON messages(sender, id);
                CREATE TABLE IF NOT EXISTS blocks (
                    owner TEXT NOT NULL, blocked TEXT NOT NULL, PRIMARY KEY(owner, blocked));
                CREATE TABLE IF NOT EXISTS videos (
                    id TEXT PRIMARY KEY, sender TEXT NOT NULL, recipient TEXT NOT NULL,
                    size INTEGER NOT NULL, created INTEGER NOT NULL);
            """)
        super().__init__(address, Handler)

    @contextmanager
    def db(self):
        db = sqlite3.connect(self.database, timeout=5)
        db.row_factory = sqlite3.Row
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
                raise ApiError(429, "Too many requests; try again shortly")


class ApiError(Exception):
    def __init__(self, status, message):
        self.status, self.message = status, message


class Handler(BaseHTTPRequestHandler):
    server_version = "MaroPhone/1"
    def log_message(self, *_):
        pass  # Never log credentials, bodies or phone numbers.

    def reply(self, status, data):
        body = json.dumps(data, ensure_ascii=False).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        self.send_header("Cache-Control", "no-store")
        self.send_header("X-Content-Type-Options", "nosniff")
        self.end_headers()
        self.wfile.write(body)

    def body(self):
        if self.headers.get("Transfer-Encoding"):
            raise ApiError(400, "Unsupported request encoding")
        try:
            size = int(self.headers.get("Content-Length", "0"))
            if not 0 < size <= 8192:
                raise ApiError(413, "Request too large")
            data = json.loads(self.rfile.read(size))
            if not isinstance(data, dict):
                raise ValueError()
            return data
        except (ValueError, UnicodeError):
            raise ApiError(400, "Invalid JSON")

    def auth(self, db):
        auth = self.headers.get("Authorization", "")
        if not auth.startswith("Bearer ") or not SECRET.fullmatch(auth[7:]):
            raise ApiError(401, "Connect your phone first")
        digest = hashlib.sha256(auth[7:].encode()).hexdigest()
        phone = db.execute("SELECT number, name FROM phones WHERE secret_hash=?", (digest,)).fetchone()
        if phone is None:
            raise ApiError(401, "Connect your phone first")
        self.server.limit(("phone", digest), 120, 60)
        return phone["number"]

    @staticmethod
    def message(row):
        return {k: row[k] for k in ("id", "client_id", "sender", "recipient", "kind", "text", "url", "created")}

    def video_upload(self, db, number, query):
        values = parse_qs(query)
        target, key = values.get("to", [""])[0], values.get("client_id", [""])[0]
        if not NUMBER.fullmatch(target) or not re.fullmatch(r"[A-Za-z0-9-]{16,64}", key):
            raise ApiError(400, "Enter a valid phone number")
        if db.execute("SELECT 1 FROM phones WHERE number=?", (target,)).fetchone() is None:
            raise ApiError(404, "That number is not registered on this relay")
        if db.execute("SELECT 1 FROM blocks WHERE owner=? AND blocked=?", (target, number)).fetchone():
            raise ApiError(403, "This number cannot receive your video")
        prior = db.execute("SELECT * FROM messages WHERE sender=? AND client_id=?", (number, key)).fetchone()
        if prior is not None:
            if prior["kind"] != "video" or prior["recipient"] != target:
                raise ApiError(409, "Attachment ID already used")
            return {"message": self.message(prior)}
        if self.headers.get("Transfer-Encoding") or self.headers.get("Content-Type", "").split(";")[0] != "video/mp4":
            raise ApiError(400, "Choose an MP4 video")
        try:
            size = int(self.headers.get("Content-Length", "0"))
        except ValueError:
            raise ApiError(400, "Invalid video size")
        if not 12 <= size <= MAX_VIDEO:
            raise ApiError(413, "Videos must be 32 MB or smaller")
        self.server.limit(("video", number), 6, 60)
        title = unquote(self.headers.get("X-Video-Name", "Video.mp4"))
        title = "".join(c for c in title if ord(c) >= 32 and c not in "/\\")[:80] or "Video.mp4"
        token = secrets.token_hex(16)
        temporary, destination = self.server.media / (token + ".part"), self.server.media / (token + ".mp4")
        try:
            self.connection.settimeout(30)
            with temporary.open("xb") as out:
                left = size
                first = True
                while left:
                    data = self.rfile.read(min(65536, left))
                    if not data:
                        raise ApiError(400, "Incomplete video upload")
                    if first and (len(data) < 12 or data[4:8] != b"ftyp"):
                        raise ApiError(400, "Choose an MP4 video")
                    first = False
                    out.write(data)
                    left -= len(data)
            db.execute("BEGIN IMMEDIATE")
            used, count = db.execute("SELECT COALESCE(SUM(size),0),COUNT(*) FROM videos WHERE sender=?", (number,)).fetchone()
            total, all_count = db.execute("SELECT COALESCE(SUM(size),0),COUNT(*) FROM videos").fetchone()
            if used + size > 256 * 1024 * 1024 or count >= 48 or total + size > 2 * 1024**3 or all_count >= 512:
                raise ApiError(413, "Video storage is full; try again later")
            if db.execute("SELECT 1 FROM blocks WHERE owner=? AND blocked=?", (target, number)).fetchone():
                raise ApiError(403, "This number cannot receive your video")
            created = int(time.time()*1000)
            db.execute("INSERT INTO videos VALUES(?,?,?,?,?)", (token, number, target, size, created))
            db.execute("INSERT INTO messages(client_id,sender,recipient,kind,text,url,created) VALUES(?,?,?,?,?,?,?)",
                       (key, number, target, "video", title, "/v1/videos/" + token + ".mp4", created))
            os.replace(temporary, destination)
            row = db.execute("SELECT * FROM messages WHERE sender=? AND client_id=?", (number, key)).fetchone()
            return {"message": self.message(row)}
        finally:
            temporary.unlink(missing_ok=True)

    def video_download(self, db, number, path):
        match = VIDEO.fullmatch(path)
        if match is None:
            raise ApiError(404, "Video not found")
        row = db.execute("SELECT * FROM videos WHERE id=? AND (sender=? OR recipient=?)", (match[1], number, number)).fetchone()
        if row is None:
            raise ApiError(404, "Video not found")
        file = self.server.media / (match[1] + ".mp4")
        if not file.is_file():
            raise ApiError(404, "Video no longer available")
        self.send_response(200)
        self.send_header("Content-Type", "video/mp4")
        self.send_header("Content-Length", str(row["size"]))
        self.send_header("Cache-Control", "private, no-store")
        self.send_header("X-Content-Type-Options", "nosniff")
        self.end_headers()
        with file.open("rb") as source:
            while data := source.read(65536):
                self.wfile.write(data)

    def handle_api(self, method):
        try:
            self.connection.settimeout(10)
            url = urlsplit(self.path)
            with self.server.db() as db:
                if method == "GET" and url.path == "/health":
                    result = {"service": "maro-phone", "version": 1}
                elif method == "POST" and url.path == "/v1/register":
                    data = self.body()
                    secret, name = data.get("secret", ""), data.get("name", "Player")
                    if not isinstance(secret, str) or not SECRET.fullmatch(secret) or not isinstance(name, str):
                        raise ApiError(400, "Invalid phone identity")
                    digest = hashlib.sha256(secret.encode()).hexdigest()
                    db.execute("BEGIN IMMEDIATE")
                    row = db.execute("SELECT number FROM phones WHERE secret_hash=?", (digest,)).fetchone()
                    if row is None:
                        self.server.limit(("register", self.client_address[0]), 20, 3600)
                        while True:
                            number = str(2_000_000_000 + secrets.randbelow(8_000_000_000))
                            if db.execute("SELECT 1 FROM phones WHERE number=?", (number,)).fetchone() is None:
                                break
                        db.execute("INSERT INTO phones VALUES(?,?,?)", (digest, number, name[:24]))
                    else:
                        number = row["number"]
                        db.execute("UPDATE phones SET name=? WHERE secret_hash=?", (name[:24], digest))
                    result = {"number": number}
                else:
                    number = self.auth(db)
                    if method == "POST" and url.path == "/v1/videos":
                        result = self.video_upload(db, number, url.query)
                    elif method == "GET" and url.path.startswith("/v1/videos/"):
                        self.video_download(db, number, url.path)
                        return
                    elif method == "GET" and url.path == "/v1/messages":
                        try:
                            cursor = max(0, int(parse_qs(url.query).get("after", ["0"])[0]))
                        except ValueError:
                            raise ApiError(400, "Invalid message cursor")
                        db.execute("DELETE FROM messages WHERE created<?", (int((time.time()-30*86400)*1000),))
                        expired = db.execute("SELECT id FROM videos WHERE created<?", (int((time.time()-30*86400)*1000),)).fetchall()
                        for item in expired:
                            (self.server.media / (item["id"] + ".mp4")).unlink(missing_ok=True)
                        db.execute("DELETE FROM videos WHERE created<?", (int((time.time()-30*86400)*1000),))
                        # Even 1,000-character Unicode messages stay within the client's response cap.
                        rows = db.execute("SELECT * FROM messages WHERE id>? AND (sender=? OR recipient=?) ORDER BY id LIMIT 50", (cursor, number, number)).fetchall()
                        result = {"messages": [self.message(r) for r in rows], "cursor": rows[-1]["id"] if rows else cursor}
                    elif method == "POST" and url.path == "/v1/messages":
                        data = self.body()
                        target, key, kind = data.get("to", ""), data.get("client_id", ""), data.get("kind", "text")
                        text, link = data.get("text", ""), data.get("url", "")
                        if not all(isinstance(v, str) for v in (target, key, kind, text, link)):
                            raise ApiError(400, "Invalid message")
                        if not NUMBER.fullmatch(target) or not re.fullmatch(r"[A-Za-z0-9-]{16,64}", key):
                            raise ApiError(400, "Enter a valid phone number")
                        if kind not in ("text", "playlist") or not text.strip() or len(text) > 1000 or any(ord(c)<32 and c not in "\n\t" for c in text):
                            raise ApiError(400, "Messages must contain 1–1000 characters")
                        if kind == "playlist" and not PLAYLIST.fullmatch(link) or kind == "text" and link:
                            raise ApiError(400, "Invalid Spotify playlist link")
                        row = db.execute("SELECT * FROM messages WHERE sender=? AND client_id=?", (number, key)).fetchone()
                        if row is None:
                            self.server.limit(("send", number), 30, 60)
                            if db.execute("SELECT 1 FROM phones WHERE number=?", (target,)).fetchone() is None:
                                raise ApiError(404, "That number is not registered on this relay")
                            if db.execute("SELECT 1 FROM blocks WHERE owner=? AND blocked=?", (target, number)).fetchone():
                                raise ApiError(403, "This number cannot receive your message")
                            db.execute("INSERT INTO messages(client_id,sender,recipient,kind,text,url,created) VALUES(?,?,?,?,?,?,?)", (key, number, target, kind, text.strip(), link, int(time.time()*1000)))
                            row = db.execute("SELECT * FROM messages WHERE sender=? AND client_id=?", (number, key)).fetchone()
                        result = {"message": self.message(row)}
                    elif method == "POST" and url.path == "/v1/block":
                        data = self.body()
                        target, blocked = data.get("number", ""), data.get("blocked")
                        if not isinstance(target, str) or not NUMBER.fullmatch(target) or not isinstance(blocked, bool):
                            raise ApiError(400, "Invalid number")
                        if blocked:
                            db.execute("INSERT OR IGNORE INTO blocks VALUES(?,?)", (number, target))
                        else:
                            db.execute("DELETE FROM blocks WHERE owner=? AND blocked=?", (number, target))
                        result = {"blocked": blocked}
                    else:
                        raise ApiError(404, "Not found")
            self.reply(200, result)
        except ApiError as error:
            self.reply(error.status, {"error": error.message})
        except (ValueError, TypeError, OverflowError):
            self.reply(400, {"error": "Invalid request"})
        except (TimeoutError, ConnectionError, BrokenPipeError):
            pass
        except sqlite3.Error:
            self.reply(503, {"error": "Relay busy; try again shortly"})

    def do_GET(self):
        self.handle_api("GET")
    def do_POST(self):
        self.handle_api("POST")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--host", default="127.0.0.1")
    parser.add_argument("--port", type=int, default=8765)
    parser.add_argument("--database", type=Path, default=Path("phone-relay.sqlite3"))
    args = parser.parse_args()
    server = PhoneRelay((args.host, args.port), args.database)
    print(f"Maro phone test relay listening on {args.host}:{server.server_port}", flush=True)
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        pass
    finally:
        server.server_close()

if __name__ == "__main__":
    main()

#!/usr/bin/env python3
"""
Minimal fake MPD and SnapCast servers for exercising the ghac Android app.

The emulator reaches the host through 10.0.2.2, so pointing the app's Settings
screen at that address drives these. Enough of each protocol is implemented to
populate every screen and to prove that commands sent by the app arrive in the
expected wire form.
"""

import json
import socket
import socketserver
import threading

# ── Shared state ─────────────────────────────────────────────────────────────

LIBRARY = {
    "": [
        ("directory", "Neil Young"),
        ("directory", "Pink Floyd"),
        ("file", "intro sting.flac", "Intro Sting", "Various"),
    ],
    "Neil Young": [
        ("directory", "Neil Young/Harvest"),
    ],
    "Neil Young/Harvest": [
        ("file", "Neil Young/Harvest/01 Out on the Weekend.flac", "Out on the Weekend", "Neil Young"),
        ("file", "Neil Young/Harvest/02 Harvest.flac", "Harvest", "Neil Young"),
    ],
    "Pink Floyd": [
        ("directory", "Pink Floyd/The Wall"),
    ],
    "Pink Floyd/The Wall": [
        ("file", "Pink Floyd/The Wall/01 In the Flesh.flac", "In the Flesh?", "Pink Floyd"),
    ],
}

QUEUE = [
    {"file": "Neil Young/Harvest/01 Out on the Weekend.flac",
     "Title": "Out on the Weekend", "Artist": "Neil Young", "Album": "Harvest"},
    {"file": "Neil Young/Harvest/02 Harvest.flac",
     "Title": "Harvest", "Artist": "Neil Young", "Album": "Harvest"},
    {"file": "Pink Floyd/The Wall/01 In the Flesh.flac",
     "Title": "In the Flesh?", "Artist": "Pink Floyd", "Album": "The Wall"},
]

PLAYER = {"state": "play", "song": 0, "elapsed": 61.5, "duration": 245.0, "random": 0}

SNAP_CLIENTS = [
    {"id": "aa:bb:cc:01", "name": "Kitchen", "percent": 45, "muted": False, "connected": True},
    {"id": "aa:bb:cc:02", "name": "Living Room", "percent": 70, "muted": False, "connected": True},
    {"id": "aa:bb:cc:03", "name": "Study", "percent": 20, "muted": True, "connected": True},
    {"id": "aa:bb:cc:04", "name": "", "percent": 55, "muted": False, "connected": False},
]

LOCK = threading.Lock()


def log(who, msg):
    print(f"[{who}] {msg}", flush=True)


# ── MPD ──────────────────────────────────────────────────────────────────────

def parse_args(line):
    """Splits an MPD command line, honouring quotes and backslash escapes."""
    parts, current, in_quotes, escaped = [], "", False, False
    for ch in line:
        if escaped:
            current += ch
            escaped = False
        elif ch == "\\":
            escaped = True
        elif ch == '"':
            in_quotes = not in_quotes
            if not in_quotes:
                parts.append(current)
                current = ""
        elif ch == " " and not in_quotes:
            if current:
                parts.append(current)
                current = ""
        else:
            current += ch
    if current:
        parts.append(current)
    return parts


class MpdHandler(socketserver.StreamRequestHandler):
    def handle(self):
        self.wfile.write(b"OK MPD 0.23.5\n")
        self.wfile.flush()
        # An idle connection must not answer until something changes; this fake
        # has no background activity, so it simply parks.
        self.idle_block = threading.Event()

        while True:
            raw = self.rfile.readline()
            if not raw:
                return
            line = raw.decode("utf-8", "replace").strip()
            if not line:
                continue
            log("mpd", f"<- {line}")
            try:
                if not self.dispatch(line):
                    return
            except Exception as exc:  # noqa: BLE001
                self.send(f"ACK [5@0] {{}} {exc}\n", ok=False)

    def send(self, payload="", ok=True):
        if payload:
            self.wfile.write(payload.encode())
        if ok:
            self.wfile.write(b"OK\n")
        self.wfile.flush()

    def dispatch(self, line):
        args = parse_args(line)
        cmd = args[0] if args else ""
        rest = args[1:]

        if cmd == "status":
            with LOCK:
                body = (f"state: {PLAYER['state']}\n"
                        f"random: {PLAYER['random']}\n"
                        f"elapsed: {PLAYER['elapsed']}\n"
                        f"duration: {PLAYER['duration']}\n"
                        f"playlistlength: {len(QUEUE)}\n")
                if PLAYER["song"] is not None:
                    body += f"song: {PLAYER['song']}\n"
            self.send(body)

        elif cmd == "currentsong":
            with LOCK:
                idx = PLAYER["song"]
                song = QUEUE[idx] if idx is not None and idx < len(QUEUE) else None
            if song:
                body = "".join(f"{k}: {v}\n" for k, v in song.items())
                body += f"Pos: {idx}\n"
                self.send(body)
            else:
                self.send()

        elif cmd == "playlistinfo":
            with LOCK:
                body = ""
                for i, song in enumerate(QUEUE):
                    body += "".join(f"{k}: {v}\n" for k, v in song.items())
                    body += f"Pos: {i}\nId: {i + 100}\n"
            self.send(body)

        elif cmd == "lsinfo":
            path = rest[0] if rest else ""
            entries = LIBRARY.get(path, [])
            body = ""
            for entry in entries:
                if entry[0] == "directory":
                    body += f"directory: {entry[1]}\n"
                else:
                    body += f"file: {entry[1]}\nTitle: {entry[2]}\nArtist: {entry[3]}\n"
            self.send(body)

        elif cmd == "idle":
            # Park forever; the client closes the socket to cancel.
            self.idle_block.wait()
            return False

        elif cmd == "play":
            with LOCK:
                if rest:
                    PLAYER["song"] = int(rest[0])
                PLAYER["state"] = "play"
            self.send()

        elif cmd == "pause":
            with LOCK:
                PLAYER["state"] = "pause"
            self.send()

        elif cmd == "random":
            with LOCK:
                PLAYER["random"] = int(rest[0]) if rest else 0
            self.send()

        elif cmd == "add":
            uri = rest[0] if rest else ""
            log("mpd", f"   ADD received uri={uri!r}")
            with LOCK:
                QUEUE.append({"file": uri, "Title": uri.split("/")[-1],
                              "Artist": "Added", "Album": ""})
            self.send()

        elif cmd == "delete":
            with LOCK:
                pos = int(rest[0])
                if 0 <= pos < len(QUEUE):
                    QUEUE.pop(pos)
            self.send()

        elif cmd == "clear":
            with LOCK:
                QUEUE.clear()
                PLAYER["song"] = None
            self.send()

        elif cmd == "move":
            with LOCK:
                frm, to = int(rest[0]), int(rest[1])
                QUEUE.insert(to, QUEUE.pop(frm))
            self.send()

        elif cmd in ("ping", "update"):
            self.send()

        else:
            self.send()

        return True


# ── SnapCast ─────────────────────────────────────────────────────────────────

SNAP_SUBSCRIBERS = []
SNAP_LOCK = threading.Lock()


def snap_status():
    with LOCK:
        return {
            "server": {
                "server": {"snapserver": {"version": "0.27.0"}},
                "groups": [
                    {
                        "id": "group-1",
                        "clients": [
                            {
                                "id": c["id"],
                                "connected": c["connected"],
                                "config": {
                                    "name": c["name"],
                                    "volume": {"muted": c["muted"], "percent": c["percent"]},
                                },
                                "host": {"name": f"pi-{c['id'][-2:]}"},
                            }
                            for c in SNAP_CLIENTS
                        ],
                    }
                ],
            }
        }


def broadcast_notification(method, params):
    payload = json.dumps({"jsonrpc": "2.0", "method": method, "params": params}) + "\r\n"
    with SNAP_LOCK:
        targets = list(SNAP_SUBSCRIBERS)
    for wfile in targets:
        try:
            wfile.write(payload.encode())
            wfile.flush()
        except Exception:  # noqa: BLE001
            pass


class SnapHandler(socketserver.StreamRequestHandler):
    def handle(self):
        with SNAP_LOCK:
            SNAP_SUBSCRIBERS.append(self.wfile)
        try:
            while True:
                raw = self.rfile.readline()
                if not raw:
                    return
                line = raw.decode("utf-8", "replace").strip()
                if not line:
                    continue
                log("snap", f"<- {line}")
                try:
                    request = json.loads(line)
                except json.JSONDecodeError:
                    continue
                self.respond(request)
        finally:
            with SNAP_LOCK:
                if self.wfile in SNAP_SUBSCRIBERS:
                    SNAP_SUBSCRIBERS.remove(self.wfile)

    def respond(self, request):
        method = request.get("method")
        params = request.get("params") or {}
        req_id = request.get("id")
        result = {}

        if method == "Server.GetStatus":
            result = snap_status()

        elif method == "Client.SetVolume":
            volume = params.get("volume", {})
            log("snap", f"   SET VOLUME id={params.get('id')} "
                        f"percent={volume.get('percent')} muted={volume.get('muted')}")
            with LOCK:
                for c in SNAP_CLIENTS:
                    if c["id"] == params.get("id"):
                        c["percent"] = volume.get("percent", c["percent"])
                        c["muted"] = volume.get("muted", c["muted"])
            result = {"volume": volume}
            threading.Thread(
                target=broadcast_notification,
                args=("Client.OnVolumeChanged", {"id": params.get("id"), "volume": volume}),
                daemon=True,
            ).start()

        elif method == "Client.SetName":
            log("snap", f"   SET NAME id={params.get('id')} name={params.get('name')!r}")
            with LOCK:
                for c in SNAP_CLIENTS:
                    if c["id"] == params.get("id"):
                        c["name"] = params.get("name", c["name"])
            result = {"name": params.get("name")}

        if req_id is not None:
            payload = json.dumps({"jsonrpc": "2.0", "id": req_id, "result": result}) + "\r\n"
            self.wfile.write(payload.encode())
            self.wfile.flush()


class ThreadedTCPServer(socketserver.ThreadingTCPServer):
    allow_reuse_address = True
    daemon_threads = True


if __name__ == "__main__":
    mpd = ThreadedTCPServer(("0.0.0.0", 6600), MpdHandler)
    snap = ThreadedTCPServer(("0.0.0.0", 1705), SnapHandler)

    threading.Thread(target=mpd.serve_forever, daemon=True).start()
    threading.Thread(target=snap.serve_forever, daemon=True).start()

    log("main", "fake MPD on :6600, fake SnapCast on :1705 (emulator host = 10.0.2.2)")
    try:
        threading.Event().wait()
    except KeyboardInterrupt:
        pass

"""Minimal RCON client for testing the Flying dedicated server (no deps)."""
import socket, struct, sys

HOST, PORT, PASS = "127.0.0.1", 25575, "testpass"

def pkt(req_id, ptype, body):
    data = struct.pack("<ii", req_id, ptype) + body.encode("utf-8") + b"\x00\x00"
    return struct.pack("<i", len(data)) + data

def recv_pkt(sock):
    raw = b""
    while len(raw) < 4:
        raw += sock.recv(4 - len(raw))
    (length,) = struct.unpack("<i", raw)
    data = b""
    while len(data) < length:
        data += sock.recv(length - len(data))
    rid, ptype = struct.unpack("<ii", data[:8])
    return rid, ptype, data[8:-2].decode("utf-8", "replace")

def main():
    sock = socket.create_connection((HOST, PORT), timeout=10)
    sock.sendall(pkt(1, 3, PASS))
    rid, _, _ = recv_pkt(sock)
    if rid == -1:
        print("RCON AUTH FAILED"); sys.exit(1)
    print("== RCON authed ==")
    for i, cmd in enumerate(sys.argv[1:], start=10):
        sock.sendall(pkt(i, 2, cmd))
        rid, ptype, body = recv_pkt(sock)
        print(f"$ {cmd}\n  -> {body!r}")
    sock.close()

main()

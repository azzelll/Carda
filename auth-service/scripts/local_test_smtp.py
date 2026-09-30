"""Loopback-only SMTP sink for disposable Carda account tests.

Messages contain verification/reset codes. The output file must stay outside Git and
must be deleted after the test. This service must never be exposed to a network.
"""

import argparse
import os
from pathlib import Path
import socketserver
import tempfile
from threading import Lock


class MailHandler(socketserver.StreamRequestHandler):
    def handle(self):
        self.wfile.write(b"220 carda-local-test SMTP\r\n")
        message = bytearray()
        in_data = False
        while True:
            line = self.rfile.readline(65537)
            if not line or len(line) > 65536:
                break
            if in_data:
                if line == b".\r\n":
                    self.server.save_message(bytes(message))
                    message.clear()
                    in_data = False
                    self.wfile.write(b"250 queued\r\n")
                else:
                    if line.startswith(b".."):
                        line = line[1:]
                    message.extend(line)
                    if len(message) > 1048576:
                        self.wfile.write(b"552 message too large\r\n")
                        break
                continue
            command = line.decode("ascii", "replace").split(" ", 1)[0].strip().upper()
            if command in {"EHLO", "HELO"}:
                self.wfile.write(b"250-carda-local-test\r\n250 8BITMIME\r\n")
            elif command == "DATA":
                in_data = True
                message.clear()
                self.wfile.write(b"354 end with dot\r\n")
            elif command == "QUIT":
                self.wfile.write(b"221 bye\r\n")
                break
            elif command == "RSET":
                message.clear()
                self.wfile.write(b"250 reset\r\n")
            else:
                self.wfile.write(b"250 ok\r\n")


class LocalTestSmtp(socketserver.ThreadingTCPServer):
    allow_reuse_address = True
    daemon_threads = True

    def __init__(self, port: int, output: Path):
        super().__init__(("127.0.0.1", port), MailHandler)
        self.output = output
        self.lock = Lock()

    def save_message(self, message: bytes):
        with self.lock:
            with self.output.open("ab") as out:
                os.fchmod(out.fileno(), 0o600)
                out.write(b"\n--- CARDA DISPOSABLE TEST MESSAGE ---\n")
                out.write(message)
                out.write(b"\n")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--port", type=int, default=25252)
    parser.add_argument("--output", type=Path,
                        default=Path(tempfile.gettempdir()) / "carda-test-mail.log")
    args = parser.parse_args()
    if not 1 <= args.port <= 65535:
        parser.error("port must be between 1 and 65535")
    args.output.parent.mkdir(parents=True, exist_ok=True)
    if args.output.exists():
        os.chmod(args.output, 0o600)
    with LocalTestSmtp(args.port, args.output) as server:
        print(f"Disposable SMTP on 127.0.0.1:{args.port}; messages in {args.output}", flush=True)
        server.serve_forever()


if __name__ == "__main__":
    main()

import os
from pathlib import Path
import smtplib
import tempfile
from threading import Thread
import unittest

from local_test_smtp import LocalTestSmtp


class LocalTestSmtpTest(unittest.TestCase):
    def test_disposable_message_stays_on_loopback_in_private_file(self):
        with tempfile.TemporaryDirectory() as directory:
            output = Path(directory) / "mail.log"
            with LocalTestSmtp(0, output) as server:
                thread = Thread(target=server.serve_forever, daemon=True)
                thread.start()
                try:
                    self.assertEqual("127.0.0.1", server.server_address[0])
                    with smtplib.SMTP("127.0.0.1", server.server_address[1], timeout=3) as smtp:
                        smtp.sendmail("sender@carda.invalid", ["recipient@carda.invalid"],
                                      "Subject: disposable test\r\n\r\nverification-code-123")
                finally:
                    server.shutdown()
                    thread.join(timeout=3)
            self.assertIn(b"verification-code-123", output.read_bytes())
            self.assertEqual(0o600, os.stat(output).st_mode & 0o777)


if __name__ == "__main__":
    unittest.main()

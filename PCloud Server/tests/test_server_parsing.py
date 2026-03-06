import unittest

import pcloud_server


class ServerParsingTests(unittest.TestCase):
    def test_parse_login_payload_newline(self):
        username, password = pcloud_server.parse_login_payload("userA\nPass123!")
        self.assertEqual("userA", username)
        self.assertEqual("Pass123!", password)

    def test_parse_login_payload_whitespace_fallback(self):
        username, password = pcloud_server.parse_login_payload("userB   Pass456!")
        self.assertEqual("userB", username)
        self.assertEqual("Pass456!", password)

    def test_parse_login_payload_sanitizes_control_chars(self):
        username, password = pcloud_server.parse_login_payload("user\x00\r\nPass!\r")
        self.assertEqual("user", username)
        self.assertEqual("Pass!", password)

    def test_parse_register_payload_fields(self):
        username, password, full_name, birth_date = pcloud_server.parse_register_payload(
            "newUser\nP@ss12\nJohn Doe\n01/01/2000"
        )
        self.assertEqual("newUser", username)
        self.assertEqual("P@ss12", password)
        self.assertEqual("John Doe", full_name)
        self.assertEqual("01/01/2000", birth_date)

    def test_parse_register_payload_sanitization_negative(self):
        username, password, full_name, birth_date = pcloud_server.parse_register_payload(
            "bad user!\npa\x00ss\nJohn123 Doe!!\n01-01-2000"
        )
        self.assertEqual("baduser", username)
        self.assertEqual("pass", password)
        self.assertEqual("John Doe", full_name)
        self.assertEqual("01012000", birth_date)


if __name__ == "__main__":
    unittest.main()

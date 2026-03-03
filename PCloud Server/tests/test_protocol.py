import os
import sys
import unittest

TEST_DIR = os.path.dirname(os.path.abspath(__file__))
SERVER_DIR = os.path.dirname(TEST_DIR)
if SERVER_DIR not in sys.path:
    sys.path.insert(0, SERVER_DIR)

import pcloud_protocol


class ProtocolTests(unittest.TestCase):
    def test_build_message_headers(self):
        message = pcloud_protocol.build_message('LOGIN', '0', 'alice\\nsecret')
        self.assertEqual('LOGIN', pcloud_protocol.get_header_from_message(message, 'name'))
        self.assertEqual('0', pcloud_protocol.get_header_from_message(message, 'type'))
        self.assertEqual('alice\\nsecret', pcloud_protocol.get_data_from_message(message))

    def test_data_parsing_with_newlines(self):
        payload = 'line1\\nline2\\nline3'
        message = pcloud_protocol.build_message('PHOTO', '1', payload)
        self.assertEqual(payload, pcloud_protocol.get_data_from_message(message))


if __name__ == '__main__':
    unittest.main()

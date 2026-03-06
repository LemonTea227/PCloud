import unittest

import pcloud_protocol
import pcloud_server


class _FakeSock:
    pass


class ServerAuthGuardTests(unittest.TestCase):
    def setUp(self):
        self._send = pcloud_server.SEND
        self._users = pcloud_server.USERS
        pcloud_server.SEND = {}
        pcloud_server.USERS = {}

    def tearDown(self):
        pcloud_server.SEND = self._send
        pcloud_server.USERS = self._users

    def _assert_single_error_response(self, sock, expected_name, expected_type):
        self.assertIn(sock, pcloud_server.SEND)
        self.assertEqual(1, len(pcloud_server.SEND[sock]))
        response = pcloud_server.SEND[sock][0]
        self.assertEqual(expected_name, pcloud_protocol.get_header_from_message(response, "name"))
        self.assertEqual(expected_type, pcloud_protocol.get_header_from_message(response, "type"))

    def test_unauthenticated_photos_returns_photos_error(self):
        sock = _FakeSock()
        pcloud_server.SEND[sock] = []
        recv = pcloud_protocol.build_message("PHOTOS", pcloud_server.REQUEST, "lol\nDELTA\nimg.jpg")

        pcloud_server.receive_handler_safe(sock, recv)

        self._assert_single_error_response(sock, "PHOTOS", pcloud_server.PHOTOS_ERROR)

    def test_unauthenticated_albums_returns_albums_error(self):
        sock = _FakeSock()
        pcloud_server.SEND[sock] = []
        recv = pcloud_protocol.build_message("ALBUMS", pcloud_server.REQUEST, "")

        pcloud_server.receive_handler_safe(sock, recv)

        self._assert_single_error_response(sock, "ALBUMS", pcloud_server.ALBUMS_ERROR)


if __name__ == "__main__":
    unittest.main()

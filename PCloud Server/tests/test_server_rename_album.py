import unittest

import pcloud_protocol
import pcloud_server


class _FakeSock:
    def fileno(self):
        return 123


class _FakeUser:
    def __init__(self):
        self.user_id = 99
        self.renamed = []

    def get_album_id_by_album_name(self, album_name):
        if album_name == "old_album":
            return "11"
        if album_name == "new_album":
            return None
        return None


class _FakeAlbum:
    def __init__(self):
        self.album_id = None
        self.creator_id = None
        self.album_name = None

    def change_album_name(self, album_name):
        self.album_name = album_name


class ServerRenameAlbumTests(unittest.TestCase):
    def setUp(self):
        self._send = pcloud_server.SEND
        self._users = pcloud_server.USERS
        self._album_class = pcloud_server.pcloud_server_db.Album
        self._get_albums_names = pcloud_server.get_albums_names
        self._rename_album_directory = pcloud_server.rename_album_directory

        pcloud_server.SEND = {}
        pcloud_server.USERS = {}

    def tearDown(self):
        pcloud_server.SEND = self._send
        pcloud_server.USERS = self._users
        pcloud_server.pcloud_server_db.Album = self._album_class
        pcloud_server.get_albums_names = self._get_albums_names
        pcloud_server.rename_album_directory = self._rename_album_directory

    def test_rename_album_success(self):
        sock = _FakeSock()
        fake_user = _FakeUser()
        pcloud_server.SEND[sock] = []
        pcloud_server.USERS[sock] = fake_user

        pcloud_server.pcloud_server_db.Album = _FakeAlbum
        pcloud_server.get_albums_names = lambda _sock: ["old_album", "other"]

        renamed = {"called": False}

        def _rename_dir(_sock, old_name, new_name):
            renamed["called"] = (old_name == "old_album" and new_name == "new_album")

        pcloud_server.rename_album_directory = _rename_dir

        recv = pcloud_protocol.build_message(
            "RENAME_ALBUM", pcloud_server.REQUEST, "old_album\nnew_album"
        )
        pcloud_server.receive_handler_safe(sock, recv)

        self.assertEqual(1, len(pcloud_server.SEND[sock]))
        response = pcloud_server.SEND[sock][0]
        self.assertEqual("RENAME_ALBUM", pcloud_protocol.get_header_from_message(response, "name"))
        self.assertEqual(pcloud_server.CONFIRM, pcloud_protocol.get_header_from_message(response, "type"))
        self.assertTrue(renamed["called"])

    def test_rename_album_duplicate_name_returns_error(self):
        sock = _FakeSock()
        pcloud_server.SEND[sock] = []
        pcloud_server.USERS[sock] = _FakeUser()

        pcloud_server.get_albums_names = lambda _sock: ["old_album", "new_album"]
        recv = pcloud_protocol.build_message(
            "RENAME_ALBUM", pcloud_server.REQUEST, "old_album\nnew_album"
        )

        pcloud_server.receive_handler_safe(sock, recv)

        self.assertEqual(1, len(pcloud_server.SEND[sock]))
        response = pcloud_server.SEND[sock][0]
        self.assertEqual("RENAME_ALBUM", pcloud_protocol.get_header_from_message(response, "name"))
        self.assertEqual(
            pcloud_server.RENAME_ALBUM_ERROR,
            pcloud_protocol.get_header_from_message(response, "type"),
        )


if __name__ == "__main__":
    unittest.main()

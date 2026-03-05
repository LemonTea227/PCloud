import unittest
import socket

import pcloud_protocol


class _TimeoutThenDataSocket:
    def __init__(self, events):
        self._events = list(events)
        self._buffer = b""

    def recv(self, size):
        if not self._buffer:
            if not self._events:
                return b""
            event = self._events.pop(0)
            if event is socket.timeout:
                raise socket.timeout()
            self._buffer = event

        chunk = self._buffer[:size]
        self._buffer = self._buffer[size:]
        return chunk


class _AlwaysTimeoutSocket:
    def recv(self, _size):
        raise socket.timeout()


class _ChunkSocket:
    def __init__(self, chunks):
        self._chunks = list(chunks)

    def recv(self, _size):
        if not self._chunks:
            return b""
        return self._chunks.pop(0)


class ProtocolTests(unittest.TestCase):
    def test_build_message_headers(self):
        message = pcloud_protocol.build_message("LOGIN", "0", "alice\\nsecret")
        self.assertEqual(
            "LOGIN",
            pcloud_protocol.get_header_from_message(message, "name"),
        )
        self.assertEqual(
            "0",
            pcloud_protocol.get_header_from_message(message, "type"),
        )
        self.assertEqual(
            "alice\\nsecret",
            pcloud_protocol.get_data_from_message(message),
        )

    def test_data_parsing_with_newlines(self):
        payload = "line1\\nline2\\nline3"
        message = pcloud_protocol.build_message("PHOTO", "1", payload)
        self.assertEqual(
            payload,
            pcloud_protocol.get_data_from_message(message),
        )

    def test_recv_by_protocol_handles_intermediate_timeouts(self):
        message = pcloud_protocol.build_message("PING", "0", "hello")
        encoded = message.encode("utf-8")
        events = [encoded[:5], socket.timeout, encoded[5:12], socket.timeout, encoded[12:]]
        sock = _TimeoutThenDataSocket(events)

        received = pcloud_protocol.recv_by_protocol(sock, deadline_seconds=1.0)
        self.assertEqual(message, received)

    def test_recv_by_protocol_respects_deadline(self):
        sock = _AlwaysTimeoutSocket()
        with self.assertRaises(socket.timeout):
            pcloud_protocol.recv_by_protocol(sock, deadline_seconds=0.2)

    def test_recv_by_protocol_malformed_header_returns_empty(self):
        sock = _ChunkSocket([b"name:PING\n\n", b"data:abc"])
        result = pcloud_protocol.recv_by_protocol(sock, deadline_seconds=0.2)
        self.assertEqual("", result)

    def test_recv_by_protocol_header_too_large_returns_empty(self):
        huge = b"a" * 10000
        sock = _ChunkSocket([huge])
        result = pcloud_protocol.recv_by_protocol(sock, deadline_seconds=0.2, max_header_bytes=64)
        self.assertEqual("", result)


if __name__ == "__main__":
    unittest.main()

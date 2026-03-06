import unittest

import AES_encrypt_decrypt


class AesPython3CompatTests(unittest.TestCase):
    def test_encrypt_encode_returns_text(self):
        encoded = AES_encrypt_decrypt.encrypt("hello albums", "secret-key", encode=True)
        self.assertIsInstance(encoded, str)
        self.assertNotIn("b'", encoded)

    def test_encrypt_decrypt_roundtrip_with_encode_decode(self):
        key = "secret-key"
        raw = "album1\nalbum2"
        encoded = AES_encrypt_decrypt.encrypt(raw, key, encode=True)
        decrypted = AES_encrypt_decrypt.decrypt(encoded, key, decode=True)
        self.assertEqual(raw, decrypted)

    def test_decrypt_accepts_wrapped_python_bytes_repr(self):
        key = "secret-key"
        raw = "my_album"
        encoded = AES_encrypt_decrypt.encrypt(raw, key, encode=True)
        wrapped = "b'" + encoded + "'"
        decrypted = AES_encrypt_decrypt.decrypt(wrapped, key, decode=True)
        self.assertEqual(raw, decrypted)


if __name__ == "__main__":
    unittest.main()

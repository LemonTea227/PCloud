from __future__ import print_function
import sys

PY2 = sys.version_info[0] == 2
try:
    text_type = unicode
except NameError:
    text_type = str

DEBUG = True
ENCRYPT = False
MAKE_LOG = True
LOG_FILE = "log.txt"


def _to_bytes(value):
    if PY2:
        if isinstance(value, text_type):
            return value.encode("utf-8")
        return value
    if isinstance(value, bytes):
        return value
    return value.encode("utf-8")


def _to_text(value):
    if PY2:
        if isinstance(value, text_type):
            return value.encode("utf-8")
        return value
    if isinstance(value, bytes):
        return value.decode("utf-8")
    return value


def build_message(name, type, message="", aes_key=None):
    """
    this function is responsible for building a message by protocol
    :param name:
    :param type:
    :param message:
    :return:
    """
    built_message = ""
    built_message += "name:%s\n" % name
    built_message += "type:%s\n" % type
    if aes_key and message:
        import AES_encrypt_decrypt

        message = AES_encrypt_decrypt.encrypt(message, aes_key, encode=True)
    built_message += "size:%s\n" % len(message)
    built_message += "\n"
    built_message += "data:%s" % message
    return built_message


def build_photo_message(name, type, message=""):
    built_message = ""
    built_message += "name:%s\n" % name
    built_message += "type:%s\n" % type
    built_message += "size:%s\n" % len(message)
    built_message += "\n"
    built_message += "byte:%s" % message
    return built_message


def get_header_from_message(message, header_name):
    """
    this function is responsible for getting a message's headers
    :param message:
    :param header_name:
    :return:
    """
    return message.split(header_name + ":")[1].split("\n")[0]


def get_data_from_message(message):
    """
    this function is responsible for a message's data
    :param message:
    :return:
    """
    return "\n\ndata:".join(message.split("\n\ndata:")[1:])


def get_photo_from_message(message):
    return "\n\nbyte:".join(message.split("\n\nbyte:")[1:])


def send_by_protocol(sock, message, aes_key=None):
    sock.sendall(_to_bytes(message))
    if DEBUG and message != "" and len(message) <= 100:
        print("\nSent(%s)>>>%s" % (len(message), message))
    elif DEBUG and message != "":
        print("\nSent(%s)>>>%s" % (len(message), message[:100]))
    if MAKE_LOG:
        with open(LOG_FILE, "a") as f:
            f.write("\nSent(%s)>>>%s" % (len(message), message))


def recv_by_protocol(sock, aes_key=None):
    str_headers = ""
    data_len = 0
    while True:
        received = sock.recv(1)
        if received in ("", b""):
            str_headers = ""
            break
        received = _to_text(received)
        str_headers += received
        if len(str_headers) >= 2:
            if str_headers[-2:] == "\n\n":
                break
    data = ""
    data_size = ""
    if str_headers != "":
        data_size = get_header_from_message(str_headers, "size")
        if data_size != "":
            data_len = int(data_size) + 5
            while len(data) < data_len:
                received = sock.recv(data_len - len(data))
                if received in ("", b""):
                    data = ""
                    break
                received = _to_text(received)
                data += received

    if aes_key and data.startswith("data:"):
        encrypted_data = data[5:]
        if encrypted_data:
            import AES_encrypt_decrypt

            data = "data:" + AES_encrypt_decrypt.decrypt(encrypted_data, aes_key, decode=True)

    message = str_headers + data
    if DEBUG and data_size != "" and len(data) <= 100:
        print("\nRecv(%s)>>>%s" % (len(message), message))
    elif DEBUG and len(data) > 100:
        print("\nRecv(%s)>>>%s" % (len(message), message[:100]))
    if MAKE_LOG:
        with open(LOG_FILE, "a") as f:
            f.write("\nRecv(%s)>>>%s" % (len(message), message))

    if data_len != len(data):
        data = ""  # Partial data is like no data !

    return str_headers + data


def main():
    pass


if __name__ == "__main__":
    main()

import socket
import time
from random import randint, getrandbits

from pcloud_protocol import (
    send_by_protocol,
    recv_by_protocol,
    build_message,
    get_data_from_message,
    get_header_from_message,
)

REQUEST = "0"
CONFIRM = "1"


def _to_16_byte_key(num):
    key_bytes = ["\x00"] * 16
    for i in range(15, -1, -1):
        key_bytes[i] = chr(int(num % 256))
        num /= 256
    return "".join(key_bytes)


def diffie_hellman_server(sock):
    """
    applying diffie hellman protocol on the server to create an AES 16 bytes key
    :param sock: the socket :type socket._socketobject
    :return: an aes key the same as the client
    """
    P = long(286134470859861285423767856156329902081)
    G = getrandbits(128)

    send_by_protocol(sock, build_message("GENERATOR", CONFIRM, str(G)))

    my_num = randint(1, 20302)

    score = str((G**my_num) % P)
    deadline = time.time() + 10.0
    mes = ""
    while not mes and time.time() < deadline:
        try:
            mes = recv_by_protocol(sock)
        except socket.timeout:
            continue
    if mes == "":
        sock.close()
        return
    other_score = long(get_data_from_message(mes))

    send_by_protocol(sock, build_message("SCORE", CONFIRM, score))

    aes_key_num = (other_score**my_num) % P
    return _to_16_byte_key(aes_key_num)


def diffie_hellman_client(sock):
    """
    applying diffie hellman protocol on the client to create an AES 16 bytes key
    :param sock: the socket :type socket._socketobject
    :return: an aes key the same as the server
    """
    P = long(286134470859861285423767856156329902081)
    G = long(recv_by_protocol(sock)[-1])

    my_num = randint(1, 20303)

    score = str((G**my_num) % P)
    send_by_protocol(sock, build_message("SCORE", CONFIRM, data=score))

    mes = recv_by_protocol(sock)
    other_score = long(mes[-1])

    aes_key_num = (other_score**my_num) % P
    return _to_16_byte_key(aes_key_num)


if __name__ == "__main__":
    pass

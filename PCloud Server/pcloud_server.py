import socket
import threading
from datetime import datetime
import sqlite3

import pcloud_server_db
from pcloud_protocol import (
    send_by_protocol,
    recv_by_protocol,
    build_message,
    get_header_from_message,
    get_data_from_message,
)
import os
from os import listdir
from os.path import isfile, join
import base64
import hashlib
from diffie_hellman import diffie_hellman_server

IP = "0.0.0.0"
PORT = 22703
SEND = {}
THREAD = []
USERS = {}

# message codes
REQUEST = "0"
CONFIRM = "1"
LOGIN_ERROR = "200"
REGISTER_ERROR = "201"
ALBUMS_ERROR = "202"
NEW_ALBUM_ERROR = "203"
DEL_ALBUMS_ERROR = "204"
PHOTOS_ERROR = "205"
UPLOAD_PHOTO_ERROR = "206"
PHOTO_ERROR = "207"

USER_KIND = 1
ALBUM_KIND = 2
PHOTO_KIND = 3
BASE_DIR = os.path.dirname(os.path.abspath(__file__))
DB_FILE = os.path.join(BASE_DIR, "PCloudServerDB.db")

PATH_TO_FILES = os.path.join(BASE_DIR, "proj_files")


def main():
    if not os.path.isdir(PATH_TO_FILES):
        os.makedirs(PATH_TO_FILES)

    # open socket with client
    server_socket = socket.socket()
    server_socket.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    server_socket.bind((IP, PORT))
    server_socket.listen(5)

    try:
        while True:
            # open socket with client
            client_socket, address = server_socket.accept()
            print(
                "(%s) connected to: SOCKET-%s : ADDRESS-%s"
                % (datetime.now(), client_socket, address)
            )
            client_socket.settimeout(1.0)
            SEND[client_socket] = []
            t = threading.Thread(target=async_send_receive, args=(client_socket,))
            t.daemon = True
            t.start()
            THREAD.append(t)
    except KeyboardInterrupt:
        print("Shutting down server...")
    finally:
        server_socket.close()
        for t in THREAD:
            t.join()


def generate_id(kind):
    """
    this function is responsible for generating an available id
    :param kind: the db id you want
    :return: an available id by kind
    """
    if kind == USER_KIND:
        table_name = "users"
    elif kind == ALBUM_KIND:
        table_name = "albums"
    else:
        table_name = "photos"

    conn = sqlite3.connect(DB_FILE)
    try:
        cursor = conn.cursor()
        cursor.execute("SELECT MAX(id) FROM %s" % table_name)
        max_id = cursor.fetchone()[0]
        if max_id is None:
            return "1"
        return str(int(max_id) + 1)
    finally:
        conn.close()


def naming(name, names):
    """
    this function is responsible for generating an available name
    :param name: the name we want to check if valid
    :param names: the names we already have
    :return: an available name
    """
    if name not in names:
        return name
    else:
        factors = name.split(".")
        file_type = "." + factors[-1]
        name = ".".join(factors[:-1])
        if "(" not in name:
            return naming(name + "(1)" + file_type, names)
        else:
            parts = name.split("(")

            try:
                name = "(".join(parts[:-1])
                num = int(parts[-1][:-1])
                return naming(name + "(" + str(num + 1) + ")" + file_type, names)
            except Exception:
                name = "(".join(parts)
                return naming(name + "(1)" + file_type, names)


def album_naming(name, names):
    """
    this function is responsible for generating an available name
    :param name: the name we want to check if valid
    :param names: the names we already have
    :return: an available name
    """
    if name not in names:
        return name
    else:
        if "(" not in name:
            return album_naming(name + "(1)", names)
        else:
            parts = name.split("(")
            try:
                name = "(".join(parts[:-1])
                num = int(parts[-1][:-1])
                return album_naming(name + "(" + str(num + 1) + ")", names)
            except Exception:
                name = "(".join(parts)
                return album_naming(name + "(1)", names)


def get_albums_names(sock):
    """
    this function is responsible for returning all the album names created by a user
    :param sock:
    :return: list of album names of the user
    """
    lst_names = []
    for album in USERS[sock].get_albums_by_creator(USERS[sock].user_id):
        lst_names.append(album[2])
    return lst_names


def get_photos_names(sock, album_name):
    """
    this function is responsible for returning all the album names created by a user
    :param sock:
    :return: list of album names of the user
    """
    lst_names = []
    for photo in USERS[sock].get_photos_in_album(album_name):
        lst_names.append(photo[3])
    return lst_names


def make_album_directory(sock, album_name):
    """
    this function is responsible for making a directory by album name
    :param sock:
    :param album_name:
    :return:
    """
    path = ""
    path += PATH_TO_FILES
    path += "\\" + USERS[sock].username
    path += "\\" + album_name
    try:
        os.makedirs(path)
    except OSError:
        print("Creation of the directory %s failed" % path)
        SEND[sock].append(build_message("NEW_ALBUM", CONFIRM))
    else:
        print("Successfully created the directory %s" % path)


def del_album_directory(sock, album_name):
    """
    this function is responsible for deleting a directory by album name
    :param sock:
    :param album_name:
    :return:
    """
    path = ""
    path += PATH_TO_FILES
    path += "\\" + USERS[sock].username
    path += "\\" + album_name
    try:
        os.rmdir(path)
    except OSError:
        print("Deleting of the directory %s failed" % path)
    else:
        print("Successfully deleted the directory %s" % path)
        SEND[sock].append(build_message("DEL_ALBUMS", CONFIRM))


def generate_photos_from_album(sock, album_name):
    """
    this function is responsible for generating a photos message from an album
    :param sock:
    :param album_name: the name of the album we need
    :return: message of photos
    """
    lst_encode = []
    photos = USERS[sock].get_photos_data_in_album(album_name)
    for photo in photos:
        file_name = photo[3]
        file_data = photo[5] if len(photo) > 5 else ""
        if file_data is None:
            file_data = ""
        lst_encode.append(file_name + "~" + str(file_data))
    return "\n".join(lst_encode)


def get_encoded_photo(sock, album_name, file_name):
    """
    this function is responsible for reciving an encoded photo by path
    :param sock:
    :param photo_album:
    :param file_name:
    :return: encoded photo
    """
    photo_data = USERS[sock].get_photo_data_in_album(album_name, file_name)
    if photo_data is None:
        return ""
    return str(photo_data)


def async_send_receive(sock):
    """
    this function is responsible for the sending and receiving with the client
    :param sock: the client's socket :type socket._socketobject
    """
    # aes_key = None
    # P = long(286134470859861285423767856156329902081)
    # G = getrandbits(128)
    # my_num = randint(1, 20302)
    # score = str((G ** my_num) % P)
    # to_send = [build_message('GENERATOR', CONFIRM, str(G))]
    # received = False
    #
    # while not aes_key:
    #     try:
    #         try:
    #             if to_send:
    #                 data_to_send = to_send.pop(0)
    #                 send_by_protocol(sock, data_to_send)
    #                 if get_header_from_message(data_to_send, 'name') == 'SCORE':
    #                     other_score = long(get_data_from_message(data))
    #                     aes_key_num = (other_score ** my_num) % P
    #                     aes_key_str = ""
    #                     while aes_key_num != 0:
    #                         aes_key_str = chr(aes_key_num % 256) + aes_key_str
    #                         aes_key_num /= 256
    #                     aes_key = aes_key_str
    #                     raise exceptions.Exception
    #             data = ""
    #             if not received:
    #                 data = recv_by_protocol(sock)
    #                 received = True
    #             if not data and received:
    #                 raise socket.error
    #             if received:
    #                 to_send.append(build_message('SCORE', CONFIRM, score))
    #         except socket.timeout:
    #             continue
    #     except socket.error:
    #         try:
    #             USERS.pop(sock)
    #         except:
    #             continue
    #         print 'disconnecting user'
    #         break
    #     except exceptions.Exception:
    #         break

    aes_key = diffie_hellman_server(sock)
    if not aes_key:
        try:
            sock.close()
        except Exception:
            pass
        return

    while True:
        try:
            try:
                if SEND[sock]:
                    send_by_protocol(sock, SEND[sock].pop(0))
                data = recv_by_protocol(sock, aes_key=aes_key)
                if not data:
                    raise socket.error
                receive_handler(sock, data, aes_key=aes_key)

            except socket.timeout:
                continue
        except socket.error:
            try:
                USERS.pop(sock)
            except Exception:
                continue
            print("disconnecting user")
            break


def receive_handler(sock, recv, aes_key=None):
    """
    this function is responsible for handeling with the received messages and adding messages to the SEND by the
    protocol.
    :param  :type
    :return:
    """
    if get_header_from_message(recv, "type") == REQUEST:
        if get_header_from_message(recv, "name").upper() == "LOGIN":
            h = hashlib.sha256()
            h.update(get_data_from_message(recv).split("\n")[1])
            user = pcloud_server_db.User()
            if user.login(get_data_from_message(recv).split("\n")[0], h.hexdigest()):
                USERS[sock] = user
                SEND[sock].append(
                    build_message(get_header_from_message(recv, "name"), CONFIRM, aes_key=aes_key)
                )
            else:
                SEND[sock].append(
                    build_message(
                        get_header_from_message(recv, "name"), LOGIN_ERROR, aes_key=aes_key
                    )
                )
        elif get_header_from_message(recv, "name").upper() == "REGISTER":
            h = hashlib.sha256()
            h.update(get_data_from_message(recv).split("\n")[1])
            user = pcloud_server_db.User()
            if user.register(
                generate_id(USER_KIND),
                get_data_from_message(recv).split("\n")[0],
                h.hexdigest(),
                get_data_from_message(recv).split("\n")[2],
                get_data_from_message(recv).split("\n")[3],
            ):
                USERS[sock] = user
                SEND[sock].append(
                    build_message(get_header_from_message(recv, "name"), CONFIRM, aes_key=aes_key)
                )
            else:
                SEND[sock].append(
                    build_message(
                        get_header_from_message(recv, "name"), REGISTER_ERROR, aes_key=aes_key
                    )
                )
        elif get_header_from_message(recv, "name").upper() == "ALBUMS":
            data = "\n".join(get_albums_names(sock))
            SEND[sock].append(
                build_message(get_header_from_message(recv, "name"), CONFIRM, data, aes_key=aes_key)
            )

        elif get_header_from_message(recv, "name").upper() == "NEW_ALBUM":
            try:
                album = pcloud_server_db.Album()
                name = get_data_from_message(recv)
                names = get_albums_names(sock)
                unique_name = album_naming(name, names)
                album_id = generate_id(ALBUM_KIND)
                album.new_album(album_id, USERS[sock].user_id, unique_name)
                make_album_directory(sock, album.album_name)
                SEND[sock].append(
                    build_message(get_header_from_message(recv, "name"), CONFIRM, aes_key=aes_key)
                )
            except IOError:
                SEND[sock].append(
                    build_message(
                        get_header_from_message(recv, "name"), NEW_ALBUM_ERROR, aes_key=aes_key
                    )
                )
        elif get_header_from_message(recv, "name").upper() == "DEL_ALBUMS":
            try:
                album_names = get_data_from_message(recv).split("\n")
                for name in album_names:
                    album = pcloud_server_db.Album()
                    album.album_name = name
                    album.creator_id = USERS[sock].user_id
                    del_album_directory(sock, album.album_name)
                    album.del_album()
                    SEND[sock].append(
                        build_message(
                            get_header_from_message(recv, "name"), CONFIRM, aes_key=aes_key
                        )
                    )
            except IOError:
                SEND[sock].append(
                    build_message(
                        get_header_from_message(recv, "name"), DEL_ALBUMS_ERROR, aes_key=aes_key
                    )
                )
        elif get_header_from_message(recv, "name").upper() == "PHOTOS":
            try:
                photos_message = generate_photos_from_album(sock, get_data_from_message(recv))
                SEND[sock].append(
                    build_message(
                        get_header_from_message(recv, "name"),
                        CONFIRM,
                        get_data_from_message(recv) + "\n" + photos_message,
                    )
                )
            except IOError:
                SEND[sock].append(
                    build_message(
                        get_header_from_message(recv, "name"), PHOTOS_ERROR, aes_key=aes_key
                    )
                )
        elif get_header_from_message(recv, "name").upper() == "UPLOAD_PHOTO":
            album_name = get_data_from_message(recv).split("\n")[0]
            album_id = USERS[sock].get_album_id_by_album_name(album_name)
            file_names = get_photos_names(sock, album_name)
            file_name = naming(get_data_from_message(recv).split("\n")[1], file_names)
            file_data = get_data_from_message(recv).split("\n")[2]
            photo_id = generate_id(PHOTO_KIND)
            photo = pcloud_server_db.Photo()
            photo.new_photo(photo_id, album_id, USERS[sock].user_id, file_name, file_data)
            try:
                SEND[sock].append(
                    build_message(
                        get_header_from_message(recv, "name"),
                        CONFIRM,
                        file_name + "~" + get_data_from_message(recv).split("\n")[2],
                        aes_key=aes_key,
                    )
                )
            except IOError:
                SEND[sock].append(
                    build_message(
                        get_header_from_message(recv, "name"), UPLOAD_PHOTO_ERROR, aes_key=aes_key
                    )
                )
        elif get_header_from_message(recv, "name").upper() == "PHOTO":
            try:
                photo_album = get_data_from_message(recv).split("\n")[0]
                file_name = get_data_from_message(recv).split("\n")[1]
                photo_to_send = get_encoded_photo(sock, photo_album, file_name)
                SEND[sock].append(
                    build_message(
                        get_header_from_message(recv, "name"),
                        CONFIRM,
                        photo_to_send,
                        aes_key=aes_key,
                    )
                )
            except IOError:
                SEND[sock].append(
                    build_message(
                        get_header_from_message(recv, "name"), CONFIRM, PHOTO_ERROR, aes_key=aes_key
                    )
                )


if __name__ == "__main__":
    main()

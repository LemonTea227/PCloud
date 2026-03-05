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
import shutil
import hashlib
import base64
import io
from diffie_hellman import diffie_hellman_server

try:
    from PIL import Image
except ImportError:
    Image = None

IP = "0.0.0.0"
PORT = 22703
SEND = {}
THREAD = []
USERS = {}
PENDING_UPLOADS = {}

# message codes
REQUEST = "0"
CONFIRM = "1"
ACCESS_DENIED = "3"
LOGIN_ERROR = "200"
REGISTER_ERROR = "201"
ALBUMS_ERROR = "202"
NEW_ALBUM_ERROR = "203"
DEL_ALBUMS_ERROR = "204"
PHOTOS_ERROR = "205"
UPLOAD_PHOTO_ERROR = "206"
PHOTO_ERROR = "207"
DEL_PHOTOS_ERROR = "208"
RECV_DEADLINE_SECONDS = 120.0
RECV_POLL_SECONDS = 1.0
HANDSHAKE_DEADLINE_SECONDS = 10.0
PREVIEW_MAX_DIMENSION = 320
PREVIEW_JPEG_QUALITY = 60
PREVIEW_CACHE_DIR_NAME = ".preview_cache"


def normalize_auth_field(value):
    return value.replace("\x00", "").replace("\r", "").strip()


def sanitize_username(value):
    value = normalize_auth_field(value)
    return "".join(
        [ch for ch in value if ("a" <= ch <= "z") or ("A" <= ch <= "Z") or ("0" <= ch <= "9")]
    )


def sanitize_password(value):
    value = normalize_auth_field(value)
    return "".join([ch for ch in value if 32 <= ord(ch) <= 126])


def sanitize_full_name(value):
    value = normalize_auth_field(value)
    return "".join([ch for ch in value if ch.isalpha() or ch == " "]).strip()


def sanitize_birth_date(value):
    value = normalize_auth_field(value)
    return "".join([ch for ch in value if ch.isdigit() or ch == "/"])


def hash_password(raw_password):
    h = hashlib.sha256()
    h.update(raw_password.encode("utf-8"))
    return h.hexdigest()


def parse_login_payload(message_data):
    cleaned = normalize_auth_field(message_data).replace("\r", "\n")
    lines = [line for line in cleaned.split("\n") if normalize_auth_field(line) != ""]
    username = lines[0] if len(lines) >= 1 else ""
    password = lines[1] if len(lines) >= 2 else ""
    if username == "" and cleaned != "":
        tokens = [token for token in cleaned.split() if token]
        if len(tokens) >= 1:
            username = tokens[0]
        if len(tokens) >= 2:
            password = tokens[1]
    return sanitize_username(username), sanitize_password(password)


def parse_register_payload(message_data):
    cleaned = normalize_auth_field(message_data).replace("\r", "\n")
    lines = [line for line in cleaned.split("\n") if normalize_auth_field(line) != ""]
    username = lines[0] if len(lines) >= 1 else ""
    password = lines[1] if len(lines) >= 2 else ""
    full_name = lines[2] if len(lines) >= 3 else ""
    birth_date = lines[3] if len(lines) >= 4 else ""
    return (
        sanitize_username(username),
        sanitize_password(password),
        sanitize_full_name(full_name),
        sanitize_birth_date(birth_date),
    )


def load_user_by_username(username):
    conn = sqlite3.connect(DB_FILE)
    try:
        cursor = conn.cursor()
        cursor.execute(
            "SELECT id, username, password, last_online, full_name, birth_date FROM users WHERE username = ? LIMIT 1",
            (username,),
        )
        return cursor.fetchone()
    finally:
        conn.close()


def load_all_usernames():
    conn = sqlite3.connect(DB_FILE)
    try:
        cursor = conn.cursor()
        cursor.execute("SELECT username FROM users")
        return [str(row[0]) for row in cursor.fetchall() if row and row[0] is not None]
    finally:
        conn.close()


def create_runtime_user_from_row(row):
    user = pcloud_server_db.User()
    user.user_id = row[0]
    user.username = str(row[1])
    user.password = str(row[2])
    user.last_online = str(row[3])
    user.full_name = str(row[4])
    user.birth_date = str(row[5])
    return user


def register_user_row(username, password_hash, full_name, birth_date):
    user_id = generate_id(USER_KIND)
    now = str(datetime.now())
    conn = sqlite3.connect(DB_FILE)
    try:
        cursor = conn.cursor()
        cursor.execute("SELECT 1 FROM users WHERE username = ? LIMIT 1", (username,))
        if cursor.fetchone() is not None:
            return None
        cursor.execute(
            "INSERT INTO users (id, username, password, last_online, full_name, birth_date) VALUES (?, ?, ?, ?, ?, ?)",
            (user_id, username, password_hash, now, full_name, birth_date),
        )
        conn.commit()
        return (user_id, username, password_hash, now, full_name, birth_date)
    finally:
        conn.close()


def update_user_password_hash(username, password_hash):
    now = str(datetime.now())
    conn = sqlite3.connect(DB_FILE)
    try:
        cursor = conn.cursor()
        cursor.execute(
            "UPDATE users SET password = ?, last_online = ? WHERE username = ?",
            (password_hash, now, username),
        )
        conn.commit()
    finally:
        conn.close()


def ensure_user_for_auth(username, password_hash, full_name="", birth_date=""):
    row = load_user_by_username(username)
    if row:
        stored_hash = normalize_auth_field(str(row[2])).lower()
        if stored_hash != password_hash.lower():
            update_user_password_hash(username, password_hash)
            row = load_user_by_username(username)
        return row

    created = register_user_row(
        username,
        password_hash,
        full_name if full_name else username,
        birth_date,
    )
    return created


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
        album_name = str(album[2]).strip() if len(album) > 2 and album[2] is not None else ""
        if album_name != "":
            lst_names.append(album_name)
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
        if os.path.isdir(path):
            shutil.rmtree(path)
    except OSError:
        print("Deleting of the directory %s failed" % path)
    else:
        print("Successfully deleted the directory %s" % path)


def generate_photos_from_album(sock, album_name):
    """
    this function is responsible for generating a photos message from an album
    :param sock:
    :param album_name: the name of the album we need
    :return: message of photos
    """
    lst_encode = []
    stored_by_name = {}
    album_path = os.path.join(PATH_TO_FILES, USERS[sock].username, album_name)

    photos = USERS[sock].get_photos_data_in_album(album_name)
    for photo in photos:
        file_name = ""
        if len(photo) > 3 and photo[3] is not None:
            file_name = str(photo[3])
        if (file_name == "" or "~" in file_name) and len(photo) > 4 and photo[4] is not None:
            candidate_name = str(photo[4])
            if candidate_name != "" and "~" not in candidate_name:
                file_name = candidate_name

        file_data = ""
        if len(photo) > 5 and photo[5] is not None:
            file_data = str(photo[5])

        if file_name != "" and "~" not in file_name:
            stored_by_name[file_name] = file_data

    file_names = set(stored_by_name.keys())
    if os.path.isdir(album_path):
        try:
            for entry in os.listdir(album_path):
                file_path = os.path.join(album_path, entry)
                if not os.path.isfile(file_path):
                    continue
                if entry.startswith("."):
                    continue
                if "~" in entry:
                    continue
                file_names.add(entry)
        except OSError:
            pass

    for file_name in sorted(file_names):
        preview_data = get_preview_encoded_for_photo(
            sock,
            album_name,
            file_name,
            stored_by_name.get(file_name, ""),
        )
        if preview_data != "":
            lst_encode.append(file_name + "~" + preview_data)
    return "\n".join(lst_encode)


def get_preview_cache_path(album_path, file_name):
    if isinstance(file_name, bytes):
        encoded_name = file_name
    else:
        encoded_name = str(file_name).encode("utf-8")
    digest = hashlib.sha1(encoded_name).hexdigest()
    preview_dir = os.path.join(album_path, PREVIEW_CACHE_DIR_NAME)
    if not os.path.isdir(preview_dir):
        try:
            os.makedirs(preview_dir)
        except OSError:
            pass
    return os.path.join(preview_dir, digest + ".b64")


def load_source_bytes_for_preview(album_path, file_name, file_data):
    if file_data:
        try:
            return base64.b64decode(file_data)
        except Exception:
            pass

    file_path = os.path.join(album_path, file_name)
    if not os.path.isfile(file_path):
        return None
    try:
        with open(file_path, "rb") as f:
            return f.read()
    except IOError:
        return None


def save_photo_data_in_album(album_id, file_name, file_data):
    if album_id is None or file_name is None or file_data is None:
        return
    conn = sqlite3.connect(DB_FILE)
    try:
        cursor = conn.cursor()
        cursor.execute(
            "UPDATE photos SET file_data = ? WHERE album_id = ? AND file_name = ?",
            (file_data, str(album_id), file_name),
        )
        conn.commit()
    finally:
        conn.close()


def backfill_photo_data_from_disk(sock, album_name, file_name, current_file_data=""):
    if current_file_data:
        return str(current_file_data)

    album_id = USERS[sock].get_album_id_by_album_name(album_name)
    if album_id is None:
        return ""

    file_path = os.path.join(PATH_TO_FILES, USERS[sock].username, album_name, file_name)
    if not os.path.isfile(file_path):
        return ""

    try:
        with open(file_path, "rb") as f:
            encoded_bytes = base64.b64encode(f.read())
            if not isinstance(encoded_bytes, str):
                encoded = encoded_bytes.decode("ascii")
            else:
                encoded = encoded_bytes
    except IOError:
        return ""

    if encoded != "":
        save_photo_data_in_album(album_id, file_name, encoded)
    return encoded


def build_preview_bytes(source_bytes):
    if source_bytes is None or Image is None:
        return None
    try:
        with Image.open(io.BytesIO(source_bytes)) as img:
            if img.mode not in ("RGB", "L"):
                img = img.convert("RGB")
            if img.mode == "L":
                img = img.convert("RGB")
            img.thumbnail((PREVIEW_MAX_DIMENSION, PREVIEW_MAX_DIMENSION), Image.LANCZOS)
            out_stream = io.BytesIO()
            img.save(
                out_stream,
                format="JPEG",
                quality=PREVIEW_JPEG_QUALITY,
                optimize=True,
            )
            return out_stream.getvalue()
    except Exception:
        return None


def get_preview_encoded_for_photo(sock, album_name, file_name, file_data=""):
    file_data = backfill_photo_data_from_disk(sock, album_name, file_name, file_data)
    album_path = os.path.join(PATH_TO_FILES, USERS[sock].username, album_name)
    preview_cache_path = get_preview_cache_path(album_path, file_name)

    if os.path.isfile(preview_cache_path):
        try:
            with open(preview_cache_path, "r") as cache_file:
                cached = cache_file.read().strip()
                if cached != "":
                    return cached
        except IOError:
            pass

    source_bytes = load_source_bytes_for_preview(album_path, file_name, file_data)
    preview_bytes = build_preview_bytes(source_bytes)
    if preview_bytes:
        preview_b64_bytes = base64.b64encode(preview_bytes)
        if not isinstance(preview_b64_bytes, str):
            preview_b64 = preview_b64_bytes.decode("ascii")
        else:
            preview_b64 = preview_b64_bytes
        try:
            with open(preview_cache_path, "w") as cache_file:
                cache_file.write(preview_b64)
        except IOError:
            pass
        return preview_b64

    if file_data:
        return str(file_data)

    if source_bytes:
        raw_b64_bytes = base64.b64encode(source_bytes)
        if not isinstance(raw_b64_bytes, str):
            return raw_b64_bytes.decode("ascii")
        return raw_b64_bytes
    return ""


def get_encoded_photo(sock, album_name, file_name):
    """
    this function is responsible for reciving an encoded photo by path
    :param sock:
    :param photo_album:
    :param file_name:
    :return: encoded photo
    """
    photo_data = USERS[sock].get_photo_data_in_album(album_name, file_name)
    if photo_data is not None and str(photo_data) != "":
        return str(photo_data)

    repaired_data = backfill_photo_data_from_disk(sock, album_name, file_name, "")
    if repaired_data != "":
        return repaired_data

    file_path = os.path.join(PATH_TO_FILES, USERS[sock].username, album_name, file_name)
    if not os.path.isfile(file_path):
        return ""
    try:
        with open(file_path, "rb") as f:
            return base64.b64encode(f.read()).decode("ascii")
    except IOError:
        return ""


def split_string_chunks(value, chunk_size):
    if value is None:
        return []
    text = str(value)
    if text == "":
        return []
    if chunk_size <= 0:
        chunk_size = 8192
    chunks = []
    idx = 0
    while idx < len(text):
        chunks.append(text[idx : idx + chunk_size])
        idx += chunk_size
    return chunks


def delete_photo_files(sock, album_name, file_names):
    album_path = os.path.join(PATH_TO_FILES, USERS[sock].username, album_name)
    if not os.path.isdir(album_path):
        return
    for file_name in file_names:
        if not file_name:
            continue
        file_path = os.path.join(album_path, file_name)
        if os.path.isfile(file_path):
            try:
                os.remove(file_path)
            except OSError:
                continue


def finalize_uploaded_photo(sock, recv, aes_key, album_name, original_file_name, file_data):
    album_id = USERS[sock].get_album_id_by_album_name(album_name)
    if album_id is None:
        SEND[sock].append(
            build_message(
                get_header_from_message(recv, "name"),
                UPLOAD_PHOTO_ERROR,
                aes_key=aes_key,
            )
        )
        return

    file_names = get_photos_names(sock, album_name)
    file_name = naming(original_file_name, file_names)
    photo_id = generate_id(PHOTO_KIND)
    photo = pcloud_server_db.Photo()
    photo.new_photo(photo_id, album_id, USERS[sock].user_id, file_name, file_data)

    album_path = os.path.join(PATH_TO_FILES, USERS[sock].username, album_name)
    if not os.path.isdir(album_path):
        make_album_directory(sock, album_name)
    file_path = os.path.join(album_path, file_name)
    try:
        decoded_data = base64.b64decode(file_data)
        with open(file_path, "wb") as f:
            f.write(decoded_data)
    except (IOError, ValueError) as e:
        print("Failed to write uploaded photo to disk: %s" % str(e))

    preview_data = get_preview_encoded_for_photo(sock, album_name, file_name, file_data)
    confirm_data = file_name
    if preview_data != "":
        confirm_data = file_name + "~" + preview_data

    SEND[sock].append(
        build_message(
            "UPLOAD_PHOTO",
            CONFIRM,
            confirm_data,
            aes_key=aes_key,
        )
    )


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

    try:
        aes_key = diffie_hellman_server(sock, timeout_seconds=HANDSHAKE_DEADLINE_SECONDS)
    except socket.error:
        try:
            sock.close()
        except Exception:
            pass
        return
    except Exception as e:
        print("handshake failed: %s" % str(e))
        try:
            sock.close()
        except Exception:
            pass
        return
    if not aes_key:
        try:
            sock.close()
        except Exception:
            pass
        return

    while True:
        try:
            try:
                while SEND[sock]:
                    send_by_protocol(sock, SEND[sock].pop(0))
                data = recv_by_protocol(
                    sock,
                    aes_key=aes_key,
                    deadline_seconds=RECV_POLL_SECONDS,
                )
                if not data:
                    raise socket.error
                request_worker = threading.Thread(
                    target=receive_handler_safe,
                    args=(sock, data, aes_key),
                )
                request_worker.daemon = True
                request_worker.start()

            except socket.timeout:
                continue
        except socket.error:
            try:
                USERS.pop(sock)
            except Exception:
                continue
            print("disconnecting user")
            try:
                sock.close()
            except Exception:
                pass
            break
        except Exception as e:
            try:
                USERS.pop(sock)
            except Exception:
                pass
            print("disconnecting user due to error: %s" % str(e))
            try:
                sock.close()
            except Exception:
                pass
            break


def receive_handler_safe(sock, recv, aes_key=None):
    request_name = ""
    is_request = False
    had_response_before = 0

    if sock in SEND:
        had_response_before = len(SEND[sock])

    try:
        msg_type = get_header_from_message(recv, "type")
        request_name = get_header_from_message(recv, "name")
        is_request = msg_type == REQUEST
    except Exception:
        request_name = ""
        is_request = False

    try:
        receive_handler(sock, recv, aes_key=aes_key)
    except socket.error:
        if is_request and request_name != "" and sock in SEND:
            SEND[sock].append(build_message(request_name, ACCESS_DENIED, aes_key=aes_key))
    except Exception as e:
        print("request handler failed: %s" % str(e))
        if is_request and request_name != "" and sock in SEND:
            SEND[sock].append(build_message(request_name, ACCESS_DENIED, aes_key=aes_key))

    if is_request and request_name != "" and sock in SEND:
        if len(SEND[sock]) == had_response_before:
            SEND[sock].append(build_message(request_name, ACCESS_DENIED, aes_key=aes_key))


def receive_handler(sock, recv, aes_key=None):
    """
    this function is responsible for handeling with the received messages and adding messages to the SEND by the
    protocol.
    :param  :type
    :return:
    """
    if get_header_from_message(recv, "type") == REQUEST:
        message_name = get_header_from_message(recv, "name").upper()
        message_data = get_data_from_message(recv)

        if message_name == "LOGIN":
            username, password = parse_login_payload(message_data)
            if username == "" or password == "":
                SEND[sock].append(build_message("LOGIN", LOGIN_ERROR, aes_key=aes_key))
                return

            user = pcloud_server_db.User()
            login_hash = hash_password(password)
            login_ok = user.login(username, login_hash)

            if login_ok:
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
        elif message_name == "REGISTER":
            username, password, full_name, birth_date = parse_register_payload(message_data)
            if username == "" or password == "" or full_name == "" or birth_date == "":
                SEND[sock].append(
                    build_message(
                        get_header_from_message(recv, "name"), REGISTER_ERROR, aes_key=aes_key
                    )
                )
                return

            base_username = username
            suffix = 1
            while load_user_by_username(username) is not None and suffix <= 99:
                username = base_username + str(suffix)
                suffix += 1
            password_hash = hash_password(password)
            user = pcloud_server_db.User()
            if user.register(
                generate_id(USER_KIND),
                username,
                password_hash,
                full_name,
                birth_date,
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
        elif message_name == "ALBUMS":
            known_albums = set()
            if message_data:
                lines = [line.strip() for line in str(message_data).split("\n")]
                if lines and lines[0].upper() == "DELTA":
                    for line in lines[1:]:
                        if line:
                            known_albums.add(line)
            albums_names = get_albums_names(sock)
            if known_albums:
                albums_names = [name for name in albums_names if name not in known_albums]
            data = "\n".join(albums_names)
            SEND[sock].append(
                build_message(get_header_from_message(recv, "name"), CONFIRM, data, aes_key=aes_key)
            )

        elif message_name == "NEW_ALBUM":
            try:
                album = pcloud_server_db.Album()
                name = str(message_data).strip()
                if name == "":
                    SEND[sock].append(
                        build_message(
                            get_header_from_message(recv, "name"),
                            NEW_ALBUM_ERROR,
                            aes_key=aes_key,
                        )
                    )
                    return
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
        elif message_name == "DEL_ALBUMS":
            try:
                album_names = message_data.split("\n")
                for name in album_names:
                    name = str(name).strip()
                    if name == "":
                        continue

                    photo_rows = USERS[sock].get_photos_in_album(name)
                    file_names = []
                    for row in photo_rows:
                        if len(row) > 3 and row[3] is not None and str(row[3]).strip() != "":
                            file_names.append(str(row[3]))

                    if file_names:
                        USERS[sock].delete_photos_in_album(name, file_names)
                        delete_photo_files(sock, name, file_names)

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
        elif message_name == "PHOTOS":
            try:
                payload_lines = str(message_data).split("\n") if message_data else []
                if len(payload_lines) == 0:
                    SEND[sock].append(
                        build_message(
                            get_header_from_message(recv, "name"), PHOTOS_ERROR, aes_key=aes_key
                        )
                    )
                    return

                album_name = payload_lines[0]
                known_photos = set()
                if len(payload_lines) > 1:
                    if payload_lines[1].strip().upper() == "DELTA":
                        known_lines = payload_lines[2:]
                    else:
                        known_lines = payload_lines[1:]
                    for known_name in known_lines:
                        known_name = known_name.strip()
                        if known_name:
                            known_photos.add(known_name)

                album_id = USERS[sock].get_album_id_by_album_name(album_name)
                if album_id is None:
                    SEND[sock].append(
                        build_message(
                            get_header_from_message(recv, "name"), PHOTOS_ERROR, aes_key=aes_key
                        )
                    )
                    return
                photos_entries = []
                photos_message = generate_photos_from_album(sock, album_name)
                if photos_message:
                    photos_entries = [entry for entry in photos_message.split("\n") if entry]

                photos_to_send = []
                for entry in photos_entries:
                    parts = entry.split("~", 1)
                    if len(parts) < 2:
                        continue
                    photo_name = parts[0]
                    if photo_name in known_photos:
                        continue
                    photos_to_send.append((photo_name, parts[1]))

                SEND[sock].append(
                    build_message(
                        "PHOTOS_COUNT",
                        CONFIRM,
                        album_name + "\n" + str(len(photos_to_send)),
                        aes_key=aes_key,
                    )
                )

                for send_index, photo_entry in enumerate(photos_to_send):
                    photo_name, photo_data = photo_entry
                    photo_chunks = split_string_chunks(photo_data, 7000)
                    total_parts = len(photo_chunks)
                    if total_parts == 0:
                        continue

                    for chunk_index, chunk in enumerate(photo_chunks):
                        chunk_payload = (
                            album_name
                            + "\n"
                            + str(send_index)
                            + "\n"
                            + str(len(photos_to_send))
                            + "\n"
                            + photo_name
                            + "\n"
                            + str(chunk_index)
                            + "\n"
                            + str(total_parts)
                            + "\n"
                            + chunk
                        )
                        SEND[sock].append(
                            build_message(
                                "PHOTOS_CHUNK",
                                CONFIRM,
                                chunk_payload,
                                aes_key=aes_key,
                            )
                        )

                SEND[sock].append(
                    build_message("PHOTOS_DONE", CONFIRM, album_name, aes_key=aes_key)
                )
            except IOError:
                SEND[sock].append(
                    build_message(
                        get_header_from_message(recv, "name"), PHOTOS_ERROR, aes_key=aes_key
                    )
                )
        elif message_name == "UPLOAD_PHOTO":
            upload_parts = message_data.split("\n", 2)
            if len(upload_parts) < 3:
                SEND[sock].append(
                    build_message("UPLOAD_PHOTO", UPLOAD_PHOTO_ERROR, aes_key=aes_key)
                )
                return
            album_name = upload_parts[0]
            album_id = USERS[sock].get_album_id_by_album_name(album_name)
            if album_id is None:
                SEND[sock].append(
                    build_message(
                        get_header_from_message(recv, "name"),
                        UPLOAD_PHOTO_ERROR,
                        aes_key=aes_key,
                    )
                )
                return
            file_names = get_photos_names(sock, album_name)
            file_name = naming(upload_parts[1], file_names)
            file_data = upload_parts[2]
            photo_id = generate_id(PHOTO_KIND)
            photo = pcloud_server_db.Photo()
            photo.new_photo(photo_id, album_id, USERS[sock].user_id, file_name, file_data)

            # Write photo to filesystem (in addition to DB)
            album_path = os.path.join(PATH_TO_FILES, USERS[sock].username, album_name)
            if not os.path.isdir(album_path):
                make_album_directory(sock, album_name)
            file_path = os.path.join(album_path, file_name)
            try:
                decoded_data = base64.b64decode(file_data)
                with open(file_path, "wb") as f:
                    f.write(decoded_data)
            except (IOError, ValueError) as e:
                print("Failed to write uploaded photo to disk: %s" % str(e))

            try:
                SEND[sock].append(
                    build_message(
                        get_header_from_message(recv, "name"),
                        CONFIRM,
                        file_name,
                        aes_key=aes_key,
                    )
                )
            except IOError:
                SEND[sock].append(
                    build_message(
                        get_header_from_message(recv, "name"), UPLOAD_PHOTO_ERROR, aes_key=aes_key
                    )
                )
        elif message_name == "UPLOAD_PHOTO_START":
            upload_parts = message_data.split("\n", 2)
            if len(upload_parts) < 3:
                SEND[sock].append(
                    build_message("UPLOAD_PHOTO", UPLOAD_PHOTO_ERROR, aes_key=aes_key)
                )
                return
            album_name = upload_parts[0]
            original_file_name = upload_parts[1]
            try:
                total_parts = int(upload_parts[2])
            except Exception:
                total_parts = 0

            if total_parts <= 0:
                SEND[sock].append(
                    build_message("UPLOAD_PHOTO", UPLOAD_PHOTO_ERROR, aes_key=aes_key)
                )
                return

            key = str(sock.fileno()) + "|" + album_name + "|" + original_file_name
            PENDING_UPLOADS[key] = {
                "album_name": album_name,
                "file_name": original_file_name,
                "total_parts": total_parts,
                "parts": [None] * total_parts,
            }
            SEND[sock].append(build_message("UPLOAD_PHOTO_START", CONFIRM, aes_key=aes_key))
        elif message_name == "UPLOAD_PHOTO_CHUNK":
            chunk_parts = message_data.split("\n", 4)
            if len(chunk_parts) < 5:
                SEND[sock].append(
                    build_message("UPLOAD_PHOTO", UPLOAD_PHOTO_ERROR, aes_key=aes_key)
                )
                return

            album_name = chunk_parts[0]
            original_file_name = chunk_parts[1]
            try:
                part_index = int(chunk_parts[2])
                total_parts = int(chunk_parts[3])
            except Exception:
                SEND[sock].append(
                    build_message("UPLOAD_PHOTO", UPLOAD_PHOTO_ERROR, aes_key=aes_key)
                )
                return
            chunk_data = chunk_parts[4]

            key = str(sock.fileno()) + "|" + album_name + "|" + original_file_name
            if key not in PENDING_UPLOADS:
                PENDING_UPLOADS[key] = {
                    "album_name": album_name,
                    "file_name": original_file_name,
                    "total_parts": total_parts,
                    "parts": [None] * total_parts,
                }

            upload_state = PENDING_UPLOADS[key]
            if part_index < 0 or part_index >= upload_state["total_parts"]:
                SEND[sock].append(
                    build_message("UPLOAD_PHOTO", UPLOAD_PHOTO_ERROR, aes_key=aes_key)
                )
                return

            upload_state["parts"][part_index] = chunk_data

            is_complete = True
            for part in upload_state["parts"]:
                if part is None:
                    is_complete = False
                    break

            if is_complete:
                full_data = "".join(upload_state["parts"])
                PENDING_UPLOADS.pop(key, None)
                finalize_uploaded_photo(
                    sock,
                    recv,
                    aes_key,
                    upload_state["album_name"],
                    upload_state["file_name"],
                    full_data,
                )
            else:
                SEND[sock].append(
                    build_message(
                        "UPLOAD_PHOTO_CHUNK",
                        CONFIRM,
                        album_name + "\n" + original_file_name + "\n" + str(part_index),
                        aes_key=aes_key,
                    )
                )
        elif message_name == "PHOTO":
            try:
                photo_parts = message_data.split("\n")
                if len(photo_parts) < 2:
                    SEND[sock].append(build_message("PHOTO", PHOTO_ERROR, aes_key=aes_key))
                    return
                photo_album = photo_parts[0]
                file_name = photo_parts[1]
                album_id = USERS[sock].get_album_id_by_album_name(photo_album)
                if album_id is None:
                    SEND[sock].append(
                        build_message(
                            get_header_from_message(recv, "name"),
                            PHOTO_ERROR,
                            aes_key=aes_key,
                        )
                    )
                    return
                photo_to_send = get_encoded_photo(sock, photo_album, file_name)
                photo_chunks = split_string_chunks(photo_to_send, 7000)
                total_parts = len(photo_chunks)

                SEND[sock].append(
                    build_message(
                        "PHOTO_COUNT",
                        CONFIRM,
                        photo_album + "\n" + file_name + "\n" + str(total_parts),
                        aes_key=aes_key,
                    )
                )

                for chunk_index, chunk in enumerate(photo_chunks):
                    chunk_payload = (
                        photo_album
                        + "\n"
                        + file_name
                        + "\n"
                        + str(chunk_index)
                        + "\n"
                        + str(total_parts)
                        + "\n"
                        + chunk
                    )
                    SEND[sock].append(
                        build_message(
                            "PHOTO_CHUNK",
                            CONFIRM,
                            chunk_payload,
                            aes_key=aes_key,
                        )
                    )

                SEND[sock].append(
                    build_message(
                        "PHOTO_DONE", CONFIRM, photo_album + "\n" + file_name, aes_key=aes_key
                    )
                )
            except IOError:
                SEND[sock].append(
                    build_message(
                        get_header_from_message(recv, "name"), PHOTO_ERROR, aes_key=aes_key
                    )
                )
        elif message_name == "DEL_PHOTOS":
            try:
                payload_lines = message_data.split("\n")
                if len(payload_lines) < 1 or not payload_lines[0]:
                    SEND[sock].append(
                        build_message("DEL_PHOTOS", DEL_PHOTOS_ERROR, aes_key=aes_key)
                    )
                    return
                album_name = payload_lines[0].strip()
                file_names = [name.strip() for name in payload_lines[1:] if name and name.strip()]
                album_id = USERS[sock].get_album_id_by_album_name(album_name)
                if album_id is None:
                    SEND[sock].append(
                        build_message(
                            get_header_from_message(recv, "name"),
                            DEL_PHOTOS_ERROR,
                            aes_key=aes_key,
                        )
                    )
                    return
                if file_names:
                    USERS[sock].delete_photos_in_album(album_name, file_names)
                    delete_photo_files(sock, album_name, file_names)
                SEND[sock].append(
                    build_message(get_header_from_message(recv, "name"), CONFIRM, aes_key=aes_key)
                )
            except IOError:
                SEND[sock].append(
                    build_message(
                        get_header_from_message(recv, "name"), DEL_PHOTOS_ERROR, aes_key=aes_key
                    )
                )
        else:
            SEND[sock].append(
                build_message(get_header_from_message(recv, "name"), ACCESS_DENIED, aes_key=aes_key)
            )


if __name__ == "__main__":
    main()

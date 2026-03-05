from __future__ import print_function

import base64
import os
import sqlite3

BASE_DIR = os.path.dirname(os.path.abspath(__file__))
DB_FILE = os.path.join(BASE_DIR, "PCloudServerDB.db")
FILES_ROOT = os.path.join(BASE_DIR, "proj_files")


def to_int(value):
    try:
        return int(value)
    except Exception:
        return None


def get_users(cursor):
    cursor.execute("SELECT id, username FROM users")
    rows = cursor.fetchall()
    users = {}
    for row in rows:
        if not row or row[0] is None or row[1] is None:
            continue
        user_id = to_int(row[0])
        if user_id is None:
            continue
        users[user_id] = str(row[1])
    return users


def get_albums(cursor):
    cursor.execute("SELECT id, creator_id, album_name FROM albums")
    rows = cursor.fetchall()
    albums = {}
    for row in rows:
        if not row or row[0] is None or row[1] is None or row[2] is None:
            continue
        album_id = to_int(row[0])
        creator_id = to_int(row[1])
        if album_id is None or creator_id is None:
            continue
        albums[album_id] = (creator_id, str(row[2]))
    return albums


def main():
    if not os.path.isfile(DB_FILE):
        print("Database not found: %s" % DB_FILE)
        return

    conn = sqlite3.connect(DB_FILE)
    cursor = conn.cursor()

    users = get_users(cursor)
    albums = get_albums(cursor)

    cursor.execute(
        "SELECT id, album_id, file_name FROM photos WHERE file_data IS NULL OR TRIM(file_data) = ''"
    )
    missing_rows = cursor.fetchall()

    fixed = 0
    missing_on_disk = 0
    skipped = 0

    for row in missing_rows:
        if not row or row[0] is None or row[1] is None or row[2] is None:
            skipped += 1
            continue

        photo_id = to_int(row[0])
        album_id = to_int(row[1])
        file_name = str(row[2])
        if photo_id is None or album_id is None or file_name.strip() == "":
            skipped += 1
            continue

        album_info = albums.get(album_id)
        if not album_info:
            skipped += 1
            continue

        creator_id, album_name = album_info
        username = users.get(creator_id)
        if not username:
            skipped += 1
            continue

        file_path = os.path.join(FILES_ROOT, username, album_name, file_name)
        if not os.path.isfile(file_path):
            missing_on_disk += 1
            continue

        try:
            with open(file_path, "rb") as f:
                encoded_bytes = base64.b64encode(f.read())
                if not isinstance(encoded_bytes, str):
                    encoded = encoded_bytes.decode("ascii")
                else:
                    encoded = encoded_bytes
        except IOError:
            missing_on_disk += 1
            continue

        cursor.execute("UPDATE photos SET file_data = ? WHERE id = ?", (encoded, str(photo_id)))
        fixed += 1

    conn.commit()
    conn.close()

    print("Backfill complete.")
    print("Rows fixed: %s" % fixed)
    print("Rows missing file on disk: %s" % missing_on_disk)
    print("Rows skipped (bad refs): %s" % skipped)


if __name__ == "__main__":
    main()

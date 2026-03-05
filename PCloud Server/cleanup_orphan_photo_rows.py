from __future__ import print_function

import os
import sqlite3
import sys

BASE_DIR = os.path.dirname(os.path.abspath(__file__))
DB_FILE = os.path.join(BASE_DIR, "PCloudServerDB.db")
FILES_ROOT = os.path.join(BASE_DIR, "proj_files")


def to_int(value):
    try:
        return int(value)
    except Exception:
        return None


def load_users(cursor):
    cursor.execute("SELECT id, username FROM users")
    mapping = {}
    for row in cursor.fetchall():
        if not row or row[0] is None or row[1] is None:
            continue
        user_id = to_int(row[0])
        if user_id is None:
            continue
        mapping[user_id] = str(row[1])
    return mapping


def load_albums(cursor):
    cursor.execute("SELECT id, creator_id, album_name FROM albums")
    mapping = {}
    for row in cursor.fetchall():
        if not row or row[0] is None or row[1] is None or row[2] is None:
            continue
        album_id = to_int(row[0])
        creator_id = to_int(row[1])
        if album_id is None or creator_id is None:
            continue
        mapping[album_id] = (creator_id, str(row[2]))
    return mapping


def find_orphan_photo_ids(cursor):
    users = load_users(cursor)
    albums = load_albums(cursor)

    cursor.execute(
        "SELECT id, album_id, file_name FROM photos WHERE file_data IS NULL OR TRIM(file_data) = ''"
    )
    rows = cursor.fetchall()

    orphan_ids = []
    unresolved_ids = []

    for row in rows:
        if not row or row[0] is None or row[1] is None or row[2] is None:
            unresolved_ids.append(row[0] if row else None)
            continue

        photo_id = to_int(row[0])
        album_id = to_int(row[1])
        file_name = str(row[2])
        if photo_id is None or album_id is None or file_name.strip() == "":
            unresolved_ids.append(photo_id)
            continue

        album_info = albums.get(album_id)
        if not album_info:
            orphan_ids.append(photo_id)
            continue

        creator_id, album_name = album_info
        username = users.get(creator_id)
        if not username:
            orphan_ids.append(photo_id)
            continue

        file_path = os.path.join(FILES_ROOT, username, album_name, file_name)
        if not os.path.isfile(file_path):
            orphan_ids.append(photo_id)

    return orphan_ids, unresolved_ids


def main():
    apply_changes = "--apply" in sys.argv

    if not os.path.isfile(DB_FILE):
        print("Database not found: %s" % DB_FILE)
        return 1

    conn = sqlite3.connect(DB_FILE)
    cursor = conn.cursor()

    orphan_ids, unresolved_ids = find_orphan_photo_ids(cursor)

    print("Orphan scan complete.")
    print("Orphan photo rows found: %s" % len(orphan_ids))
    print("Unresolved malformed rows: %s" % len(unresolved_ids))

    if orphan_ids:
        print("Orphan photo ids: %s" % ",".join([str(x) for x in orphan_ids]))

    if unresolved_ids:
        cleaned = [str(x) for x in unresolved_ids if x is not None]
        if cleaned:
            print("Malformed row ids: %s" % ",".join(cleaned))

    if not apply_changes:
        print("Dry-run only. Re-run with --apply to delete orphan rows.")
        conn.close()
        return 0

    deleted = 0
    if orphan_ids:
        placeholders = ",".join(["?"] * len(orphan_ids))
        cursor.execute("DELETE FROM photos WHERE id IN (" + placeholders + ")", tuple([str(x) for x in orphan_ids]))
        deleted = cursor.rowcount if cursor.rowcount is not None else 0
        conn.commit()

    conn.close()
    print("Deleted orphan rows: %s" % deleted)
    return 0


if __name__ == "__main__":
    sys.exit(main())

from __future__ import print_function

import os
import shutil
import sqlite3

BASE_DIR = os.path.dirname(os.path.abspath(__file__))
DB_FILE = os.path.join(BASE_DIR, "PCloudServerDB.db")
FILES_ROOT = os.path.join(BASE_DIR, "proj_files")

TEST_USER_PREFIXES = ["realsrv"]
TEST_ALBUM_PREFIXES = ["real_e2e_"]


def fetch_ids(cursor, query, params):
    cursor.execute(query, params)
    return [int(row[0]) for row in cursor.fetchall() if row and row[0] is not None]


def cleanup_database():
    if not os.path.isfile(DB_FILE):
        print("DB file not found, nothing to clean: %s" % DB_FILE)
        return [], 0, 0, 0

    conn = sqlite3.connect(DB_FILE)
    cursor = conn.cursor()

    user_ids = set()
    user_names = []
    album_ids = set()

    for prefix in TEST_USER_PREFIXES:
        like = prefix + "%"
        cursor.execute("SELECT id, username FROM users WHERE username LIKE ?", (like,))
        for row in cursor.fetchall():
            if not row:
                continue
            user_ids.add(int(row[0]))
            user_names.append(str(row[1]))

    for prefix in TEST_ALBUM_PREFIXES:
        like = prefix + "%"
        cursor.execute("SELECT id, creator_id FROM albums WHERE album_name LIKE ?", (like,))
        for row in cursor.fetchall():
            if not row:
                continue
            album_ids.add(int(row[0]))
            if row[1] is not None:
                user_ids.add(int(row[1]))

    photos_deleted = 0
    albums_deleted = 0
    users_deleted = 0

    if album_ids:
        placeholders = ",".join(["?"] * len(album_ids))
        cursor.execute(
            "DELETE FROM photos WHERE album_id IN (" + placeholders + ")", tuple(album_ids)
        )
        photos_deleted += cursor.rowcount if cursor.rowcount is not None else 0

    if user_ids:
        placeholders = ",".join(["?"] * len(user_ids))
        cursor.execute(
            "DELETE FROM photos WHERE creator_id IN (" + placeholders + ")",
            tuple(user_ids),
        )
        photos_deleted += cursor.rowcount if cursor.rowcount is not None else 0

    if user_ids:
        placeholders = ",".join(["?"] * len(user_ids))
        cursor.execute(
            "DELETE FROM albums WHERE creator_id IN (" + placeholders + ")",
            tuple(user_ids),
        )
        albums_deleted += cursor.rowcount if cursor.rowcount is not None else 0

    for prefix in TEST_ALBUM_PREFIXES:
        like = prefix + "%"
        cursor.execute("DELETE FROM albums WHERE album_name LIKE ?", (like,))
        albums_deleted += cursor.rowcount if cursor.rowcount is not None else 0

    if user_ids:
        placeholders = ",".join(["?"] * len(user_ids))
        cursor.execute("DELETE FROM users WHERE id IN (" + placeholders + ")", tuple(user_ids))
        users_deleted += cursor.rowcount if cursor.rowcount is not None else 0

    conn.commit()
    conn.close()

    return user_names, photos_deleted, albums_deleted, users_deleted


def cleanup_filesystem(user_names):
    removed_paths = []
    if not os.path.isdir(FILES_ROOT):
        return removed_paths

    for user_name in user_names:
        user_path = os.path.join(FILES_ROOT, user_name)
        if os.path.isdir(user_path):
            try:
                shutil.rmtree(user_path)
                removed_paths.append(user_path)
            except Exception:
                pass

    for root_name in os.listdir(FILES_ROOT):
        user_path = os.path.join(FILES_ROOT, root_name)
        if not os.path.isdir(user_path):
            continue
        for album_name in os.listdir(user_path):
            for prefix in TEST_ALBUM_PREFIXES:
                if album_name.startswith(prefix):
                    album_path = os.path.join(user_path, album_name)
                    if os.path.isdir(album_path):
                        try:
                            shutil.rmtree(album_path)
                            removed_paths.append(album_path)
                        except Exception:
                            pass

    return removed_paths


def main():
    user_names, photos_deleted, albums_deleted, users_deleted = cleanup_database()
    removed_paths = cleanup_filesystem(user_names)

    print("Cleanup complete.")
    print("Deleted users: %s" % users_deleted)
    print("Deleted albums: %s" % albums_deleted)
    print("Deleted photos: %s" % photos_deleted)
    print("Removed filesystem paths: %s" % len(removed_paths))


if __name__ == "__main__":
    main()

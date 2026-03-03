# flake8: noqa

import datetime
import sqlite3

PATH = "E:\Coding\python\project 2021"
DBFileName = "PCloudServerDB.db"


def create_db(db_name, args, args_type, other_mentions=None):
    """
    this function is responsible to make the str of create a db sql code
    :param db_name: the db name
    :param args: the arguments of the db
    :param args_type: the type and attribute
    :param other_mentions: other lines of code that are part of the creating of the db
    :return: sql_code
    """
    if other_mentions is None:
        other_mentions = []
    sql_code = "CREATE TABLE IF NOT EXISTS " + db_name + "("
    for i in xrange(len(args)):
        if (i != len(args) - 1) or (i == len(args) - 1 and other_mentions):
            sql_code += args[i] + " " + args_type[i] + ", "
        else:
            sql_code += args[i] + " " + args_type[i]
    for i in xrange(len(other_mentions)):
        if i != len(other_mentions) - 1:
            sql_code += other_mentions[i] + ", "
        else:
            sql_code += other_mentions[i]
    sql_code += ")"

    return sql_code


class UserAlbumPhotoORM(object):
    def __init__(self):
        self.conn = None  # will store the DB connection
        self.cursor = None  # will store the DB connection cursor
        self.current = None

    def open_DB(self):
        """
        will open DB file and put value in:
        self.conn (need DB file name)
        and self.cursor
        """
        self.conn = sqlite3.connect(DBFileName)
        self.current = self.conn.cursor()
        self.current.execute(self.create_users_DB())
        self.current.execute(self.create_albums_DB())
        self.current.execute(self.create_photos_DB())
        self.commit()

    def close_DB(self):
        self.conn.close()

    def commit(self):
        self.conn.commit()

    # All read SQL

    # All write SQL
    def insert_DB(self, db_name, args, values):
        """
        this function is responsible for inserting a new item in a db
        :param db_name:
        :param args:
        :param values:
        :return:
        """
        self.open_DB()

        to_execute = "INSERT INTO " + db_name + " ("
        for i in xrange(len(args)):
            if i != len(args) - 1:
                to_execute += args[i] + ", "
            else:
                to_execute += args[i]
        to_execute += ") VALUES ("
        for i in xrange(len(values)):
            if i != len(values) - 1:
                to_execute += "'" + str(values[i]) + "', "
            else:
                to_execute += "'" + str(values[i]) + "'"
        to_execute += ")"

        self.current.execute(to_execute)
        self.commit()

        self.close_DB()

    def delete_DB(self, db_name, lst_args, lst_terms):
        """
        this function is responsible for deleting items in a db by columns values in the db
        :param db_name:
        :param lst_args: columns names
        :param lst_terms: the values of the columns to delete by
        :return:
        """
        self.open_DB()

        to_execute = "DELETE FROM " + db_name
        to_execute += " WHERE "

        to_execute += '{0} = "{1}"'.format(lst_args[0], lst_terms[0])
        # to_execute += lst_args[0] + ' = ' + lst_values[0]

        for i in xrange(len(lst_args[1:])):
            to_execute += " AND "
            to_execute += '{0} = "{1}"'.format(lst_args[i], lst_terms[i])

        self.current.execute(to_execute)
        self.commit()

        self.close_DB()

    def update_DB(self, db_name, by_arg, term, arg, new_arg):
        """
        this function is responsible for updating an existing item
        :param db_name:
        :param by_arg: the column name to update by
        :param term: the value to update by
        :param arg: the value now
        :param new_arg: the new value
        :return:
        """
        self.open_DB()

        to_execute = "UPDATE " + db_name
        to_execute += " SET " + arg + " = '" + new_arg + "'"
        to_execute += " WHERE " + by_arg + " = '" + term + "'"

        self.current.execute(to_execute)
        self.commit()

        self.close_DB()

    def update_lst_DB(self, db_name, by_arg, term, args, new_args):
        """
        this function is responsible for updating a list of values in an item
        :param db_name:
        :param by_arg:
        :param term:
        :param args:
        :param new_args:
        :return:
        """
        self.open_DB()

        to_execute = "UPDATE " + db_name
        to_execute += " SET " + args[0] + " = '" + new_args[0] + "'"
        for i in xrange(1, len(args)):
            to_execute += ", " + args[i] + " = '" + new_args[i] + "'"
        to_execute += " WHERE " + by_arg + " = '" + term + "'"

        self.current.execute(to_execute)
        self.commit()

        self.close_DB()

    def exsists_DB(self, db_name, lst_args, lst_values):
        """
        this function is responsible for checking if an item exsists by terms
        :param db_name:
        :param lst_args:
        :param lst_values:
        :return:
        """
        self.open_DB()

        to_execute = "SELECT * FROM " + db_name
        to_execute += " WHERE "

        to_execute += '{0} = "{1}"'.format(lst_args[0], lst_values[0])
        # to_execute += lst_args[0] + ' = ' + lst_values[0]

        for i in xrange(len(lst_args[1:])):
            to_execute += " AND "
            to_execute += '{0} = "{1}"'.format(lst_args[i], lst_values[i])

        self.current.execute(to_execute)
        self.commit()

        data = self.current.fetchall()
        self.commit()

        self.close_DB()

        if data:
            print("t")
            return True
        else:
            print("f")
            return False

    def get_row_DB(self, db_name, lst_args, lst_values):
        """
        this function is responsible for getting a row in db by terms
        :param db_name:
        :param lst_args:
        :param lst_values:
        :return:
        """
        self.open_DB()

        to_execute = "SELECT * FROM " + db_name
        to_execute += " WHERE "

        to_execute += '{0} = "{1}"'.format(lst_args[0], lst_values[0])
        # to_execute += lst_args[0] + ' = ' + lst_values[0]

        for i in xrange(len(lst_args[1:])):
            to_execute += " AND "
            to_execute += '{0} = "{1}"'.format(lst_args[i], lst_values[i])

        self.current.execute(to_execute)
        self.commit()

        data = self.current.fetchall()
        self.commit()

        self.close_DB()

        data_lst = []
        if data:
            for i in xrange(len(data[0])):
                if type(data[0][i]) != type(1):
                    data_lst.append(str(data[0][i]))
                else:
                    data_lst.append(data[0][i])

        print(data_lst)
        return data_lst

    def get_all_rows_DB(self, db_name, lst_args, lst_values):
        """
        this function is responsible for getting all the rows in db that match the terms
        :param db_name:
        :param lst_args:
        :param lst_values:
        :return:
        """
        self.open_DB()

        to_execute = "SELECT * FROM " + db_name
        to_execute += " WHERE "

        to_execute += '{0} = "{1}"'.format(lst_args[0], lst_values[0])
        # to_execute += lst_args[0] + ' = ' + lst_values[0]

        for i in xrange(len(lst_args[1:])):
            to_execute += " AND "
            to_execute += '{0} = "{1}"'.format(lst_args[i], lst_values[i])

        self.current.execute(to_execute)
        self.commit()

        data = self.current.fetchall()
        self.commit()

        self.close_DB()

        data_lst = []

        for j in xrange(len(data)):
            row_list = []
            for i in xrange(len(data[j])):
                if type(data[j][i]) != type(1):
                    row_list.append(str(data[j][i]))
                else:
                    row_list.append(data[j][i])
            data_lst.append(row_list)

        print(data_lst)
        return data_lst

    def create_photos_DB(self):
        """
        this function is responsible to make the str of create the photos db sql code
        :return: str photos code
        """
        return create_db(
            "photos",
            ["id", "album_id", "creator_id", "file_name", "creating_time"],
            [
                "INTEGER PRIMARY KEY",
                "INTEGER NOT NULL",
                "INTEGER NOT NULL",
                "TEXT NOT NULL",
                "TEXT NOT NULL",
            ],
            [
                "FOREIGN KEY (album_id) REFERENCES albums (id)",
                "FOREIGN KEY (creator_id) REFERENCES users (id)",
            ],
        )

    def create_albums_DB(self):
        """
        this function is responsible to make the str of create the albums db sql code
        :return: str albums code
        """
        return create_db(
            "albums",
            ["id", "creator_id", "album_name", "editing_time", "creating_time"],
            [
                "INTEGER PRIMARY KEY",
                "INTEGER NOT NULL",
                "TEXT NOT NULL",
                "TEXT NOT NULL",
                "TEXT NOT NULL",
            ],
            ["FOREIGN KEY (creator_id) REFERENCES users (id)"],
        )

    def create_users_DB(self):
        """
        this function is responsible to make the str of create the users db sql code
        :return: str user code
        """
        return create_db(
            "users",
            ["id", "username", "password", "last_online", "full_name", "birth_date"],
            [
                "INTEGER PRIMARY KEY",
                "TEXT UNIQUE",
                "TEXT NOT NULL",
                "TEXT NOT NULL",
                "TEXT NOT NULL",
                "TEXT NOT NULL",
            ],
        )


class Photo(object):
    """
    this class is responsible for saving the photos on the server with all their information

    """

    def __init__(
        self, photo_id=None, album_id=None, creator_id=None, creating_time=None, file_name=None
    ):
        self.photo_id = photo_id
        self.album_id = album_id
        self.creator_id = creator_id
        self.creating_time = creating_time
        self.file_name = file_name
        self.db = UserAlbumPhotoORM()

    def new_photo(self, photo_id, album_id, creator_id, file_name):
        self.photo_id = photo_id
        self.album_id = album_id
        self.creator_id = creator_id
        self.creating_time = datetime.datetime.now()
        self.file_name = file_name
        self.db = UserAlbumPhotoORM()

        # insert into the database
        self.db.insert_DB(
            "photos",
            ["id", "album_id", "creator_id", "creating_time", "file_name"],
            [self.photo_id, self.album_id, self.creator_id, self.creating_time, self.file_name],
        )

    def del_photo(self):
        # delete from the database
        self.db.delete_DB("photos", ["id"], [str(self.photo_id)])

        # DO NOT FORGET: get the photo out also from the local program memory

    def change_file_name(self, file_name):
        # update database
        self.db.update_DB("photos", "id", str(self.photo_id), "file_name", file_name)

        # update object
        self.file_name = file_name

        # DO NOT FORGET: update path


class Album(object):
    """
    this class is responsible for saving the albums and their information

    """

    def __init__(
        self, album_id=None, creator_id=None, album_name=None, editing_time=None, crating_time=None
    ):
        self.album_id = album_id
        self.creator_id = creator_id
        self.album_name = album_name
        self.editing_time = editing_time
        self.crating_time = crating_time
        self.db = UserAlbumPhotoORM()

    def new_album(self, album_id, creator_id, album_name):
        self.album_id = album_id
        self.creator_id = creator_id
        self.album_name = album_name
        self.editing_time = datetime.datetime.now()
        self.crating_time = datetime.datetime.now()
        self.db = UserAlbumPhotoORM()

        # insert into the database
        self.db.insert_DB(
            "albums",
            ["id", "creator_id", "album_name", "editing_time", "creating_time"],
            [self.album_id, self.creator_id, self.album_name, self.editing_time, self.crating_time],
        )

    def del_album(self):
        # delete from the database
        self.db.delete_DB(
            "albums", ["creator_id", "album_name"], [self.creator_id, self.album_name]
        )

        # DO NOT FORGET: get the album out also from the local program memory

    def change_album_name(self, album_name):
        # update database
        self.db.update_DB("albums", "id", str(self.album_id), "album_name", album_name)

        # update object
        self.album_name = album_name

        # DO NOT FORGET: update path


class User(object):
    """
    this class is responsible for saving the users and their information

    """

    def __init__(
        self,
        user_id=None,
        username=None,
        password=None,
        last_online=None,
        full_name=None,
        creating_date=None,
    ):
        self.user_id = user_id
        self.username = username
        self.password = password
        self.last_online = last_online
        self.full_name = full_name
        self.creating_date = creating_date
        self.db = UserAlbumPhotoORM()

    def register(self, user_id, username, password, full_name, birth_date):
        if self.db.exsists_DB("users", ["username"], [username]):
            print("not reg")
            return False
        else:
            self.user_id = user_id
            self.username = username
            self.password = password
            self.last_online = str(datetime.datetime.now())
            self.full_name = full_name
            self.birth_date = birth_date
            self.db = UserAlbumPhotoORM()

            # insert into the database
            self.db.insert_DB(
                "users",
                ["id", "username", "password", "last_online", "full_name", "birth_date"],
                [
                    self.user_id,
                    self.username,
                    self.password,
                    self.last_online,
                    self.full_name,
                    self.birth_date,
                ],
            )
            print("reg")
            return True

    def del_user(self):
        # delete from the database
        self.db.delete_DB("users", "id", str(self.user_id))

        # DO NOT FORGET: get the album out also from the local program memory

    def login(self, username, password):
        # print username, password
        if self.db.exsists_DB("users", ["username", "password"], [username, password]):
            user_data = self.db.get_row_DB("users", ["username", "password"], [username, password])
            self.user_id = user_data[0]
            self.username = user_data[1]
            self.password = user_data[2]
            self.last_online = str(datetime.datetime.now())
            self.full_name = user_data[4]
            self.birth_date = user_data[5]

            self.db.update_DB("users", "username", self.username, "last_online", self.last_online)
            return True
        return False
        # self.db.get_row_DB('users', ['username', 'password'], [username, password])

    def get_albums_by_creator(self, creator_id):
        return self.db.get_all_rows_DB("albums", ["creator_id"], [creator_id])

    def get_photos_in_album(self, album_name):
        album_id = self.get_album_id_by_album_name(album_name)
        return self.db.get_all_rows_DB("photos", ["album_id"], [album_id])

    def get_album_id_by_album_name(self, album_name):
        return self.db.get_row_DB("albums", ["album_name"], [album_name])[0]

    def change_username(self, username):
        # update database
        self.db.update_DB("users", "id", str(self.user_id), "username", username)

        # update object
        self.username = username

        # DO NOT FORGET: update path

    def change_password(self, password):
        # update database
        self.db.update_DB("users", "id", str(self.user_id), "password", password)

        # update object
        self.password = password

    def change_bith_date(self, birth_date):
        # update database
        self.db.update_DB("users", "id", str(self.user_id), "birth_date", birth_date)

        # update object
        self.birth_date = birth_date

    def change_full_name(self, full_name):
        # update database
        self.db.update_DB("users", "id", str(self.user_id), "full_name", full_name)

        # update object
        self.full_name = full_name

    def update_last_online(self, last_online):
        # update database
        self.db.update_DB("users", "id", str(self.user_id), "last_online", last_online)

        # update object
        self.last_online = last_online


def main():
    pass


if __name__ == "__main__":
    main()

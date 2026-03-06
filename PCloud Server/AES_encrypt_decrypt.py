# flake8: noqa

from base64 import b64decode, b64encode
import sys

try:
    xrange
except NameError:
    xrange = range


def _bytes_to_str(data):
    """Convert bytes to str using latin-1 encoding for Python 3 compatibility."""
    if isinstance(data, bytes):
        return data.decode('latin-1')
    return data


def _str_to_bytes(data):
    """Convert str to bytes using latin-1 encoding for Python 3 compatibility."""
    if isinstance(data, str):
        return data.encode('latin-1')
    return data


def _unwrap_python_bytes_repr(data):
    """Handle accidental text payloads formatted like b'...'."""
    if not isinstance(data, str):
        return data
    if len(data) >= 3 and data.startswith("b'") and data.endswith("'"):
        return data[2:-1]
    if len(data) >= 4 and data.startswith('b"') and data.endswith('"'):
        return data[2:-1]
    return data

SBOX = [
    [
        "\x63",
        "\x7C",
        "\x77",
        "\x7B",
        "\xF2",
        "\x6B",
        "\x6F",
        "\xC5",
        "\x30",
        "\x01",
        "\x67",
        "\x2B",
        "\xFE",
        "\xD7",
        "\xAB",
        "\x76",
    ],
    [
        "\xCA",
        "\x82",
        "\xC9",
        "\x7D",
        "\xFA",
        "\x59",
        "\x47",
        "\xF0",
        "\xAD",
        "\xD4",
        "\xA2",
        "\xAF",
        "\x9C",
        "\xA4",
        "\x72",
        "\xC0",
    ],
    [
        "\xB7",
        "\xFD",
        "\x93",
        "\x26",
        "\x36",
        "\x3F",
        "\xF7",
        "\xCC",
        "\x34",
        "\xA5",
        "\xE5",
        "\xF1",
        "\x71",
        "\xD8",
        "\x31",
        "\x15",
    ],
    [
        "\x04",
        "\xC7",
        "\x23",
        "\xC3",
        "\x18",
        "\x96",
        "\x05",
        "\x9A",
        "\x07",
        "\x12",
        "\x80",
        "\xE2",
        "\xEB",
        "\x27",
        "\xB2",
        "\x75",
    ],
    [
        "\x09",
        "\x83",
        "\x2C",
        "\x1A",
        "\x1B",
        "\x6E",
        "\x5A",
        "\xA0",
        "\x52",
        "\x3B",
        "\xD6",
        "\xB3",
        "\x29",
        "\xE3",
        "\x2F",
        "\x84",
    ],
    [
        "\x53",
        "\xD1",
        "\x00",
        "\xED",
        "\x20",
        "\xFC",
        "\xB1",
        "\x5B",
        "\x6A",
        "\xCB",
        "\xBE",
        "\x39",
        "\x4A",
        "\x4C",
        "\x58",
        "\xCF",
    ],
    [
        "\xD0",
        "\xEF",
        "\xAA",
        "\xFB",
        "\x43",
        "\x4D",
        "\x33",
        "\x85",
        "\x45",
        "\xF9",
        "\x02",
        "\x7F",
        "\x50",
        "\x3C",
        "\x9F",
        "\xA8",
    ],
    [
        "\x51",
        "\xA3",
        "\x40",
        "\x8F",
        "\x92",
        "\x9D",
        "\x38",
        "\xF5",
        "\xBC",
        "\xB6",
        "\xDA",
        "\x21",
        "\x10",
        "\xFF",
        "\xF3",
        "\xD2",
    ],
    [
        "\xCD",
        "\x0C",
        "\x13",
        "\xEC",
        "\x5F",
        "\x97",
        "\x44",
        "\x17",
        "\xC4",
        "\xA7",
        "\x7E",
        "\x3D",
        "\x64",
        "\x5D",
        "\x19",
        "\x73",
    ],
    [
        "\x60",
        "\x81",
        "\x4F",
        "\xDC",
        "\x22",
        "\x2A",
        "\x90",
        "\x88",
        "\x46",
        "\xEE",
        "\xB8",
        "\x14",
        "\xDE",
        "\x5E",
        "\x0B",
        "\xDB",
    ],
    [
        "\xE0",
        "\x32",
        "\x3A",
        "\x0A",
        "\x49",
        "\x06",
        "\x24",
        "\x5C",
        "\xC2",
        "\xD3",
        "\xAC",
        "\x62",
        "\x91",
        "\x95",
        "\xE4",
        "\x79",
    ],
    [
        "\xE7",
        "\xC8",
        "\x37",
        "\x6D",
        "\x8D",
        "\xD5",
        "\x4E",
        "\xA9",
        "\x6C",
        "\x56",
        "\xF4",
        "\xEA",
        "\x65",
        "\x7A",
        "\xAE",
        "\x08",
    ],
    [
        "\xBA",
        "\x78",
        "\x25",
        "\x2E",
        "\x1C",
        "\xA6",
        "\xB4",
        "\xC6",
        "\xE8",
        "\xDD",
        "\x74",
        "\x1F",
        "\x4B",
        "\xBD",
        "\x8B",
        "\x8A",
    ],
    [
        "\x70",
        "\x3E",
        "\xB5",
        "\x66",
        "\x48",
        "\x03",
        "\xF6",
        "\x0E",
        "\x61",
        "\x35",
        "\x57",
        "\xB9",
        "\x86",
        "\xC1",
        "\x1D",
        "\x9E",
    ],
    [
        "\xE1",
        "\xF8",
        "\x98",
        "\x11",
        "\x69",
        "\xD9",
        "\x8E",
        "\x94",
        "\x9B",
        "\x1E",
        "\x87",
        "\xE9",
        "\xCE",
        "\x55",
        "\x28",
        "\xDF",
    ],
    [
        "\x8C",
        "\xA1",
        "\x89",
        "\x0D",
        "\xBF",
        "\xE6",
        "\x42",
        "\x68",
        "\x41",
        "\x99",
        "\x2D",
        "\x0F",
        "\xB0",
        "\x54",
        "\xBB",
        "\x16",
    ],
]  # substitution box

RSBOX = [
    [
        "\x52",
        "\x09",
        "\x6A",
        "\xD5",
        "\x30",
        "\x36",
        "\xA5",
        "\x38",
        "\xBF",
        "\x40",
        "\xA3",
        "\x9E",
        "\x81",
        "\xF3",
        "\xD7",
        "\xFB",
    ],
    [
        "\x7C",
        "\xE3",
        "\x39",
        "\x82",
        "\x9B",
        "\x2F",
        "\xFF",
        "\x87",
        "\x34",
        "\x8E",
        "\x43",
        "\x44",
        "\xC4",
        "\xDE",
        "\xE9",
        "\xCB",
    ],
    [
        "\x54",
        "\x7B",
        "\x94",
        "\x32",
        "\xA6",
        "\xC2",
        "\x23",
        "\x3D",
        "\xEE",
        "\x4C",
        "\x95",
        "\x0B",
        "\x42",
        "\xFA",
        "\xC3",
        "\x4E",
    ],
    [
        "\x08",
        "\x2E",
        "\xA1",
        "\x66",
        "\x28",
        "\xD9",
        "\x24",
        "\xB2",
        "\x76",
        "\x5B",
        "\xA2",
        "\x49",
        "\x6D",
        "\x8B",
        "\xD1",
        "\x25",
    ],
    [
        "\x72",
        "\xF8",
        "\xF6",
        "\x64",
        "\x86",
        "\x68",
        "\x98",
        "\x16",
        "\xD4",
        "\xA4",
        "\x5C",
        "\xCC",
        "\x5D",
        "\x65",
        "\xB6",
        "\x92",
    ],
    [
        "\x6C",
        "\x70",
        "\x48",
        "\x50",
        "\xFD",
        "\xED",
        "\xB9",
        "\xDA",
        "\x5E",
        "\x15",
        "\x46",
        "\x57",
        "\xA7",
        "\x8D",
        "\x9D",
        "\x84",
    ],
    [
        "\x90",
        "\xD8",
        "\xAB",
        "\x00",
        "\x8C",
        "\xBC",
        "\xD3",
        "\x0A",
        "\xF7",
        "\xE4",
        "\x58",
        "\x05",
        "\xB8",
        "\xB3",
        "\x45",
        "\x06",
    ],
    [
        "\xD0",
        "\x2C",
        "\x1E",
        "\x8F",
        "\xCA",
        "\x3F",
        "\x0F",
        "\x02",
        "\xC1",
        "\xAF",
        "\xBD",
        "\x03",
        "\x01",
        "\x13",
        "\x8A",
        "\x6B",
    ],
    [
        "\x3A",
        "\x91",
        "\x11",
        "\x41",
        "\x4F",
        "\x67",
        "\xDC",
        "\xEA",
        "\x97",
        "\xF2",
        "\xCF",
        "\xCE",
        "\xF0",
        "\xB4",
        "\xE6",
        "\x73",
    ],
    [
        "\x96",
        "\xAC",
        "\x74",
        "\x22",
        "\xE7",
        "\xAD",
        "\x35",
        "\x85",
        "\xE2",
        "\xF9",
        "\x37",
        "\xE8",
        "\x1C",
        "\x75",
        "\xDF",
        "\x6E",
    ],
    [
        "\x47",
        "\xF1",
        "\x1A",
        "\x71",
        "\x1D",
        "\x29",
        "\xC5",
        "\x89",
        "\x6F",
        "\xB7",
        "\x62",
        "\x0E",
        "\xAA",
        "\x18",
        "\xBE",
        "\x1B",
    ],
    [
        "\xFC",
        "\x56",
        "\x3E",
        "\x4B",
        "\xC6",
        "\xD2",
        "\x79",
        "\x20",
        "\x9A",
        "\xDB",
        "\xC0",
        "\xFE",
        "\x78",
        "\xCD",
        "\x5A",
        "\xF4",
    ],
    [
        "\x1F",
        "\xDD",
        "\xA8",
        "\x33",
        "\x88",
        "\x07",
        "\xC7",
        "\x31",
        "\xB1",
        "\x12",
        "\x10",
        "\x59",
        "\x27",
        "\x80",
        "\xEC",
        "\x5F",
    ],
    [
        "\x60",
        "\x51",
        "\x7F",
        "\xA9",
        "\x19",
        "\xB5",
        "\x4A",
        "\x0D",
        "\x2D",
        "\xE5",
        "\x7A",
        "\x9F",
        "\x93",
        "\xC9",
        "\x9C",
        "\xEF",
    ],
    [
        "\xA0",
        "\xE0",
        "\x3B",
        "\x4D",
        "\xAE",
        "\x2A",
        "\xF5",
        "\xB0",
        "\xC8",
        "\xEB",
        "\xBB",
        "\x3C",
        "\x83",
        "\x53",
        "\x99",
        "\x61",
    ],
    [
        "\x17",
        "\x2B",
        "\x04",
        "\x7E",
        "\xBA",
        "\x77",
        "\xD6",
        "\x26",
        "\xE1",
        "\x69",
        "\x14",
        "\x63",
        "\x55",
        "\x21",
        "\x0C",
        "\x7D",
    ],
]  # reverse substitution box

Rcon = [
    ["\x01", "\x02", "\x04", "\x08", "\x10", "\x20", "\x40", "\x80", "\x1b", "\x36"],
    ["\x00", "\x00", "\x00", "\x00", "\x00", "\x00", "\x00", "\x00", "\x00", "\x00"],
    ["\x00", "\x00", "\x00", "\x00", "\x00", "\x00", "\x00", "\x00", "\x00", "\x00"],
    ["\x00", "\x00", "\x00", "\x00", "\x00", "\x00", "\x00", "\x00", "\x00", "\x00"],
]
# this table is being used to create the round keys

Matrix = [
    [2, 3, 1, 1],
    [1, 2, 3, 1],
    [1, 1, 2, 3],
    [3, 1, 1, 2],
]  # this table is being used to mix_columns function


def encrypt(data, key, iv=None, encode=False):
    """
    encrypt the given data by the key using AES128 protocol
    :param data: the text to encrypt :type str or bytes
    :param key: the key of the encryption :type str or bytes
    :param iv: optional initialization vector to use CBC instead of ECB :type str or bytes
    :param encode: return as Base64 encoded :type bool
    :return: encrypted data
    """
    # Convert bytes to str if needed
    data = _bytes_to_str(data)
    key = _bytes_to_str(key)

    if len(key) > 16:
        key = key[:16]
    else:
        while len(key) != 16:
            key += "\x00"
    cipher_key = block_to_2d_lst(str(key))
    rounds_keys = create_round_key(cipher_key)
    blocks = data_to_blocks(data)

    if iv:
        iv = _bytes_to_str(iv)
        iv = data_to_blocks(iv)[0]

    for j in xrange(len(blocks)):
        if iv:
            blocks[j] = add_round_key(blocks[j], iv)

        blocks[j] = add_round_key(blocks[j], cipher_key)
        for i in xrange(10):
            blocks[j] = sub_bytes(blocks[j])
            blocks[j] = shift_row(blocks[j])
            if i != 9:
                blocks[j] = mix_columns(blocks[j])
            blocks[j] = add_round_key(blocks[j], rounds_keys[i])
        if iv:
            iv = blocks[j]
    if encode:
        result = blocks_to_string(blocks)
        encoded = b64encode(_str_to_bytes(result))
        return _bytes_to_str(encoded)
    return blocks_to_string(blocks)


def decrypt(data, key, iv=None, decode=False):
    """
    decrypt the given data by the key using AES128 protocol
    :param data: the text to encrypt :type str or bytes
    :param key: the key of the encryption :type str or bytes
    :param iv: optional initialization vector to use CBC instead of ECB :type str or bytes
    :param decode: return as Base64 encoded :type bool
    :return: decrypted data
    """
    # Convert bytes to str if needed
    data = _bytes_to_str(data)
    data = _unwrap_python_bytes_repr(data)
    key = _bytes_to_str(key)

    if decode:
        try:
            decoded = b64decode(data)
            data = _bytes_to_str(decoded)
        except (TypeError, Exception) as e:
            print(e)
            return ""

    if len(key) > 16:
        key = key[:16]
    else:
        while len(key) != 16:
            key += "\x00"
    cipher_key = block_to_2d_lst(str(key))
    rounds_keys = create_round_key(cipher_key)
    rounds_keys = [cipher_key] + rounds_keys
    blocks = data_to_blocks(data)

    if iv:
        iv = _bytes_to_str(iv)
        iv = data_to_blocks(iv)[0]

    for j in xrange(len(blocks)):
        if iv:
            previous_block = blocks[j]
        blocks[j] = add_round_key(blocks[j], rounds_keys[-1])
        for i in range(10)[::-1]:
            blocks[j] = ishift_row(blocks[j])
            blocks[j] = isub_bytes(blocks[j])
            blocks[j] = add_round_key(blocks[j], rounds_keys[i])
            if i != 0:
                blocks[j] = imix_columns(blocks[j])
        if iv:
            blocks[j] = add_round_key(blocks[j], iv)
            iv = previous_block

    return inonefill(blocks_to_string(blocks))


def inonefill(s):
    """
    take down the zeros from the start
    :param s: the string :type str
    :return:
    """
    if len(s) != 0:
        while s[-1] == "\x00":
            s = s[: len(s) - 1]
        return s


def create_round_key(chipper_key):
    """
    create the rounds keys
    :param chipper_key:
    :return: the round keys
    """
    rounds_keys = [chipper_key]
    new_key = [["", "", "", ""], ["", "", "", ""], ["", "", "", ""], ["", "", "", ""]]

    for i in xrange(10):
        # first col of the key
        pre_key = rounds_keys[i]
        col3 = [
            use_sbox(pre_key[1][3]),
            use_sbox(pre_key[2][3]),
            use_sbox(pre_key[3][3]),
            use_sbox(pre_key[0][3]),
        ]
        col1 = [pre_key[0][0], pre_key[1][0], pre_key[2][0], pre_key[3][0]]
        rcon_col = [Rcon[0][i], Rcon[1][i], Rcon[2][i], Rcon[3][i]]
        new_key[0][0] = chr(ord(col3[0]) ^ ord(col1[0]) ^ ord(rcon_col[0]))
        new_key[1][0] = chr(ord(col3[1]) ^ ord(col1[1]) ^ ord(rcon_col[1]))
        new_key[2][0] = chr(ord(col3[2]) ^ ord(col1[2]) ^ ord(rcon_col[2]))
        new_key[3][0] = chr(ord(col3[3]) ^ ord(col1[3]) ^ ord(rcon_col[3]))

        # the other cols of the key
        for j in xrange(1, 4):
            new_key[0][j] = chr(ord(new_key[0][j - 1]) ^ ord(pre_key[0][j]))
            new_key[1][j] = chr(ord(new_key[1][j - 1]) ^ ord(pre_key[1][j]))
            new_key[2][j] = chr(ord(new_key[2][j - 1]) ^ ord(pre_key[2][j]))
            new_key[3][j] = chr(ord(new_key[3][j - 1]) ^ ord(pre_key[3][j]))
        rounds_keys.append(new_key)
        new_key = [["", "", "", ""], ["", "", "", ""], ["", "", "", ""], ["", "", "", ""]]
    return rounds_keys[1:]


def sub_bytes(block):
    """
    replacing the bytes in the block using SBOX table protocol
    :param block: the block of data :type list of list
    :return: new block
    """
    new_block = []
    for i in xrange(len(block)):
        row = []
        for j in xrange(len(block[i])):
            row.append(use_sbox(block[i][j]))
        new_block.append(row)
    return new_block


def isub_bytes(block):
    """
    replacing the bytes in the block using RSBOX table protocol
    this function is being use to reverse the effect of sub_bytes
    :param block: the block of data :type list of list
    :return: new block
    """
    new_block = []
    for i in xrange(len(block)):
        row = []
        for j in xrange(len(block[i])):
            row.append(use_rsbox(block[i][j]))
        new_block.append(row)
    return new_block


def shift_row(block):
    """
    rotate right every row by it's index
    :param block: the block of data :type list of list
    :return: rotated block
    """
    cnt = 0
    return [
        [block[0][0], block[0][1], block[0][2], block[0][3]],
        [block[1][1], block[1][2], block[1][3], block[1][0]],
        [block[2][2], block[2][3], block[2][0], block[2][1]],
        [block[3][3], block[3][0], block[3][1], block[3][2]],
    ]


def ishift_row(block):
    """
    rotate left every row by it's index
    this function is being use to reverse the effect of shift_row
    :param block: the block of data :type list of list
    :return: rotated block
    """
    cnt = 0
    return [
        [block[0][0], block[0][1], block[0][2], block[0][3]],
        [block[1][3], block[1][0], block[1][1], block[1][2]],
        [block[2][2], block[2][3], block[2][0], block[2][1]],
        [block[3][1], block[3][2], block[3][3], block[3][0]],
    ]


def mul2(byte):
    if byte & 0b10000000 != 0:
        return ((byte * 2) ^ 0x1B) & 0b11111111
    else:
        return (byte * 2) & 0b11111111


def mix_columns(block):
    """
    mix the columns by the MATRIX
    :param block: the block of data :type list of list
    :return: mixed block
    """
    global Matrix
    new_block = [[], [], [], []]

    for i in xrange(len(block)):
        col = [block[0][i], block[1][i], block[2][i], block[3][i]]
        cnt = 0
        for mat in Matrix:
            ans = []
            for j in xrange(len(mat)):
                byte = ord(col[j])
                if mat[j] == 1:
                    ans.append(byte)
                elif mat[j] == 2:
                    ans.append(mul2(byte))
                else:
                    ans.append(byte ^ mul2(byte))
            the_num = 0
            for j in ans:
                the_num = the_num ^ j
            new_block[cnt].append(chr(the_num))
            cnt += 1

    return new_block


# def imix_columns(block):
#     global iMatrix
#     new_block = [[],
#                  [],
#                  [],
#                  []]
#
#     for i in xrange(len(block)):
#         col = [block[0][i], block[1][i], block[2][i], block[3][i]]
#         cnt = 0
#         for mat in iMatrix:
#             ans = []
#             for j in xrange(len(mat)):
#                 byte = ord(col[j])
#                 if mat[j] == 9:
#                     ans.append((mul2(mul2(mul2(byte))) ^ byte) & 0b11111111)
#                 elif mat[j] == 11:
#                     ans.append((mul2(mul2(mul2(byte)) ^ byte) ^ byte) & 0b11111111)
#                 elif mat[j] == 13:
#                     ans.append((mul2(mul2(mul2(byte) ^ byte)) ^ byte) & 0b11111111)
#                 else:
#                     ans.append(mul2((mul2(mul2(byte) ^ byte) ^ byte)) & 0b11111111)
#
#             the_num = 0
#             for j in ans:
#                 the_num = the_num ^ j
#             new_block[cnt].append(chr(the_num))
#             cnt += 1
#
#     return new_block


def add_round_key(block, round_key):
    """
    do XOR between the block and the round key
    :param block: the block of data :type list of list
    :param round_key: the current round key (can also be an other block of some data) :type list of list
    :return: block of the XOR outcome
    """
    new_block = []
    for i in xrange(len(block)):
        new_block.append([])
        for j in xrange(len(block[i])):
            new_block[len(new_block) - 1].append(chr(ord(block[i][j]) ^ ord(round_key[i][j])))
    return new_block


def use_sbox(ch):
    """
    replacing the a char by SBOX protocol
    the first 4 bits are the line
    the last 4 bits are the row
    :param ch: the char :type chr
    :return: the char replaced by SBOX protocol
    """
    global SBOX
    d = hex(ord(ch))[2:]
    d = d if len(d) == 2 else ("0" + d)
    return SBOX[int(d[0], 16)][int(d[1], 16)]


def use_rsbox(ch):
    """
    replacing the a char by RSBOX protocol (the same as SBOX - reverse the effect of SBOX)
    the first 4 bits are the line
    the last 4 bits are the row
    :param ch: the char :type chr
    :return: the char replaced by RSBOX protocol
    """
    global SBOX
    d = hex(ord(ch))[2:]
    d = d if len(d) == 2 else ("0" + d)
    return RSBOX[int(d[0], 16)][int(d[1], 16)]


def data_to_blocks(data):
    """
    converting string of data to 4X4 blocks
    :param data: :type str or bytes
    :return: list of blocks (list that is a list of list)
    """
    # Convert bytes to str if needed (Python 3 compatibility)
    data = _bytes_to_str(data)

    blocks = []
    block = ""
    for i in data:
        block += i
        if len(block) == 16:
            blocks.append(block)
            block = ""
    if block != "":
        while len(block) != 16:
            block += "\x00"
        blocks.append(block)
    for i in xrange(len(blocks)):
        blocks[i] = block_to_2d_lst(blocks[i])
    return blocks


def block_to_2d_lst(data):
    """
    converting 16 length data to 4X4 block
    :param data: 16 length data :type str
    :return: a block (list od list)
    """
    new_data = [["", "", "", ""], ["", "", "", ""], ["", "", "", ""], ["", "", "", ""]]
    for i in xrange(4):
        for j in xrange(4):
            new_data[j][i] = data[(i * 4) + j]
    return new_data


def blocks_to_string(blocks):
    """
    converting blocks to a string
    :param blocks: list of blocks :type list of list of list
    :return: the data :type str
    """
    string_ret = ""
    for block in blocks:
        for i in xrange(4):
            for j in xrange(4):
                string_ret += block[j][i]
    return str(string_ret)


def block_to_list(block):
    """
    converting list of list block to a list
    :param block: the block of data :type list of list
    :return: list
    """
    lst = []
    for row in block:
        lst += row
    for i in xrange(len(lst)):
        lst[i] = ord(lst[i])
    return lst


def list_to_block(lst):
    """
    converting list to a list of list block
    :param lst: the list of data :type list
    :return: block
    """
    block = [[], [], [], []]
    for i in xrange(len(lst)):
        lst[i] = chr(lst[i])
    for i in xrange(4):
        for j in xrange(4):
            block[i].append(lst[i * 4 + j])
    return block


def imix_columns(state):
    state = block_to_list(state)
    # iterate over the 4 columns
    for i in range(4):
        # construct one column by slicing over the 4 rows
        column = state[i : i + 16 : 4]
        # apply the mixColumn on one column
        column = mixColumn(column)
        # put the values back into the state
        state[i : i + 16 : 4] = column

    return list_to_block(state)


# galois multiplication of 1 column of the 4x4 matrix
def mixColumn(column):
    mult = [14, 9, 13, 11]
    cpy = list(column)
    g = galois_multiplication

    column[0] = g(cpy[0], mult[0]) ^ g(cpy[3], mult[1]) ^ g(cpy[2], mult[2]) ^ g(cpy[1], mult[3])
    column[1] = g(cpy[1], mult[0]) ^ g(cpy[0], mult[1]) ^ g(cpy[3], mult[2]) ^ g(cpy[2], mult[3])
    column[2] = g(cpy[2], mult[0]) ^ g(cpy[1], mult[1]) ^ g(cpy[0], mult[2]) ^ g(cpy[3], mult[3])
    column[3] = g(cpy[3], mult[0]) ^ g(cpy[2], mult[1]) ^ g(cpy[1], mult[2]) ^ g(cpy[0], mult[3])
    return column


def galois_multiplication(a, b):
    """Galois multiplication of 8 bit characters a and b."""
    p = 0
    for counter in range(8):
        if b & 1:
            p ^= a
        hi_bit_set = a & 0x80
        a <<= 1
        # keep a 8 bit
        a &= 0xFF
        if hi_bit_set:
            a ^= 0x1B
        b >>= 1
    return p


if __name__ == "__main__":
    dta = "12345678901234567"  # raw_input('please enter data to encrypt: ')
    ky = "1234567890123456"  # raw_input('please enter key to encrypt: ')
    edata = encrypt(dta, ky)
    print("encrypted data: " + edata)
    for c in edata:
        sys.stdout.write(str(ord(c)) + " ")
    sys.stdout.write("\n")
    ddata = decrypt(edata, ky)
    print("decrypted data: " + ddata)

#!/usr/bin/env python3
"""Apply Detour's minimal SOCKS5 RFC1929 auth extension to pinned ByeDPI.

The build script resets ByeDPI to one exact upstream commit before invoking this
file. Every replacement below must match exactly once; any upstream/source drift
fails closed instead of producing a partially patched binary.
"""

from pathlib import Path
import sys


root = Path(sys.argv[1] if len(sys.argv) > 1 else ".").resolve()


def replace_once(relative: str, before: str, after: str) -> None:
    path = root / relative
    text = path.read_text(encoding="utf-8")
    count = text.count(before)
    if count != 1:
        raise SystemExit(f"{relative}: expected exactly one auth anchor, found {count}")
    path.write_text(text.replace(before, after, 1), encoding="utf-8")


replace_once(
    "conev.h",
    """#define FLAG_S4 1
#define FLAG_S5 2
#define FLAG_CONN 4
#define FLAG_HTTP 8
""",
    """#define FLAG_S4 1
#define FLAG_S5 2
#define FLAG_CONN 4
#define FLAG_HTTP 8
#define FLAG_S5_AUTH 16
""",
)

replace_once(
    "proxy.h",
    """#define S_AUTH_NONE 0x00
#define S_AUTH_BAD 0xff
""",
    """#define S_AUTH_NONE 0x00
#define S_AUTH_USERPASS 0x02
#define S_AUTH_BAD 0xff
""",
)

replace_once(
    "proxy.h",
    """#define S_SIZE_I6 22
#define S_SIZE_ID 7

void map_fix(union sockaddr_u *addr, char f6);
""",
    """#define S_SIZE_I6 22
#define S_SIZE_ID 7

int load_socks5_auth_stdin(void);

void map_fix(union sockaddr_u *addr, char f6);
""",
)

replace_once(
    "main.c",
    r'''    "    -x, --debug <level>       Print logs, 0, 1 or 2\n"
    "    -g, --def-ttl <num>       TTL for all outgoing connections\n"
''',
    r'''    "    -x, --debug <level>       Print logs, 0, 1 or 2\n"
    "    -J, --socks5-auth-stdin   Require SOCKS5 username/password from stdin\n"
    "    -g, --def-ttl <num>       TTL for all outgoing connections\n"
''',
)

replace_once(
    "main.c",
    """    {\"http-connect\",  0, 0, 'G'},
    {\"help\",          0, 0, 'h'},
""",
    """    {\"http-connect\",  0, 0, 'G'},
    {\"socks5-auth-stdin\", 0, 0, 'J'},
    {\"help\",          0, 0, 'h'},
""",
)

replace_once(
    "main.c",
    """    const char *pid_file = 0;
    bool daemonize = 0;
""",
    """    const char *pid_file = 0;
    bool daemonize = 0;
    bool socks5_auth_stdin = 0;
""",
)

replace_once(
    "main.c",
    """        case 'G':
            params.http_connect = 1;
            break;
        #ifdef __linux__
""",
    """        case 'G':
            params.http_connect = 1;
            break;
        case 'J':
            socks5_auth_stdin = 1;
            break;
        #ifdef __linux__
""",
)

replace_once(
    "main.c",
    """    params.mempool = mem_pool(MF_EXTRA, CMP_BYTES);
""",
    """    if (socks5_auth_stdin && load_socks5_auth_stdin() < 0) {
        fprintf(stderr, \"invalid SOCKS5 auth on stdin\\n\");
        clear_params();
        return -1;
    }
    params.mempool = mem_pool(MF_EXTRA, CMP_BYTES);
""",
)

replace_once(
    "proxy.c",
    """static int auth_socks5(int fd, const char *buffer, ssize_t n)
""",
    """static char socks5_user[256];
static char socks5_pass[256];
static size_t socks5_user_len;
static size_t socks5_pass_len;
static char socks5_auth_enabled;


static int read_auth_line(char out[256])
{
    char line[257];
    if (!fgets(line, sizeof(line), stdin)) {
        return -1;
    }
    size_t len = strcspn(line, \"\\r\\n\");
    if (!len || len > 255 || line[len] == '\\0') {
        return -1;
    }
    memcpy(out, line, len);
    out[len] = 0;
    return (int)len;
}


int load_socks5_auth_stdin(void)
{
    int user_len = read_auth_line(socks5_user);
    int pass_len = read_auth_line(socks5_pass);
    if (user_len < 1 || pass_len < 1) {
        return -1;
    }
    socks5_user_len = (size_t)user_len;
    socks5_pass_len = (size_t)pass_len;
    socks5_auth_enabled = 1;
    return 0;
}


static int auth_equal(const char *a, size_t an, const char *b, size_t bn)
{
    if (an != bn) {
        return 0;
    }
    unsigned char diff = 0;
    for (size_t i = 0; i < an; i++) {
        diff |= (unsigned char)a[i] ^ (unsigned char)b[i];
    }
    return diff == 0;
}


static int auth_socks5(int fd, const char *buffer, ssize_t n)
""",
)

replace_once(
    "proxy.c",
    """static int auth_socks5(int fd, const char *buffer, ssize_t n)
{
    if (n <= 2 || (uint8_t)buffer[1] != (n - 2)) {
        return -1;
    }
    uint8_t c = S_AUTH_BAD;
    for (long i = 2; i < n; i++)
        if (buffer[i] == S_AUTH_NONE) {
            c = S_AUTH_NONE;
            break;
        }
    uint8_t a[2] = { S_VER5, c };
    if (send(fd, (char *)a, sizeof(a), 0) < 0) {
        uniperror(\"send\");
        return -1;
    }
    return c != S_AUTH_BAD ? 0 : -1;
}
""",
    """static int auth_socks5(int fd, const char *buffer, ssize_t n)
{
    if (n <= 2 || (uint8_t)buffer[1] != (n - 2)) {
        return -1;
    }
    uint8_t required = socks5_auth_enabled ? S_AUTH_USERPASS : S_AUTH_NONE;
    uint8_t c = S_AUTH_BAD;
    for (long i = 2; i < n; i++)
        if ((uint8_t)buffer[i] == required) {
            c = required;
            break;
        }
    uint8_t a[2] = { S_VER5, c };
    if (send(fd, (char *)a, sizeof(a), 0) < 0) {
        uniperror(\"send\");
        return -1;
    }
    return c != S_AUTH_BAD ? c : -1;
}


static int auth_socks5_userpass(int fd, const char *buffer, ssize_t n)
{
    if (!socks5_auth_enabled || n < 5 || (uint8_t)buffer[0] != 0x01) {
        return -1;
    }
    size_t user_len = (uint8_t)buffer[1];
    size_t pass_len_pos = 2 + user_len;
    if (!user_len || pass_len_pos >= (size_t)n) {
        return -1;
    }
    size_t pass_len = (uint8_t)buffer[pass_len_pos];
    size_t expected = pass_len_pos + 1 + pass_len;
    uint8_t status = 1;
    if (pass_len && expected == (size_t)n &&
            auth_equal(buffer + 2, user_len, socks5_user, socks5_user_len) &&
            auth_equal(buffer + pass_len_pos + 1, pass_len,
                socks5_pass, socks5_pass_len)) {
        status = 0;
    }
    uint8_t reply[2] = { 0x01, status };
    if (send(fd, (char *)reply, sizeof(reply), 0) < 0) {
        uniperror(\"send\");
        return -1;
    }
    return status ? -1 : 0;
}
""",
)

replace_once(
    "proxy.c",
    """    int error = 0;
""" + (" " * 4) + """
    if (*buff->data == S_VER5) {
        if (val->flag != FLAG_S5) {
            if (auth_socks5(val->fd, buff->data, n)) {
                return -1;
            }
            val->flag = FLAG_S5;
            return 0;
        }
""",
    """    int error = 0;
""" + (" " * 4) + """
    if (val->flag == FLAG_S5_AUTH) {
        if (auth_socks5_userpass(val->fd, buff->data, n)) {
            return -1;
        }
        val->flag = FLAG_S5;
        return 0;
    }
    if (socks5_auth_enabled && *buff->data != S_VER5) {
        return -1;
    }
    if (*buff->data == S_VER5) {
        if (val->flag != FLAG_S5) {
            int auth = auth_socks5(val->fd, buff->data, n);
            if (auth < 0) {
                return -1;
            }
            val->flag = auth == S_AUTH_USERPASS ? FLAG_S5_AUTH : FLAG_S5;
            return 0;
        }
""",
)

print("Detour SOCKS5 auth transform applied")

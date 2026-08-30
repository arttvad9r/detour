#!/usr/bin/env python3
from pathlib import Path
import sys

if len(sys.argv) != 2:
    raise SystemExit("usage: patch_auth.py <byedpi-source-dir>")

root = Path(sys.argv[1])


def replace_once(relative: str, old: str, new: str) -> None:
    path = root / relative
    text = path.read_text()
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"FATAL: expected one auth patch anchor in {relative}, found {count}")
    path.write_text(text.replace(old, new, 1))


replace_once(
    "conev.h",
    "#define FLAG_HTTP 8\n",
    "#define FLAG_HTTP 8\n#define FLAG_S5_AUTH 16\n",
)

replace_once(
    "params.h",
    "#define FM_ORIG 2\n",
    "#define FM_ORIG 2\n\n#define SOCKS_AUTH_MAX 255\n",
)
replace_once(
    "params.h",
    "    const char *cache_file;\n};\n",
    """    const char *cache_file;

    bool socks_auth;
    uint8_t socks_user_len;
    uint8_t socks_pass_len;
    char socks_user[SOCKS_AUTH_MAX + 1];
    char socks_pass[SOCKS_AUTH_MAX + 1];
};
""",
)

replace_once(
    "main.c",
    '    "    -c, --max-conn <count>    Connection count limit, default 512\\n"\n',
    '    "    -c, --max-conn <count>    Connection count limit, default 512\\n"\n'
    '    "    -z, --auth-stdin          Require SOCKS5 username/password from stdin\\n"\n',
)
replace_once(
    "main.c",
    '    {"max-conn",      1, 0, \'c\'},\n',
    '    {"max-conn",      1, 0, \'c\'},\n'
    '    {"auth-stdin",    0, 0, \'z\'},\n',
)
replace_once(
    "main.c",
    "\n\nint main(int argc, char **argv) \n{\n",
    """

static int read_auth_line(char *out, uint8_t *out_len)
{
    char line[SOCKS_AUTH_MAX + 2];
    if (!fgets(line, sizeof(line), stdin)) {
        return -1;
    }
    size_t len = strcspn(line, "\\r\\n");
    if (len == 0 || len > SOCKS_AUTH_MAX) {
        return -1;
    }
    // A full buffer without newline means the credential exceeded the limit.
    if (line[len] == 0 && !feof(stdin)) {
        return -1;
    }
    memcpy(out, line, len);
    out[len] = 0;
    *out_len = (uint8_t)len;
    return 0;
}


static int read_socks_auth_stdin(void)
{
    if (read_auth_line(params.socks_user, &params.socks_user_len)
            || read_auth_line(params.socks_pass, &params.socks_pass_len)) {
        return -1;
    }
    params.socks_auth = 1;
    return 0;
}


int main(int argc, char **argv) 
{
""",
)
replace_once(
    "main.c",
    "    bool all_limited = 1;\n",
    "    bool all_limited = 1;\n    bool auth_stdin = 0;\n",
)
replace_once(
    "main.c",
    "        case 'x': //\n",
    "        case 'z':\n            auth_stdin = 1;\n            break;\n\n        case 'x': //\n",
)
replace_once(
    "main.c",
    """    if (all_limited) {
        dp = add_group(dp);
""",
    """    if (auth_stdin && read_socks_auth_stdin()) {
        fprintf(stderr, "invalid SOCKS5 credentials on stdin\\n");
        clear_params();
        return -1;
    }
    if (all_limited) {
        dp = add_group(dp);
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
        uniperror("send");
        return -1;
    }
    return c != S_AUTH_BAD ? 0 : -1;
}
""",
    """#define S_AUTH_USERPASS 0x02
#define S_AUTH_VERSION 0x01

static int auth_socks5(int fd, const char *buffer, ssize_t n)
{
    if (n <= 2 || (uint8_t)buffer[1] != (n - 2)) {
        return -1;
    }
    const uint8_t required = params.socks_auth ? S_AUTH_USERPASS : S_AUTH_NONE;
    uint8_t c = S_AUTH_BAD;
    for (long i = 2; i < n; i++)
        if ((uint8_t)buffer[i] == required) {
            c = required;
            break;
        }
    uint8_t a[2] = { S_VER5, c };
    if (send(fd, (char *)a, sizeof(a), 0) < 0) {
        uniperror("send");
        return -1;
    }
    return c != S_AUTH_BAD ? 0 : -1;
}


static int auth_socks5_userpass(int fd, const char *buffer, ssize_t n)
{
    uint8_t status = 1;
    if (n >= 5 && (uint8_t)buffer[0] == S_AUTH_VERSION) {
        const size_t user_len = (uint8_t)buffer[1];
        const size_t pass_len_pos = 2 + user_len;
        if (pass_len_pos < (size_t)n) {
            const size_t pass_len = (uint8_t)buffer[pass_len_pos];
            const size_t expected = pass_len_pos + 1 + pass_len;
            if ((size_t)n == expected
                    && user_len == params.socks_user_len
                    && pass_len == params.socks_pass_len
                    && !memcmp(buffer + 2, params.socks_user, user_len)
                    && !memcmp(buffer + pass_len_pos + 1, params.socks_pass, pass_len)) {
                status = 0;
            }
        }
    }
    uint8_t reply[2] = { S_AUTH_VERSION, status };
    if (send(fd, (char *)reply, sizeof(reply), 0) < 0) {
        uniperror("send");
        return -1;
    }
    return status ? -1 : 0;
}
""",
)
replace_once(
    "proxy.c",
    """    int error = 0;
    
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

    if (val->flag == FLAG_S5_AUTH) {
        if (auth_socks5_userpass(val->fd, buff->data, n)) {
            return -1;
        }
        val->flag = FLAG_S5;
        return 0;
    }
    // When authentication is enabled, SOCKS4 and HTTP CONNECT must not provide
    // an unauthenticated bypass around the SOCKS5 username/password exchange.
    if (params.socks_auth && val->flag == 0 && (uint8_t)*buff->data != S_VER5) {
        return -1;
    }
    if (*buff->data == S_VER5) {
        if (val->flag != FLAG_S5) {
            if (auth_socks5(val->fd, buff->data, n)) {
                return -1;
            }
            val->flag = params.socks_auth ? FLAG_S5_AUTH : FLAG_S5;
            return 0;
        }
""",
)

print("byedpi: Detour SOCKS5 auth patch applied")

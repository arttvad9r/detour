#!/usr/bin/env python3
import socket
import subprocess
import sys
import time

USER = b"detour-smoke"
PASSWORD = b"correct-smoke-password"
WRONG_PASSWORD = b"wrong-smoke-password"


def free_port() -> int:
    with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as sock:
        sock.bind(("127.0.0.1", 0))
        return sock.getsockname()[1]


def recv_exact(sock: socket.socket, size: int) -> bytes:
    data = bytearray()
    while len(data) < size:
        chunk = sock.recv(size - len(data))
        if not chunk:
            break
        data.extend(chunk)
    return bytes(data)


def connect(port: int) -> socket.socket:
    sock = socket.create_connection(("127.0.0.1", port), timeout=1.0)
    sock.settimeout(1.0)
    return sock


def wait_until_listening(proc: subprocess.Popen[bytes], port: int) -> None:
    deadline = time.monotonic() + 4.0
    while time.monotonic() < deadline:
        if proc.poll() is not None:
            raise RuntimeError(f"ciadpi exited before listening: {proc.returncode}")
        try:
            with connect(port):
                return
        except OSError:
            time.sleep(0.05)
    raise RuntimeError("ciadpi did not start listening")


def negotiate_userpass(sock: socket.socket) -> None:
    sock.sendall(b"\x05\x01\x02")
    response = recv_exact(sock, 2)
    if response != b"\x05\x02":
        raise AssertionError(f"expected RFC1929 method, got {response!r}")


def auth_frame(password: bytes) -> bytes:
    return bytes((1, len(USER))) + USER + bytes((len(password),)) + password


def run(binary: str) -> None:
    port = free_port()
    proc = subprocess.Popen(
        [binary, "-i", "127.0.0.1", "-p", str(port), "-U", "--auth-stdin"],
        stdin=subprocess.PIPE,
        stdout=subprocess.DEVNULL,
        stderr=subprocess.STDOUT,
    )
    try:
        assert proc.stdin is not None
        proc.stdin.write(USER + b"\n" + PASSWORD + b"\n")
        proc.stdin.close()
        wait_until_listening(proc, port)

        with connect(port) as sock:
            sock.sendall(b"\x05\x01\x00")
            response = recv_exact(sock, 2)
            if response != b"\x05\xff":
                raise AssertionError(f"NO AUTH was not rejected: {response!r}")

        with connect(port) as sock:
            negotiate_userpass(sock)
            sock.sendall(auth_frame(WRONG_PASSWORD))
            response = recv_exact(sock, 2)
            if response != b"\x01\x01":
                raise AssertionError(f"wrong password was not rejected: {response!r}")

        with connect(port) as sock:
            negotiate_userpass(sock)
            sock.sendall(auth_frame(PASSWORD))
            response = recv_exact(sock, 2)
            if response != b"\x01\x00":
                raise AssertionError(f"correct password was not accepted: {response!r}")

        # Auth mode must not leave the legacy SOCKS4 parser as a bypass.
        with connect(port) as sock:
            sock.sendall(b"\x04\x01\x00\x50\x7f\x00\x00\x01\x00")
            try:
                response = sock.recv(1)
            except ConnectionResetError:
                response = b""
            if response != b"":
                raise AssertionError("SOCKS4 was accepted while auth is required")

        print("byedpi auth smoke: PASS")
    finally:
        if proc.poll() is None:
            proc.terminate()
            try:
                proc.wait(timeout=2.0)
            except subprocess.TimeoutExpired:
                proc.kill()
                proc.wait(timeout=2.0)


if __name__ == "__main__":
    if len(sys.argv) != 2:
        raise SystemExit("usage: auth_smoke.py <ciadpi-host-binary>")
    run(sys.argv[1])

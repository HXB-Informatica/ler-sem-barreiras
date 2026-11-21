#!/usr/bin/env python3
"""Envia o microfone do computador para o emulador.

O AudioRecord do Android no emulador fica em silêncio. A app de depuração liga a
10.0.2.2:27183, que é este processo em 127.0.0.1. PCM s16le, 48 kHz, mono.
"""

import argparse
import os
import signal
import socket
import subprocess

PORT = 27183


def capture_source() -> str | None:
    chosen = os.environ.get("LSB_PULSE_SOURCE", "").strip()
    if chosen:
        return chosen
    try:
        sources = subprocess.check_output(["pactl", "list", "short", "sources"], text=True)
    except (OSError, subprocess.CalledProcessError):
        return None
    for line in sources.splitlines():
        parts = line.split()
        if len(parts) < 2 or ".monitor" in parts[1]:
            continue
        if "HyperX" in parts[1] or "Cloud_III" in parts[1]:
            return parts[1]
    try:
        default = subprocess.check_output(["pactl", "get-default-source"], text=True).strip()
    except (OSError, subprocess.CalledProcessError):
        return None
    if default and ".monitor" not in default:
        return default
    return None


def record_command(source: str | None) -> list[str]:
    command = [
        "parecord",
        "--raw",
        "--format=s16le",
        "--rate=48000",
        "--channels=1",
        "--latency-msec=20",
        "--client-name=ler-host-mic",
    ]
    if source:
        command += ["--device", source]
    if shutil_which("stdbuf"):
        return ["stdbuf", "-o0", *command]
    return command


def shutil_which(name: str) -> bool:
    from shutil import which

    return which(name) is not None


def serve(port: int) -> None:
    server = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    server.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    server.bind(("127.0.0.1", port))
    server.listen(1)
    print(f"Microfone do computador em 127.0.0.1:{port} ({capture_source()})", flush=True)
    while True:
        conn, _addr = server.accept()
        source = capture_source()
        proc = subprocess.Popen(
            record_command(source),
            stdout=subprocess.PIPE,
            stderr=subprocess.DEVNULL,
        )
        try:
            assert proc.stdout is not None
            while True:
                chunk = proc.stdout.read(4096)
                if not chunk:
                    break
                conn.sendall(chunk)
        except (BrokenPipeError, ConnectionResetError, OSError):
            pass
        finally:
            proc.send_signal(signal.SIGTERM)
            try:
                proc.wait(timeout=1)
            except subprocess.TimeoutExpired:
                proc.kill()
            conn.close()


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--port", type=int, default=PORT)
    args = parser.parse_args()
    serve(args.port)


if __name__ == "__main__":
    main()

#!/usr/bin/env python3
import argparse
import shutil
import subprocess
import sys
import time

PACKAGE = "com.lightningstudio.watchrss"
PROVIDER_COMPONENT = f"{PACKAGE}/.debug.DebugLogProvider"
SENTINEL = "DOUYIN_PLAYBACK_DEBUG"


def run(cmd: list[str]) -> subprocess.CompletedProcess[str]:
    return subprocess.run(cmd, capture_output=True, text=True, check=False)


def adb_cmd(args: list[str], serial: str | None) -> list[str]:
    cmd = ["adb"]
    if serial:
        cmd += ["-s", serial]
    cmd += args
    return cmd


def fetch_debug_text(serial: str | None, retries: int = 8, retry_delay_s: float = 0.25) -> tuple[str, str | None]:
    last_error: str | None = None
    for attempt in range(max(1, retries)):
        completed = run(
            adb_cmd(
                ["shell", "dumpsys", "activity", "provider", PROVIDER_COMPONENT, "--douyin-playback"],
                serial,
            )
        )
        if completed.returncode != 0:
            last_error = completed.stderr.strip() or completed.stdout.strip() or f"adb exited with {completed.returncode}"
        else:
            lines = completed.stdout.splitlines()
            try:
                start = next(index for index, line in enumerate(lines) if SENTINEL in line)
            except StopIteration:
                body = completed.stdout.strip()
                last_error = "provider returned empty output" if not body else f"sentinel not found\n{body}"
            else:
                extracted = []
                for line in lines[start + 1 :]:
                    extracted.append(line[6:] if line.startswith("      ") else line.lstrip())
                return "\n".join(extracted).strip(), None

        if attempt + 1 < max(1, retries):
            time.sleep(max(0.05, retry_delay_s))
    return "", last_error or "unknown provider error"


def clear_screen() -> None:
    sys.stdout.write("\x1b[?1049h\x1b[2J\x1b[H")
    sys.stdout.flush()


def restore_screen() -> None:
    sys.stdout.write("\x1b[?1049l")
    sys.stdout.flush()


def render_frame(body: str, error: str | None, serial: str | None, interval_s: float) -> None:
    width = shutil.get_terminal_size((100, 40)).columns
    header = f"Douyin adb TUI  serial={serial or 'default'}  interval={interval_s:.2f}s"
    sys.stdout.write("\x1b[H\x1b[2J")
    sys.stdout.write(header[:width] + "\n")
    sys.stdout.write("=" * min(width, len(header)) + "\n")
    if error:
        sys.stdout.write(f"ERROR\n{error}\n")
    else:
        sys.stdout.write(body or "state=empty\n")
    sys.stdout.write("\nCtrl-C 退出\n")
    sys.stdout.flush()


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="通过 adb 在终端里实时查看抖音播放调试快照。")
    parser.add_argument("--serial", help="adb 设备序列号")
    parser.add_argument("--interval", type=float, default=0.5, help="轮询间隔秒数，默认 0.5")
    parser.add_argument("--once", action="store_true", help="只抓取一次，不进入 TUI 循环")
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    if args.once:
        body, error = fetch_debug_text(args.serial)
        if error:
            print(error, file=sys.stderr)
            return 1
        print(body)
        return 0

    clear_screen()
    try:
        while True:
            body, error = fetch_debug_text(args.serial)
            render_frame(body=body, error=error, serial=args.serial, interval_s=args.interval)
            time.sleep(max(0.1, args.interval))
    except KeyboardInterrupt:
        return 0
    finally:
        restore_screen()


if __name__ == "__main__":
    raise SystemExit(main())

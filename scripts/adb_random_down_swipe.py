#!/usr/bin/env python3
import argparse
import datetime as dt
import random
import re
import subprocess
import sys
import time
from dataclasses import dataclass

APP_LOG_TAG = "DouyinImmersive"
PAGE_SETTLED_RE = re.compile(
    r"^(?P<date>\d\d-\d\d) (?P<time>\d\d:\d\d:\d\d\.\d{3}).*"
    r"DouyinImmersive.*TEST_EVENT page_settled awemeId=(?P<aweme>\d+) page=(?P<page>\d+)"
)
PLAYBACK_STARTED_RE = re.compile(
    r"^(?P<date>\d\d-\d\d) (?P<time>\d\d:\d\d:\d\d\.\d{3}).*"
    r"DouyinImmersive.*TEST_EVENT playback_started awemeId=(?P<aweme>\d+) "
    r"mode=(?P<mode>[\w_]+) slot=(?P<slot>\w+)"
)


@dataclass(frozen=True)
class SwipeConfig:
    x: int
    start_y: int
    end_y: int
    duration_ms: int


@dataclass(frozen=True)
class SwipeEvent:
    index: int
    wait_s: float
    at: dt.datetime


@dataclass(frozen=True)
class PageSettledEvent:
    aweme_id: str
    page: int
    at: dt.datetime


@dataclass(frozen=True)
class PlaybackStartedEvent:
    aweme_id: str
    mode: str
    slot: str
    at: dt.datetime


@dataclass(frozen=True)
class LoadingSample:
    swipe: SwipeEvent
    settled: PageSettledEvent | None
    initial_start: PlaybackStartedEvent | None
    resolved_start: PlaybackStartedEvent | None


def run(cmd: list[str], check: bool = True) -> subprocess.CompletedProcess[str]:
    completed = subprocess.run(cmd, check=check, capture_output=True, text=True)
    return completed


def adb(args: list[str], serial: str | None = None, check: bool = True) -> subprocess.CompletedProcess[str]:
    cmd = ["adb"]
    if serial:
        cmd += ["-s", serial]
    cmd += args
    return run(cmd, check=check)


def adb_shell(args: list[str], serial: str | None = None, check: bool = True) -> str:
    return adb(["shell", *args], serial=serial, check=check).stdout


def parse_ts(date_str: str, time_str: str) -> dt.datetime:
    year = dt.datetime.now().year
    return dt.datetime.strptime(f"{year}-{date_str} {time_str}", "%Y-%m-%d %H:%M:%S.%f")


def device_now(serial: str | None) -> dt.datetime:
    raw = adb_shell(["date", "+%m-%dT%H:%M:%S.%3N"], serial=serial).strip()
    year = dt.datetime.now().year
    return dt.datetime.strptime(f"{year}-{raw}", "%Y-%m-%dT%H:%M:%S.%f")


def get_display_size(serial: str | None) -> tuple[int, int]:
    raw = adb_shell(["wm", "size"], serial=serial)
    for prefix in ("Override size:", "Physical size:"):
        if prefix in raw:
            size = raw.split(prefix, 1)[1].strip().splitlines()[0]
            width_str, height_str = size.split("x", 1)
            return int(width_str), int(height_str)
    raise RuntimeError(f"无法解析屏幕尺寸: {raw.strip()}")


def build_default_swipe_config(width: int, height: int, duration_ms: int) -> SwipeConfig:
    x = max(1, width // 2)
    start_y = max(1, int(height * 0.72))
    end_y = max(1, int(height * 0.28))
    return SwipeConfig(
        x=x,
        start_y=start_y,
        end_y=end_y,
        duration_ms=duration_ms,
    )


def perform_swipe(config: SwipeConfig, serial: str | None) -> None:
    adb_shell(
        [
            "input",
            "swipe",
            str(config.x),
            str(config.start_y),
            str(config.x),
            str(config.end_y),
            str(config.duration_ms),
        ],
        serial=serial,
    )


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="使用 adb 在设备上按随机间隔执行向上滑动手势，并可统计抖音页加载时长。"
    )
    parser.add_argument("--serial", help="adb 设备序列号")
    parser.add_argument("--count", type=int, default=100, help="下滑次数，默认 100")
    parser.add_argument("--min-wait", type=float, default=1.0, help="两次下滑之间的最小等待秒数，默认 1")
    parser.add_argument("--max-wait", type=float, default=10.0, help="两次下滑之间的最大等待秒数，默认 20")
    parser.add_argument("--duration-ms", type=int, default=180, help="单次 swipe 持续时间，默认 180ms")
    parser.add_argument("--x", type=int, help="手势横坐标，默认取屏幕中点")
    parser.add_argument("--start-y", type=int, help="起点纵坐标，默认取屏幕高度的 72%%")
    parser.add_argument("--end-y", type=int, help="终点纵坐标，默认取屏幕高度的 28%%")
    parser.add_argument("--seed", type=int, help="随机种子，便于复现")
    parser.add_argument("--settle-seconds", type=float, default=5.0, help="最后一次 swipe 后额外等待多少秒收集加载日志，默认 5")
    parser.add_argument("--record-douyin-loading", action=argparse.BooleanOptionalAction, default=True, help="是否读取 DouyinImmersive 日志并汇总是否刷到 loading 视频，默认开启")
    parser.add_argument("--dry-run", action="store_true", help="只打印计划，不真正执行 swipe")
    return parser.parse_args()


def read_douyin_logcat(serial: str | None) -> str:
    return adb(
        ["logcat", "-d", "-v", "time", f"{APP_LOG_TAG}:D", "*:S"],
        serial=serial,
    ).stdout


def collect_douyin_loading_events(log_text: str) -> tuple[list[PageSettledEvent], list[PlaybackStartedEvent]]:
    settled: list[PageSettledEvent] = []
    started: list[PlaybackStartedEvent] = []
    for line in log_text.splitlines():
        settled_match = PAGE_SETTLED_RE.search(line)
        if settled_match:
            settled.append(
                PageSettledEvent(
                    aweme_id=settled_match.group("aweme"),
                    page=int(settled_match.group("page")),
                    at=parse_ts(settled_match.group("date"), settled_match.group("time")),
                )
            )
            continue
        started_match = PLAYBACK_STARTED_RE.search(line)
        if started_match:
            started.append(
                PlaybackStartedEvent(
                    aweme_id=started_match.group("aweme"),
                    mode=started_match.group("mode"),
                    slot=started_match.group("slot"),
                    at=parse_ts(started_match.group("date"), started_match.group("time")),
                )
            )
    return settled, started


def match_loading_samples(
    swipes: list[SwipeEvent],
    settled_events: list[PageSettledEvent],
    playback_started_events: list[PlaybackStartedEvent],
) -> list[LoadingSample]:
    samples: list[LoadingSample] = []
    settle_cursor = 0
    for index, swipe in enumerate(swipes):
        next_swipe_at = swipes[index + 1].at if index + 1 < len(swipes) else None
        matched_settle: PageSettledEvent | None = None
        search_floor = swipe.at - dt.timedelta(milliseconds=250)
        while settle_cursor < len(settled_events):
            candidate = settled_events[settle_cursor]
            if candidate.at < search_floor:
                settle_cursor += 1
                continue
            if next_swipe_at is not None and candidate.at >= next_swipe_at:
                break
            matched_settle = candidate
            settle_cursor += 1
            break

        if matched_settle is None:
            samples.append(LoadingSample(swipe=swipe, settled=None, initial_start=None, resolved_start=None))
            continue

        matching_starts = [
            event for event in playback_started_events
            if (
                event.aweme_id == matched_settle.aweme_id and
                event.at >= matched_settle.at - dt.timedelta(milliseconds=250) and
                (next_swipe_at is None or event.at < next_swipe_at)
            )
        ]
        initial_start = matching_starts[0] if matching_starts else None
        resolved_start = next(
            (event for event in matching_starts if event.mode != "settled_buffering"),
            None,
        )
        samples.append(
            LoadingSample(
                swipe=swipe,
                settled=matched_settle,
                initial_start=initial_start,
                resolved_start=resolved_start,
            )
        )
    return samples


def print_loading_summary(samples: list[LoadingSample]) -> None:
    print("\nloading_summary")
    print("index\taweme_id\tpage\tinitial_mode\tloading_on_arrival\tloading_ms\tresolved_mode")
    loading_hits = 0
    unresolved_hits = 0
    for sample in samples:
        if sample.settled is None:
            print(f"{sample.swipe.index}\t-\t-\t-\tunknown\t-\t-")
            continue
        initial_mode = sample.initial_start.mode if sample.initial_start is not None else "missing"
        loading_status = "unknown" if sample.initial_start is None else (
            "yes" if initial_mode == "settled_buffering" else "no"
        )
        loading_ms: str
        resolved_mode: str
        if loading_status == "yes":
            loading_hits += 1
            if sample.resolved_start is None:
                unresolved_hits += 1
                loading_ms = "pending"
                resolved_mode = "-"
            else:
                loading_delta_ms = max(
                    0,
                    int((sample.resolved_start.at - sample.settled.at).total_seconds() * 1000),
                )
                loading_ms = str(loading_delta_ms)
                resolved_mode = sample.resolved_start.mode
        else:
            loading_ms = "0" if loading_status == "no" else "-"
            resolved_mode = sample.resolved_start.mode if sample.resolved_start is not None else "-"
        print(
            f"{sample.swipe.index}\t{sample.settled.aweme_id}\t{sample.settled.page}\t{initial_mode}\t"
            f"{loading_status}\t{loading_ms}\t{resolved_mode}"
        )
    print(
        f"\nloading_hits={loading_hits}/{len(samples)} unresolved_loading_hits={unresolved_hits}"
    )


def main() -> int:
    args = parse_args()
    if args.count <= 0:
        print("--count 必须大于 0", file=sys.stderr)
        return 2
    if args.min_wait < 0 or args.max_wait < 0:
        print("等待时间不能为负数", file=sys.stderr)
        return 2
    if args.min_wait > args.max_wait:
        print("--min-wait 不能大于 --max-wait", file=sys.stderr)
        return 2
    if args.duration_ms <= 0:
        print("--duration-ms 必须大于 0", file=sys.stderr)
        return 2

    if args.seed is not None:
        random.seed(args.seed)

    width, height = get_display_size(args.serial)
    default_config = build_default_swipe_config(width, height, args.duration_ms)
    swipe_config = SwipeConfig(
        x=args.x if args.x is not None else default_config.x,
        start_y=args.start_y if args.start_y is not None else default_config.start_y,
        end_y=args.end_y if args.end_y is not None else default_config.end_y,
        duration_ms=args.duration_ms,
    )

    print(
        f"display={width}x{height} swipe=({swipe_config.x},{swipe_config.start_y})->"
        f"({swipe_config.x},{swipe_config.end_y}) durationMs={swipe_config.duration_ms}"
    )

    swipe_events: list[SwipeEvent] = []
    if args.record_douyin_loading and not args.dry_run:
        adb(["logcat", "-c"], serial=args.serial)

    for index in range(1, args.count + 1):
        wait_s = random.uniform(args.min_wait, args.max_wait)
        print(f"[{index}/{args.count}] wait={wait_s:.2f}s then swipe up")
        if not args.dry_run:
            time.sleep(wait_s)
            swipe_events.append(
                SwipeEvent(
                    index=index,
                    wait_s=wait_s,
                    at=device_now(args.serial),
                )
            )
            perform_swipe(swipe_config, args.serial)

    if args.record_douyin_loading and not args.dry_run:
        time.sleep(max(0.0, args.settle_seconds))
        settled_events, playback_started_events = collect_douyin_loading_events(
            read_douyin_logcat(args.serial)
        )
        print_loading_summary(
            match_loading_samples(
                swipes=swipe_events,
                settled_events=settled_events,
                playback_started_events=playback_started_events,
            )
        )

    return 0


if __name__ == "__main__":
    raise SystemExit(main())

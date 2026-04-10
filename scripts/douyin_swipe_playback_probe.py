#!/usr/bin/env python3
import argparse
import datetime as dt
import re
import subprocess
import sys
import time
from dataclasses import dataclass


APP_TAG = "DouyinImmersive"
APP_PACKAGE = "com.lightningstudio.watchrss"
DEBUG_ACTIVITY = "com.lightningstudio.watchrss/.DouyinDebugEntryActivity"
MAIN_ACTIVITY = "com.lightningstudio.watchrss/.MainActivity"
DEBUG_RECEIVER = "com.lightningstudio.watchrss/com.lightningstudio.watchrss.debug.DouyinPlaybackDebugReceiver"
DEBUG_ACTION = "com.lightningstudio.watchrss.debug.action.OPEN_DOUYIN"
ADVANCE_ACTION = "com.lightningstudio.watchrss.debug.action.ADVANCE_TO_NEXT_DOUYIN_VIDEO"
CONTEXT_ACTION = "com.lightningstudio.watchrss.debug.action.REPORT_DOUYIN_PLAYBACK_CONTEXT"
LOGCAT_FILTERS = [
    "DouyinImmersive:D",
    "DouyinPreviewCache:D",
    "DouyinPreviewDataSource:D",
    "DouyinPlaybackDebug:D",
    "*:S",
]

START_RE = re.compile(
    r"^(?P<date>\d\d-\d\d) (?P<time>\d\d:\d\d:\d\d\.\d{3}).*"
    r"DouyinImmersive.*TEST_EVENT playback_started awemeId=(?P<aweme>\d+) "
    r"mode=(?P<mode>[\w_]+) slot=(?P<slot>\w+)"
)
ADVANCE_RE = re.compile(
    r"^(?P<date>\d\d-\d\d) (?P<time>\d\d:\d\d:\d\d\.\d{3}).*"
    r"DouyinImmersive.*TEST_EVENT debug_advance targetPage=(?P<target>\d+) currentPage=(?P<current>\d+)"
)
SETTLED_RE = re.compile(
    r"^(?P<date>\d\d-\d\d) (?P<time>\d\d:\d\d:\d\d\.\d{3}).*"
    r"DouyinImmersive.*TEST_EVENT page_settled awemeId=(?P<aweme>\d+) page=(?P<page>\d+)"
)
FAIL_RE = re.compile(
    r"^(?P<date>\d\d-\d\d) (?P<time>\d\d:\d\d:\d\d\.\d{3}).*"
    r"DouyinImmersive.*TEST_EVENT playback_failed awemeId=(?P<aweme>\d+) "
    r"slot=(?P<slot>\w+) retryCount=(?P<retry>\d+)"
)
ERROR_RE = re.compile(r"DouyinImmersive.*playback error|DouyinPreviewCache.*prefetch failed")
CONTEXT_DATA_RE = re.compile(
    r'data="activeAwemeId=(?P<active>[^;"]*);nextAwemeId=(?P<next>[^;"]*);inVideoFlow=(?P<flow>true|false)"'
)


@dataclass
class SwipeEvent:
    index: int
    at: dt.datetime


@dataclass
class StartEvent:
    aweme_id: str
    mode: str
    slot: str
    at: dt.datetime


@dataclass
class AdvanceEvent:
    target_page: int
    current_page: int
    at: dt.datetime


@dataclass
class SettledEvent:
    aweme_id: str
    page: int
    at: dt.datetime


@dataclass
class FailEvent:
    aweme_id: str
    slot: str
    retry_count: int
    at: dt.datetime


@dataclass
class StepResult:
    index: int
    expected_aweme_id: str
    start: StartEvent | None
    failure: FailEvent | None
    delta_s: float | None


def run(cmd: list[str], check: bool = True) -> subprocess.CompletedProcess[str]:
    completed = subprocess.run(cmd, check=check, capture_output=True)
    completed.stdout = completed.stdout.decode("utf-8", "replace")
    completed.stderr = completed.stderr.decode("utf-8", "replace")
    return completed  # type: ignore[return-value]


def adb(*args: str, check: bool = True) -> str:
    completed = run(["adb", *args], check=check)
    return completed.stdout


def adb_shell(*args: str, check: bool = True) -> str:
    return adb("shell", *args, check=check)


def parse_ts(date_str: str, time_str: str) -> dt.datetime:
    year = dt.datetime.now().year
    return dt.datetime.strptime(f"{year}-{date_str} {time_str}", "%Y-%m-%d %H:%M:%S.%f")


def parse_device_now(raw: str) -> dt.datetime:
    year = dt.datetime.now().year
    return dt.datetime.strptime(f"{year}-{raw.strip()}", "%Y-%m-%dT%H:%M:%S.%f")


def query_playback_context() -> tuple[str | None, str | None, bool]:
    raw = adb_shell(
        "am",
        "broadcast",
        "-n",
        DEBUG_RECEIVER,
        "-a",
        CONTEXT_ACTION,
        "--receiver-foreground",
    )
    match = CONTEXT_DATA_RE.search(raw)
    if match is None:
        return None, None, False
    active_aweme = match.group("active").strip() or None
    next_aweme = match.group("next").strip() or None
    in_video_flow = match.group("flow") == "true"
    return active_aweme, next_aweme, in_video_flow


def read_filtered_logcat() -> str:
    return adb("logcat", "-d", "-v", "time", *LOGCAT_FILTERS)


def get_display_size() -> tuple[int, int]:
    raw = adb_shell("wm", "size")
    match = re.search(r"Override size:\s*(\d+)x(\d+)", raw)
    if match is None:
        match = re.search(r"Physical size:\s*(\d+)x(\d+)", raw)
    if match is None:
        raise RuntimeError(f"unable to parse display size from: {raw.strip()}")
    return int(match.group(1)), int(match.group(2))


def send_swipe_gesture() -> str:
    width, height = get_display_size()
    x = max(1, width // 2)
    start_y = max(1, int(height * 0.72))
    end_y = max(1, int(height * 0.28))
    duration_ms = 180
    return adb_shell(
        "input",
        "swipe",
        str(x),
        str(start_y),
        str(x),
        str(end_y),
        str(duration_ms),
    )


def send_advance_broadcast() -> str:
    return adb_shell(
        "am",
        "broadcast",
        "-n",
        DEBUG_RECEIVER,
        "-a",
        ADVANCE_ACTION,
        "--receiver-foreground",
    )


def wait_for_next_ready(timeout_s: float, poll_interval_s: float = 0.5) -> tuple[str | None, str]:
    deadline = time.monotonic() + timeout_s
    last_seen: tuple[str | None, str | None, bool] = (None, None, False)
    while time.monotonic() < deadline:
        active_aweme, next_aweme, in_video_flow = query_playback_context()
        last_seen = (active_aweme, next_aweme, in_video_flow)
        if in_video_flow and next_aweme:
            return active_aweme, next_aweme
        time.sleep(poll_interval_s)
    raise TimeoutError(
        "timed out waiting for douyin active/next context after "
        f"{timeout_s:.1f}s; last_seen activeAwemeId={last_seen[0]} "
        f"nextAwemeId={last_seen[1]} inVideoFlow={last_seen[2]}"
    )


def wait_for_active_aweme(
    expected_aweme: str,
    timeout_s: float,
    poll_interval_s: float = 0.5,
) -> tuple[str, str | None]:
    deadline = time.monotonic() + timeout_s
    last_seen: tuple[str | None, str |None, bool] = (None, None, False)
    while time.monotonic() < deadline:
        active_aweme, next_aweme, in_video_flow = query_playback_context()
        last_seen = (active_aweme, next_aweme, in_video_flow)
        if active_aweme == expected_aweme:
            return active_aweme, next_aweme
        time.sleep(poll_interval_s)
    raise TimeoutError(
        "timed out waiting for douyin active aweme after "
        f"{timeout_s:.1f}s; expected={expected_aweme} "
        f"last_seen activeAwemeId={last_seen[0]} nextAwemeId={last_seen[1]} "
        f"inVideoFlow={last_seen[2]}"
    )


def wait_for_playback_started(
    expected_aweme: str,
    earliest_at: dt.datetime,
    timeout_s: float,
    poll_interval_s: float = 0.5,
) -> tuple[StartEvent | None, FailEvent | None]:
    deadline = time.monotonic() + timeout_s
    search_floor = earliest_at - dt.timedelta(seconds=1)
    while time.monotonic() < deadline:
        log_text = read_filtered_logcat()
        _, starts, _, failures, _ = collect_events(log_text)
        matched_start = next(
            (
                event for event in starts
                if event.aweme_id == expected_aweme and event.at >= search_floor
            ),
            None,
        )
        if matched_start is not None:
            return matched_start, None
        matched_failure = next(
            (
                event for event in failures
                if event.aweme_id == expected_aweme and event.at >= search_floor
            ),
            None,
        )
        if matched_failure is not None:
            return None, matched_failure
        time.sleep(poll_interval_s)
    return None, None


def collect_events(log_text: str) -> tuple[list[AdvanceEvent], list[StartEvent], list[SettledEvent], list[FailEvent], list[str]]:
    advances: list[AdvanceEvent] = []
    starts: list[StartEvent] = []
    settled: list[SettledEvent] = []
    failures: list[FailEvent] = []
    errors: list[str] = []
    for line in log_text.splitlines():
        advance_match = ADVANCE_RE.search(line)
        if advance_match:
            advances.append(
                AdvanceEvent(
                    target_page=int(advance_match.group("target")),
                    current_page=int(advance_match.group("current")),
                    at=parse_ts(advance_match.group("date"), advance_match.group("time")),
                )
            )
            continue
        start_match = START_RE.search(line)
        if start_match:
            starts.append(
                StartEvent(
                    aweme_id=start_match.group("aweme"),
                    mode=start_match.group("mode"),
                    slot=start_match.group("slot"),
                    at=parse_ts(start_match.group("date"), start_match.group("time")),
                )
            )
            continue
        settled_match = SETTLED_RE.search(line)
        if settled_match:
            settled.append(
                SettledEvent(
                    aweme_id=settled_match.group("aweme"),
                    page=int(settled_match.group("page")),
                    at=parse_ts(settled_match.group("date"), settled_match.group("time")),
                )
            )
            continue
        fail_match = FAIL_RE.search(line)
        if fail_match:
            failures.append(
                FailEvent(
                    aweme_id=fail_match.group("aweme"),
                    slot=fail_match.group("slot"),
                    retry_count=int(fail_match.group("retry")),
                    at=parse_ts(fail_match.group("date"), fail_match.group("time")),
                )
            )
            continue
        if ERROR_RE.search(line):
            errors.append(line)
    return advances, starts, settled, failures, errors


def pair_events(
    swipes: list[SwipeEvent],
    advances: list[AdvanceEvent],
    settled: list[SettledEvent],
    starts: list[StartEvent],
    failures: list[FailEvent],
) -> list[tuple[SwipeEvent, AdvanceEvent | None, SettledEvent | None, StartEvent | None, FailEvent | None, float | None]]:
    results: list[tuple[SwipeEvent, AdvanceEvent | None, SettledEvent | None, StartEvent | None, FailEvent | None, float | None]] = []
    advance_cursor = 0
    settle_cursor = 0
    start_cursor = 0
    fail_cursor = 0
    for idx, swipe in enumerate(swipes):
        matched_advance: AdvanceEvent | None = None
        matched_settle: SettledEvent | None = None
        matched: StartEvent | None = None
        failed: FailEvent | None = None
        while advance_cursor < len(advances):
            candidate = advances[advance_cursor]
            matched_advance = candidate
            advance_cursor += 1
            break
        if matched_advance is None:
            results.append((swipe, None, None, None, None, None))
            continue
        settle_floor_at = matched_advance.at - dt.timedelta(seconds=0.25)
        while settle_cursor < len(settled):
            candidate = settled[settle_cursor]
            if candidate.at < settle_floor_at:
                settle_cursor += 1
                continue
            matched_settle = candidate
            settle_cursor += 1
            break
        search_floor_at = matched_advance.at - dt.timedelta(seconds=0.25)
        while start_cursor < len(starts):
            candidate = starts[start_cursor]
            if candidate.at < search_floor_at:
                start_cursor += 1
                continue
            matched = candidate
            start_cursor += 1
            break
        while fail_cursor < len(failures):
            candidate = failures[fail_cursor]
            if candidate.at < search_floor_at:
                fail_cursor += 1
                continue
            failed = candidate
            fail_cursor += 1
            break
        delta = None if matched is None else max(0.0, (matched.at - matched_advance.at).total_seconds())
        results.append((swipe, matched_advance, matched_settle, matched, failed, delta))
    return results


def print_summary(
    results: list[tuple[SwipeEvent, AdvanceEvent | None, SettledEvent | None, StartEvent | None, FailEvent | None, float | None]],
    threshold_s: float,
) -> int:
    failures = 0
    print("index\tdelta_s\taweme_id\tmode\tslot\tadvance_page\tsettled_page\tresult")
    for swipe, advance, settled, start, failed, delta in results:
        if start is None or delta is None or settled is None:
            failures += 1
            if failed is not None:
                print(
                    f"{swipe.index}\tFAILED\t{failed.aweme_id}\tretry_{failed.retry_count}\t{failed.slot}\t{advance.target_page if advance else '-'}\t{settled.page if settled else '-'}\tFAIL"
                )
            else:
                print(f"{swipe.index}\tMISSING\t-\t-\t-\t{advance.target_page if advance else '-'}\t{settled.page if settled else '-'}\tFAIL")
            continue
        status = "PASS" if delta <= threshold_s else "FAIL"
        if status == "FAIL":
            failures += 1
        print(
            f"{swipe.index}\t{delta:.3f}\t{start.aweme_id}\t{start.mode}\t{start.slot}\t{advance.target_page if advance else '-'}\t{settled.page}\t{status}"
        )
    return failures


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--steps", type=int, default=15)
    parser.add_argument("--interval-seconds", type=float, default=15.0)
    parser.add_argument("--threshold-seconds", type=float, default=3.0)
    parser.add_argument("--warmup-seconds", type=float, default=8.0)
    parser.add_argument("--settle-seconds", type=float, default=5.0)
    parser.add_argument("--context-timeout-seconds", type=float, default=30.0)
    args = parser.parse_args()

    adb_shell("am", "force-stop", APP_PACKAGE, check=False)
    adb("logcat", "-c")
    print("starting app")
    adb_shell("am", "start", "-n", MAIN_ACTIVITY)
    time.sleep(3)
    adb_shell(
        "am",
        "start",
        "-n",
        DEBUG_ACTIVITY,
        "-a",
        DEBUG_ACTION,
        "--ez",
        "watchrss.debug.auto_enter_douyin_flow",
        "true",
    )
    time.sleep(args.warmup_seconds)
    try:
        active_aweme, next_aweme = wait_for_next_ready(args.context_timeout_seconds)
    except TimeoutError:
        bootstrap_active, bootstrap_next, bootstrap_in_flow = query_playback_context()
        if bootstrap_next and not bootstrap_in_flow:
            bootstrap_result = send_advance_broadcast().strip().replace("\n", " | ")
            print(
                "bootstrap advance "
                f"activeAwemeId={bootstrap_active} "
                f"nextAwemeId={bootstrap_next} "
                f"result={bootstrap_result}"
            )
            sys.stdout.flush()
            active_aweme, next_aweme = wait_for_next_ready(args.context_timeout_seconds)
        else:
            raise
    print(f"ready context activeAwemeId={active_aweme} nextAwemeId={next_aweme}")
    adb("logcat", "-c")

    print("running swipe sequence")
    results: list[StepResult] = []
    for index in range(1, args.steps + 1):
        context_active, context_next = wait_for_next_ready(args.context_timeout_seconds)
        host_now = dt.datetime.now().strftime("%H:%M:%S.%f")[:-3]
        swipe_at = parse_device_now(adb_shell("date", "+%m-%dT%H:%M:%S.%3N"))
        swipe_result = send_advance_broadcast().strip().replace("\n", " | ")
        print(
            f"step {index} host_send_at={host_now} "
            f"device_send_at={swipe_at.strftime('%H:%M:%S.%f')[:-3]} "
            f"context_active={context_active} context_next={context_next} "
            f"advance={swipe_result}"
        )
        sys.stdout.flush()
        activated_aweme, activated_next = wait_for_active_aweme(
            expected_aweme=context_next,
            timeout_s=args.context_timeout_seconds,
        )
        print(
            f"step {index} activated_aweme={activated_aweme} "
            f"activated_next={activated_next}"
        )
        sys.stdout.flush()
        start_event, fail_event = wait_for_playback_started(
            expected_aweme=context_next,
            earliest_at=swipe_at,
            timeout_s=args.context_timeout_seconds,
        )
        delta_s = None if start_event is None else max(0.0, (start_event.at - swipe_at).total_seconds())
        results.append(
            StepResult(
                index=index,
                expected_aweme_id=context_next,
                start=start_event,
                failure=fail_event,
                delta_s=delta_s,
            )
        )
        time.sleep(args.interval_seconds)

    time.sleep(args.settle_seconds)
    log_text = read_filtered_logcat()
    _, _, _, _, errors = collect_events(log_text)

    failures = 0
    print("index\tdelta_s\taweme_id\tmode\tslot\tresult")
    for result in results:
        if result.start is None or result.delta_s is None:
            failures += 1
            if result.failure is not None:
                print(
                    f"{result.index}\tFAILED\t{result.expected_aweme_id}\tretry_{result.failure.retry_count}\t{result.failure.slot}\tFAIL"
                )
            else:
                print(
                    f"{result.index}\tMISSING\t{result.expected_aweme_id}\t-\t-\tFAIL"
                )
            continue
        status = "PASS" if result.delta_s <= args.threshold_seconds else "FAIL"
        if status == "FAIL":
            failures += 1
        print(
            f"{result.index}\t{result.delta_s:.3f}\t{result.start.aweme_id}\t{result.start.mode}\t{result.start.slot}\t{status}"
        )

    if errors:
        print("\nrelevant_errors")
        for line in errors[:20]:
            print(line)

    if failures > 0:
        print(
            f"\nprobe failed: {failures} swipe(s) exceeded {args.threshold_seconds:.1f}s or missed playback_started"
        )
        return 1

    print(f"\nprobe passed: all {len(results)} swipes started within {args.threshold_seconds:.1f}s")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

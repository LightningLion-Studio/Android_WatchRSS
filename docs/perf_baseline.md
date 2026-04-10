# Performance Baseline

## Environment
- Device: Pixel 8 - 16 (emulator)
- Build: debug
- Scenario: perf_large_list / perf_large_article
- Date: 2026-01-26

## Metrics (JankStats)
| Scenario | Frames | Jank | Jank % | Avg ms | Max ms | Notes |
| --- | --- | --- | --- | --- | --- | --- |
| Large list | 200 | 2 | 1.00 | 7.85 | 47.04 | screen=PerfLargeListActivity |
| Large article | 216 | 1 | 0.46 | 8.70 | 57.14 | screen=PerfLargeArticleActivity |

## Capture Checklist
- List screenshot: `scripts/perf_replay.sh` + screenshot
- Article screenshot: `scripts/perf_replay.sh` + screenshot
- Logcat: `adb logcat -d | grep "perf"`

Notes: list/article screenshots captured on 2026-01-26 via Android MCP.

## Device Perf Triage
- Clear runtime logs before sampling: `adb logcat -c`
- Launch the app: `adb shell am start -n com.lightningstudio.watchrss/.MainActivity`
- Reset frame stats: `adb shell dumpsys gfxinfo com.lightningstudio.watchrss reset`
- Reproduce the target scroll path, then collect:
  - `adb shell dumpsys gfxinfo com.lightningstudio.watchrss`
  - `adb logcat -d -v time | rg 'PerfTrace'`

## Home Cold Start Triage
- Scenario tag: `home_cold_start`
- Clear runtime logs before sampling: `adb logcat -c`
- Reset frame stats: `adb shell dumpsys gfxinfo com.lightningstudio.watchrss reset`
- Cold launch the app: `adb shell am start -S -W -n com.lightningstudio.watchrss/.MainActivity`
- Wait until the home list is visible, then collect:
  - `adb shell dumpsys gfxinfo com.lightningstudio.watchrss`
  - `adb logcat -d -v time | rg 'PerfTrace' | rg 'home_cold_start|cat=frame'`

## PerfTrace Filters
- Unified logcat tag: `PerfTrace`
- Categories:
  - `cat=feed`: ViewModel/list state and load-more/original-content triggers
  - `cat=repo`: Room emissions, preview scheduling, original-content writes
  - `cat=img`: thumbnail cache/decode/fetch path
  - `cat=frame`: slow-frame metrics emitted by `PerformanceMonitor`
- Useful filters:
  - `adb logcat -d -v time | rg 'PerfTrace' | rg 'cat=repo'`
  - `adb logcat -d -v time | rg 'PerfTrace' | rg 'skip-noop-write|skip-same-attempt'`
  - `adb logcat -d -v time | rg 'PerfTrace' | rg 'cat=feed|cat=frame'`

## DebugLogBuffer
- Dump in-app debug buffer:
  - `adb shell dumpsys activity provider com.lightningstudio.watchrss.debug.DebugLogProvider`
- Clear buffer:
  - `adb shell dumpsys activity provider com.lightningstudio.watchrss.debug.DebugLogProvider --clear`

## Douyin Playback Debug TUI
- 用途：在宿主机终端实时查看抖音沉浸式播放调试状态，替代手表端半透明 overlay。
- 当前输出包含：
  - 当前页 / settled 页 / 是否在滚动
  - 正在下载的视频及其缓存进度
  - RAM 预览缓存槽中的字节流
  - `Primary` / `Secondary` 两个播放器槽当前绑定的视频、codec、来源、缓存字节和状态
- 单次抓取：
  - `python3 scripts/douyin_debug_tui.py --once`
- 持续刷新 TUI：
  - `python3 scripts/douyin_debug_tui.py`
- 可选参数：
  - `python3 scripts/douyin_debug_tui.py --serial <adb-serial>`
  - `python3 scripts/douyin_debug_tui.py --interval 0.25`
- 底层 provider 导出命令：
  - `adb shell dumpsys activity provider com.lightningstudio.watchrss/.debug.DebugLogProvider --douyin-playback`

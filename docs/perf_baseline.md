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

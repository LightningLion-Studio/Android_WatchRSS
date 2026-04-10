package com.lightningstudio.watchrss.sdk.douyin

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DouyinUnifiedParserTest {
    private val parser = DouyinUnifiedParser()

    @Test
    fun parse_detailFallsBackToVariantsWhenPlayAddrUrlListIsEmpty() {
        val content = parser.parse(
            """
            {
              "aweme_detail": {
                "aweme_id": "detail-1",
                "desc": "detail fallback",
                "aweme_type": 0,
                "author": {
                  "nickname": "tester"
                },
                "statistics": {
                  "digg_count": 123
                },
                "video": {
                  "play_addr": {
                    "url_list": []
                  },
                  "cover": {
                    "url_list": [
                      "https://example.com/cover.jpg"
                    ]
                  },
                  "bit_rate": [
                    {
                      "play_addr": {
                        "url_list": [
                          "https://example.com/fallback-variant.mp4"
                        ],
                        "width": 720,
                        "height": 1280
                      },
                      "video_extra": {
                        "definition": "540p"
                      },
                      "codec_type": "hvc1"
                    }
                  ]
                }
              }
            }
            """.trimIndent()
        )

        val video = content as DouyinContent.Video
        assertEquals("detail-1", video.awemeId)
        assertEquals("https://example.com/fallback-variant.mp4", video.playUrl)
        assertEquals("https://example.com/cover.jpg", video.coverUrl)
        assertEquals(1, video.variants.size)
        assertEquals(DouyinVideoCodec.H265, video.variants.first().codec)
    }

    @Test
    fun parse_feedPageRecognizesAvc1AndHvc1CodecTypes() {
        val page = parser.parseFeedPage(
            """
            {
              "aweme_list": [
                {
                  "aweme_id": "feed-1",
                  "desc": "avc variant",
                  "create_time": 1,
                  "author": {
                    "uid": "u1",
                    "nickname": "alice",
                    "avatar_thumb": {
                      "url_list": [
                        "https://example.com/avatar.jpg"
                      ]
                    }
                  },
                  "statistics": {
                    "digg_count": 10,
                    "comment_count": 2,
                    "share_count": 3,
                    "collect_count": 4
                  },
                  "video": {
                    "duration": 12,
                    "play_addr": {
                      "url_list": [
                        "https://example.com/original-1.mp4"
                      ]
                    },
                    "bit_rate": [
                      {
                        "play_addr": {
                          "url_list": [
                            "https://example.com/avc1.mp4"
                          ],
                          "width": 720,
                          "height": 1280
                        },
                        "video_extra": {
                          "definition": "540p"
                        },
                        "codec_type": "avc1.4d401f"
                      }
                    ]
                  }
                },
                {
                  "aweme_id": "feed-2",
                  "desc": "hvc variant",
                  "create_time": 2,
                  "author": {
                    "uid": "u2",
                    "nickname": "bob",
                    "avatar_thumb": {
                      "url_list": [
                        "https://example.com/avatar2.jpg"
                      ]
                    }
                  },
                  "statistics": {
                    "digg_count": 20,
                    "comment_count": 5,
                    "share_count": 6,
                    "collect_count": 7
                  },
                  "video": {
                    "duration": 18,
                    "play_addr": {
                      "url_list": [
                        "https://example.com/original-2.mp4"
                      ]
                    },
                    "bit_rate": [
                      {
                        "play_addr": {
                          "url_list": [
                            "https://example.com/hvc1.mp4"
                          ],
                          "width": 720,
                          "height": 1280
                        },
                        "video_extra": {
                          "definition": "540p"
                        },
                        "gear_name": "hvc1_540p"
                      }
                    ]
                  }
                }
              ],
              "max_cursor": 9,
              "has_more": 1
            }
            """.trimIndent()
        )

        assertEquals(2, page.items.size)
        assertEquals("https://example.com/original-1.mp4", page.items[0].playUrl)
        assertEquals(DouyinVideoCodec.H264, page.items[0].variants.first().codec)
        assertEquals("https://example.com/original-2.mp4", page.items[1].playUrl)
        assertEquals(DouyinVideoCodec.H265, page.items[1].variants.first().codec)
        assertEquals("9", page.nextCursor)
        assertTrue(page.hasMore)
    }
}

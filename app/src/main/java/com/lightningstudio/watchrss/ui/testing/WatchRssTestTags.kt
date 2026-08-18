package com.lightningstudio.watchrss.ui.testing

object OobeTestTags {
    const val ROOT = "oobe/root"
    const val INTRO_PAGE = "oobe/intro_page"
    const val CUSTOM_PAGE = "oobe/custom_page"
    const val CUSTOM_THEME_TOGGLE = "oobe/custom_theme_toggle"
    const val CUSTOM_FONT_VALUE = "oobe/custom_font_value"
    const val CUSTOM_MEDIA_VOLUME_CONTROL_SWITCH = "oobe/custom_media_volume_control_switch"
    const val CUSTOM_MEDIA_GUARD_SWITCH = "oobe/custom_media_guard_switch"
    const val CUSTOM_PLAYBACK_START_VOLUME_VALUE = "oobe/custom_playback_start_volume_value"
    const val INTERNET_PAGE = "oobe/internet_page"
    const val AGREEMENT_CHECKBOX = "oobe/agreement_checkbox"
    const val LEGAL_TEXT = "oobe/legal_text"
    const val NEXT_BUTTON = "oobe/next_button"
    const val CONTINUE_BUTTON = "oobe/continue_button"
    const val ERROR_TEXT = "oobe/error_text"
    const val INTERNET_STATUS_CHECKING = "oobe/internet_status_checking"
    const val INTERNET_STATUS_UNAVAILABLE = "oobe/internet_status_unavailable"
    const val INTERNET_STATUS_BLUETOOTH = "oobe/internet_status_bluetooth"
    const val INTERNET_STATUS_AVAILABLE = "oobe/internet_status_available"
    const val OFFLINE_WARNING_DIALOG = "oobe/offline_warning_dialog"
    const val OFFLINE_WARNING_CONFIRM_BUTTON = "oobe/offline_warning_confirm_button"
    const val OFFLINE_WARNING_CANCEL_BUTTON = "oobe/offline_warning_cancel_button"
}

object HomeTestTags {
    const val ROOT = "home/root"
    const val CHANNEL_LIST = "home/channel_list"
    const val PROFILE_ENTRY = "home/profile_entry"
    const val NOTES_ENTRY = "home/notes_entry"
    const val EMPTY_ENTRY = "home/empty_entry"
    const val RECOMMEND_ENTRY = "home/recommend_entry"
    const val ADD_ENTRY = "home/add_entry"
    const val BEIAN_ENTRY = "home/beian_entry"

    fun channelRow(channelId: Long): String = "home/channel_row/$channelId"

    fun channelCard(channelId: Long): String = "home/channel_card/$channelId"

    fun channelIndicator(channelId: Long): String = "home/channel_indicator/$channelId"

    fun moveTopAction(channelId: Long): String = "home/channel_move_top/$channelId"

    fun markReadAction(channelId: Long): String = "home/channel_mark_read/$channelId"
}

object DouyinChannelInfoTestTags {
    const val MARK_READ_BUTTON = "douyin/channel_info/mark_read_button"
}

object ProfileTestTags {
    const val ROOT = "profile/root"
    const val ACCOUNT_ENTRY = "profile/account_entry"
    const val FAVORITES_ENTRY = "profile/favorites_entry"
    const val WATCH_LATER_ENTRY = "profile/watch_later_entry"
    const val PHONE_CONNECTION_ENTRY = "profile/phone_connection_entry"
    const val SETTINGS_ENTRY = "profile/settings_entry"
    const val ABOUT_ENTRY = "profile/about_entry"
    const val CONTACT_DEVELOPER_ENTRY = "profile/contact_developer_entry"
    const val BEIAN_ENTRY = "profile/beian_entry"
}

object PhoneConnectionTestTags {
    const val ROOT = "phone_connection/root"
    const val BLUETOOTH_ENTRY = "phone_connection/bluetooth_entry"
    const val PURE_SOUND_ENTRY = "phone_connection/pure_sound_entry"
    const val SOUND_GUIDED_WIFI_ENTRY = "phone_connection/sound_guided_wifi_entry"
    const val MANUAL_WIFI_ENTRY = "phone_connection/manual_wifi_entry"
}

object AddRssTestTags {
    const val ROOT = "add_rss/root"
    const val URL_INPUT = "add_rss/url_input"
    const val SUBMIT_BUTTON = "add_rss/submit_button"
    const val REMOTE_INPUT_BUTTON = "add_rss/remote_input_button"
    const val LOADING_TEXT = "add_rss/loading_text"
    const val ERROR_TEXT = "add_rss/error_text"
    const val RETRY_BUTTON = "add_rss/retry_button"
    const val CANCEL_ERROR_BUTTON = "add_rss/cancel_error_button"
    const val PREVIEW_PANEL = "add_rss/preview_panel"
    const val CONFIRM_BUTTON = "add_rss/confirm_button"
    const val BACK_TO_INPUT_BUTTON = "add_rss/back_to_input_button"
    const val EXISTING_PANEL = "add_rss/existing_panel"
    const val OPEN_EXISTING_BUTTON = "add_rss/open_existing_button"
    const val QR_PANEL = "add_rss/qr_panel"
    const val QR_IMAGE = "add_rss/qr_image"
}

object SettingsTestTags {
    const val ROOT = "settings/root"
    const val ADVANCED_ENTRY = "settings/advanced_entry"
    const val CACHE_DECREASE_BUTTON = "settings/cache_decrease_button"
    const val CACHE_VALUE = "settings/cache_value"
    const val CACHE_INCREASE_BUTTON = "settings/cache_increase_button"
    const val SHARE_SWITCH = "settings/share_switch"
    const val FONT_DECREASE_BUTTON = "settings/font_decrease_button"
    const val FONT_VALUE = "settings/font_value"
    const val FONT_INCREASE_BUTTON = "settings/font_increase_button"
    const val SYNC_MEDIA_KEEP_ALIVE_SWITCH = "settings/sync_media_keep_alive_switch"
    const val MEDIA_VOLUME_CONTROL_SWITCH = "settings/media_volume_control_switch"
    const val MEDIA_VOLUME_GUARD_ROW = "settings/media_volume_guard_row"
    const val MEDIA_VOLUME_GUARD_SWITCH = "settings/media_volume_guard_switch"
    const val PLAYBACK_START_VOLUME_DECREASE_BUTTON = "settings/playback_start_volume_decrease_button"
    const val PLAYBACK_START_VOLUME_VALUE = "settings/playback_start_volume_value"
    const val PLAYBACK_START_VOLUME_INCREASE_BUTTON = "settings/playback_start_volume_increase_button"
    const val OPEN_OOBE_ENTRY = "settings/open_oobe_entry"
    const val PHONE_CONNECTION_SWITCH = "settings/phone_connection_switch"
    const val DOUYIN_COOKIE_ENTRY = "settings/douyin_cookie_entry"
    const val IMAGE_PREFETCH_DECREASE_BUTTON = "settings/image_prefetch_decrease_button"
    const val IMAGE_PREFETCH_VALUE = "settings/image_prefetch_value"
    const val IMAGE_PREFETCH_INCREASE_BUTTON = "settings/image_prefetch_increase_button"
    const val BEIAN_ENTRY = "settings/beian_entry"
    const val PHONE_REMOTE_INPUT_ENTRY = "settings/phone_remote_input_entry"
    const val PHONE_AI_CONNECTIVITY_ENTRY = "settings/phone_ai_connectivity_entry"
}

object DownloadPhoneAppTestTags {
    const val SCRIM = "download_phone_app/scrim"
    const val DIALOG = "download_phone_app/dialog"
    const val QR_IMAGE = "download_phone_app/qr_image"
    const val OPEN_BROWSER_BUTTON = "download_phone_app/open_browser_button"
    const val CLOSE_BUTTON = "download_phone_app/close_button"
}

object PhoneSyncActionsTestTags {
    const val START_SYNC_BUTTON = "phone_sync_actions/start_sync_button"
}

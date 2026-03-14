package com.lightningstudio.watchrss.ui.testing

object OobeTestTags {
    const val ROOT = "oobe/root"
    const val INTRO_PAGE = "oobe/intro_page"
    const val AGREEMENT_CHECKBOX = "oobe/agreement_checkbox"
    const val LEGAL_TEXT = "oobe/legal_text"
    const val NEXT_BUTTON = "oobe/next_button"
    const val CONTINUE_BUTTON = "oobe/continue_button"
    const val ERROR_TEXT = "oobe/error_text"
}

object HomeTestTags {
    const val ROOT = "home/root"
    const val CHANNEL_LIST = "home/channel_list"
    const val PROFILE_ENTRY = "home/profile_entry"
    const val EMPTY_ENTRY = "home/empty_entry"
    const val RECOMMEND_ENTRY = "home/recommend_entry"
    const val ADD_ENTRY = "home/add_entry"
    const val BEIAN_ENTRY = "home/beian_entry"

    fun channelRow(channelId: Long): String = "home/channel_row/$channelId"

    fun channelCard(channelId: Long): String = "home/channel_card/$channelId"

    fun moveTopAction(channelId: Long): String = "home/channel_move_top/$channelId"

    fun markReadAction(channelId: Long): String = "home/channel_mark_read/$channelId"
}

object ProfileTestTags {
    const val ROOT = "profile/root"
    const val FAVORITES_ENTRY = "profile/favorites_entry"
    const val WATCH_LATER_ENTRY = "profile/watch_later_entry"
    const val SETTINGS_ENTRY = "profile/settings_entry"
    const val ABOUT_ENTRY = "profile/about_entry"
    const val CONTACT_DEVELOPER_ENTRY = "profile/contact_developer_entry"
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
    const val CACHE_DECREASE_BUTTON = "settings/cache_decrease_button"
    const val CACHE_VALUE = "settings/cache_value"
    const val CACHE_INCREASE_BUTTON = "settings/cache_increase_button"
    const val THEME_SWITCH = "settings/theme_switch"
    const val SHARE_SWITCH = "settings/share_switch"
    const val FONT_DECREASE_BUTTON = "settings/font_decrease_button"
    const val FONT_VALUE = "settings/font_value"
    const val FONT_INCREASE_BUTTON = "settings/font_increase_button"
    const val OPEN_OOBE_ENTRY = "settings/open_oobe_entry"
    const val PHONE_CONNECTION_SWITCH = "settings/phone_connection_switch"
    const val BEIAN_ENTRY = "settings/beian_entry"
}

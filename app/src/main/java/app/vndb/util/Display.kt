package app.vndb.util

import app.vndb.data.model.NsfwPolicy
import app.vndb.data.model.TitlePreference
import app.vndb.data.model.VndbImage
import app.vndb.data.model.VisualNovel
import java.util.Locale

fun VisualNovel.displayTitle(pref: TitlePreference): String {
    val titles = this.titles
    return when (pref) {
        TitlePreference.SITE -> title
        TitlePreference.ORIGINAL ->
            titles.firstOrNull { it.main == true }?.title
                ?: alttitle
                ?: title
        TitlePreference.ROMANIZED ->
            titles.firstOrNull { it.main == true }?.latin
                ?: title
                ?: alttitle
        TitlePreference.CHINESE ->
            titles.firstOrNull { it.lang == "zh-Hans" || it.lang == "zh-Hant" || it.lang == "zh" }?.title
                ?: titles.firstOrNull { it.main == true }?.title
                ?: alttitle
                ?: title
        TitlePreference.ENGLISH ->
            titles.firstOrNull { it.lang == "en" }?.title
                ?: title
                ?: alttitle
    }.orEmpty().ifBlank { id }
}

fun VisualNovel.displaySubtitle(pref: TitlePreference): String? {
    val primary = displayTitle(pref)
    val candidates = listOfNotNull(alttitle, title)
        .filter { it.isNotBlank() && it != primary }
    return candidates.firstOrNull()
}

fun VndbImage?.visibleUrl(policy: NsfwPolicy, preferFull: Boolean = false): String? {
    if (this == null) return null
    val sexual = sexual ?: 0.0
    if (policy == NsfwPolicy.HIDE && sexual >= 1.0) return null
    return if (preferFull) url ?: thumbnail else thumbnail ?: url
}

fun formatRating(rating: Double?): String {
    if (rating == null) return "—"
    return String.format(Locale.US, "%.2f", rating / 10.0)
}

fun formatVotes(count: Int?): String {
    if (count == null) return ""
    return if (count >= 1000) String.format(Locale.US, "%.1fk", count / 1000.0) else count.toString()
}

fun formatLength(minutes: Int?, bucket: Int?): String {
    if (minutes != null && minutes > 0) {
        val hours = minutes / 60
        val mins = minutes % 60
        return if (hours > 0) "${hours}h ${mins}m" else "${mins}m"
    }
    return when (bucket) {
        1 -> "极短"
        2 -> "短"
        3 -> "中等"
        4 -> "长"
        5 -> "极长"
        else -> "未知时长"
    }
}

fun formatDevStatus(status: Int?): String = when (status) {
    0 -> "已完成"
    1 -> "开发中"
    2 -> "已取消"
    else -> "未知"
}

fun languageName(code: String): String = when (code) {
    "ja" -> "日语"
    "en" -> "英语"
    "zh-Hans", "zh" -> "简体中文"
    "zh-Hant" -> "繁体中文"
    "ko" -> "韩语"
    "ru" -> "俄语"
    "fr" -> "法语"
    "de" -> "德语"
    "es" -> "西班牙语"
    "it" -> "意大利语"
    "pt-br", "pt-pt" -> "葡萄牙语"
    "vi" -> "越南语"
    "pl" -> "波兰语"
    "hu" -> "匈牙利语"
    "cs" -> "捷克语"
    "uk" -> "乌克兰语"
    "ar" -> "阿拉伯语"
    "th" -> "泰语"
    "id" -> "印尼语"
    "tr" -> "土耳其语"
    else -> code
}

fun platformName(code: String): String = when (code) {
    "win" -> "Windows"
    "lin" -> "Linux"
    "mac" -> "macOS"
    "and" -> "Android"
    "ios" -> "iOS"
    "swi" -> "Switch"
    "ps2" -> "PS2"
    "ps3" -> "PS3"
    "ps4" -> "PS4"
    "ps5" -> "PS5"
    "psp" -> "PSP"
    "psv" -> "PS Vita"
    "xb3", "xb1", "xbo" -> "Xbox"
    "nds" -> "NDS"
    "3ds" -> "3DS"
    "wii" -> "Wii"
    "n3d" -> "3DS"
    "web" -> "Web"
    "dvd" -> "DVD"
    "oth" -> "其他"
    else -> code.uppercase(Locale.US)
}

fun staffRoleName(role: String?): String = when (role) {
    "scenario" -> "剧本"
    "chardesign" -> "人设"
    "art" -> "原画"
    "music" -> "音乐"
    "songs" -> "歌曲"
    "director" -> "监督"
    "staff" -> "职员"
    "translator" -> "翻译"
    "editor" -> "编辑"
    "qa" -> "测试"
    "vocals" -> "演唱"
    else -> role.orEmpty()
}

fun characterRoleName(role: String?): String = when (role) {
    "main" -> "主人公"
    "primary" -> "主要"
    "side" -> "配角"
    "appears" -> "出场"
    else -> role.orEmpty()
}

fun tagCategoryName(category: String?): String = when (category) {
    "cont" -> "内容"
    "ero" -> "性相关"
    "tech" -> "技术"
    else -> category.orEmpty()
}

fun producerTypeName(type: String?): String = when (type) {
    "co" -> "公司"
    "in" -> "个人"
    "ng" -> "同人社团"
    else -> type.orEmpty()
}

fun voicedName(value: Int?): String = when (value) {
    1 -> "无配音"
    2 -> "仅 H 场景"
    3 -> "部分配音"
    4 -> "全程配音"
    else -> "未知"
}

fun ulistLabelName(id: Int, fallback: String?): String = when (id) {
    1 -> "游玩中"
    2 -> "已完成"
    3 -> "搁置"
    4 -> "弃坑"
    5 -> "想玩"
    6 -> "黑名单"
    7 -> "已评分"
    else -> fallback ?: "标签 $id"
}

fun formatBirthday(parts: List<Int>): String? {
    if (parts.size < 2) return null
    val month = parts[0]
    val day = parts[1]
    if (month <= 0) return null
    return if (day > 0) "%d 月 %d 日".format(month, day) else "%d 月".format(month)
}

fun vndbSiteUrl(id: String): String = "https://vndb.org/$id"

fun stripVndbMarkup(raw: String?): String {
    if (raw.isNullOrBlank()) return ""
    return raw
        .replace(Regex("\\[url=[^\\]]+]"), "")
        .replace("[/url]", "")
        .replace(Regex("\\[/?[bius]]"), "")
        .replace(Regex("\\[spoiler]"), "（剧透）")
        .replace("[/spoiler]", "")
        .replace(Regex("\\[/?quote]"), "")
        .replace(Regex("\\[/?raw]"), "")
        .replace(Regex("\\[/?code]"), "")
        .replace(Regex("\\[/?center]"), "")
        .trim()
}

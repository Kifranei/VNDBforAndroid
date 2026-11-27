package app.vndb.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class VndbImage(
    val id: String? = null,
    val url: String? = null,
    val dims: List<Int> = emptyList(),
    val sexual: Double? = null,
    val violence: Double? = null,
    val votecount: Int? = null,
    val thumbnail: String? = null,
    @SerialName("thumbnail_dims") val thumbnailDims: List<Int> = emptyList(),
    val type: String? = null,
)

@Serializable
data class LocalizedTitle(
    val lang: String? = null,
    val title: String? = null,
    val latin: String? = null,
    val official: Boolean? = null,
    val main: Boolean? = null,
)

@Serializable
data class ExtLink(
    val url: String? = null,
    val label: String? = null,
    val name: String? = null,
    val id: String? = null,
)

@Serializable
data class VnTag(
    val id: String,
    val name: String? = null,
    val rating: Double? = null,
    val spoiler: Int? = null,
    val lie: Boolean? = null,
    val category: String? = null,
)

@Serializable
data class VnRelation(
    val id: String,
    val title: String? = null,
    val alttitle: String? = null,
    val relation: String? = null,
    @SerialName("relation_official") val relationOfficial: Boolean? = null,
    val image: VndbImage? = null,
    val rating: Double? = null,
)

@Serializable
data class VnStaff(
    val id: String,
    val aid: Int? = null,
    val name: String? = null,
    val original: String? = null,
    val role: String? = null,
    val note: String? = null,
    val eid: Int? = null,
)

@Serializable
data class VoiceActor(
    val note: String? = null,
    val staff: StaffSummary? = null,
    val character: CharacterSummary? = null,
)

@Serializable
data class StaffSummary(
    val id: String,
    val name: String? = null,
    val original: String? = null,
)

@Serializable
data class CharacterSummary(
    val id: String,
    val name: String? = null,
    val original: String? = null,
    val image: VndbImage? = null,
    val role: String? = null,
)

@Serializable
data class ProducerSummary(
    val id: String,
    val name: String? = null,
    val original: String? = null,
    val type: String? = null,
)

@Serializable
data class VisualNovel(
    val id: String = "",
    val title: String? = null,
    val alttitle: String? = null,
    val titles: List<LocalizedTitle> = emptyList(),
    val aliases: List<String> = emptyList(),
    val olang: String? = null,
    val devstatus: Int? = null,
    val released: String? = null,
    val languages: List<String> = emptyList(),
    val platforms: List<String> = emptyList(),
    val image: VndbImage? = null,
    val length: Int? = null,
    @SerialName("length_minutes") val lengthMinutes: Int? = null,
    @SerialName("length_votes") val lengthVotes: Int? = null,
    val description: String? = null,
    val average: Double? = null,
    val rating: Double? = null,
    val votecount: Int? = null,
    val screenshots: List<VndbImage> = emptyList(),
    val relations: List<VnRelation> = emptyList(),
    val tags: List<VnTag> = emptyList(),
    val developers: List<ProducerSummary> = emptyList(),
    val staff: List<VnStaff> = emptyList(),
    val va: List<VoiceActor> = emptyList(),
    val extlinks: List<ExtLink> = emptyList(),
)

@Serializable
data class CharacterVn(
    val id: String,
    val title: String? = null,
    val alttitle: String? = null,
    val spoiler: Int? = null,
    val role: String? = null,
    val image: VndbImage? = null,
    val rating: Double? = null,
)

@Serializable
data class CharacterTrait(
    val id: String,
    val name: String? = null,
    val spoiler: Int? = null,
    val lie: Boolean? = null,
    @SerialName("group_id") val groupId: String? = null,
    @SerialName("group_name") val groupName: String? = null,
    val sexual: Boolean? = null,
)

@Serializable
data class Character(
    val id: String,
    val name: String? = null,
    val original: String? = null,
    val aliases: List<String> = emptyList(),
    val description: String? = null,
    val image: VndbImage? = null,
    @SerialName("blood_type") val bloodType: String? = null,
    val height: Int? = null,
    val weight: Int? = null,
    val bust: Int? = null,
    val waist: Int? = null,
    val hips: Int? = null,
    val cup: String? = null,
    val age: Int? = null,
    val birthday: List<Int> = emptyList(),
    val sex: List<String?> = emptyList(),
    val gender: List<String?> = emptyList(),
    val vns: List<CharacterVn> = emptyList(),
    val traits: List<CharacterTrait> = emptyList(),
)

@Serializable
data class Producer(
    val id: String,
    val name: String? = null,
    val original: String? = null,
    val aliases: List<String> = emptyList(),
    val lang: String? = null,
    val type: String? = null,
    val description: String? = null,
    val extlinks: List<ExtLink> = emptyList(),
)

@Serializable
data class StaffAlias(
    val aid: Int? = null,
    val name: String? = null,
    val latin: String? = null,
    val ismain: Boolean? = null,
)

@Serializable
data class Staff(
    val id: String,
    val aid: Int? = null,
    val ismain: Boolean? = null,
    val name: String? = null,
    val original: String? = null,
    val lang: String? = null,
    val gender: String? = null,
    val description: String? = null,
    val extlinks: List<ExtLink> = emptyList(),
    val aliases: List<StaffAlias> = emptyList(),
)

@Serializable
data class Tag(
    val id: String,
    val name: String? = null,
    val aliases: List<String> = emptyList(),
    val description: String? = null,
    val category: String? = null,
    val searchable: Boolean? = null,
    val applicable: Boolean? = null,
    @SerialName("vn_count") val vnCount: Int? = null,
)

@Serializable
data class Trait(
    val id: String,
    val name: String? = null,
    val aliases: List<String> = emptyList(),
    val description: String? = null,
    val searchable: Boolean? = null,
    val applicable: Boolean? = null,
    val sexual: Boolean? = null,
    @SerialName("group_id") val groupId: String? = null,
    @SerialName("group_name") val groupName: String? = null,
    @SerialName("char_count") val charCount: Int? = null,
)

@Serializable
data class ReleaseLanguage(
    val lang: String? = null,
    val title: String? = null,
    val latin: String? = null,
    val mtl: Boolean? = null,
    val main: Boolean? = null,
)

@Serializable
data class ReleaseVn(
    val id: String,
    val title: String? = null,
    val rtype: String? = null,
)

@Serializable
data class ReleaseProducer(
    val id: String,
    val name: String? = null,
    val developer: Boolean? = null,
    val publisher: Boolean? = null,
)

@Serializable
data class Release(
    val id: String,
    val title: String? = null,
    val alttitle: String? = null,
    val languages: List<ReleaseLanguage> = emptyList(),
    val platforms: List<String> = emptyList(),
    val vns: List<ReleaseVn> = emptyList(),
    val producers: List<ReleaseProducer> = emptyList(),
    val released: String? = null,
    val minage: Int? = null,
    val patch: Boolean? = null,
    val freeware: Boolean? = null,
    val official: Boolean? = null,
    @SerialName("has_ero") val hasEro: Boolean? = null,
    val voiced: Int? = null,
    val notes: String? = null,
    val extlinks: List<ExtLink> = emptyList(),
    val engine: String? = null,
)

@Serializable
data class Quote(
    val id: String,
    val quote: String? = null,
    val score: Int? = null,
    val vn: VisualNovel? = null,
    val character: Character? = null,
)

@Serializable
data class UlistLabel(
    val id: Int,
    val label: String? = null,
    val private: Boolean? = null,
    val count: Int? = null,
)

@Serializable
data class UlistLabelsResponse(
    val labels: List<UlistLabel> = emptyList(),
)

@Serializable
data class UlistEntry(
    val id: String,
    val added: Long? = null,
    val voted: Long? = null,
    val lastmod: Long? = null,
    val vote: Int? = null,
    val started: String? = null,
    val finished: String? = null,
    val notes: String? = null,
    val labels: List<UlistLabel> = emptyList(),
    val vn: VisualNovel? = null,
)

@Serializable
data class AuthInfo(
    val id: String,
    val username: String? = null,
    val permissions: List<String> = emptyList(),
)

@Serializable
data class DbStats(
    val chars: Int = 0,
    val producers: Int = 0,
    val releases: Int = 0,
    val staff: Int = 0,
    val tags: Int = 0,
    val traits: Int = 0,
    val vn: Int = 0,
)

@Serializable
data class FavoriteItem(
    val id: String,
    val type: String,
    val title: String,
    val subtitle: String? = null,
    val imageUrl: String? = null,
    val addedAt: Long = System.currentTimeMillis(),
)

enum class SearchKind(val label: String) {
    VN("作品"),
    CHARACTER("角色"),
    PRODUCER("制作"),
    STAFF("职员"),
    TAG("标签"),
}

enum class TitlePreference(val label: String) {
    SITE("站点标题"),
    ORIGINAL("原文"),
    ROMANIZED("罗马音"),
    CHINESE("中文优先"),
    ENGLISH("英文优先"),
}

enum class ColorMode(val label: String) {
    SYSTEM("跟随系统"),
    LIGHT("浅色"),
    DARK("深色"),
    MONET_SYSTEM("莫奈 · 系统"),
    MONET_LIGHT("莫奈 · 浅色"),
    MONET_DARK("莫奈 · 深色"),
}

enum class NsfwPolicy(val label: String) {
    HIDE("隐藏"),
    SHOW("显示"),
}

data class SearchFilters(
    val language: String? = null,
    val platform: String? = null,
    val minRating: Int? = null,
    val length: Int? = null,
    val sort: String = "searchrank",
    val reverse: Boolean = true,
    val releasedFrom: String? = null,
    val releasedTo: String? = null,
    val hasDescription: Boolean = false,
    val finishedOnly: Boolean = false,
)

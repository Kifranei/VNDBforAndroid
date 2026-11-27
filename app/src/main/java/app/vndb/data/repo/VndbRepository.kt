package app.vndb.data.repo

import app.vndb.data.api.UlistPatch
import app.vndb.data.api.VndbClient
import app.vndb.data.api.VndbFilter
import app.vndb.data.api.VndbPage
import app.vndb.data.api.VndbQuery
import app.vndb.data.model.AuthInfo
import app.vndb.data.model.Character
import app.vndb.data.model.DbStats
import app.vndb.data.model.Producer
import app.vndb.data.model.Quote
import app.vndb.data.model.Release
import app.vndb.data.model.SearchFilters
import app.vndb.data.model.SearchKind
import app.vndb.data.model.Staff
import app.vndb.data.model.Tag
import app.vndb.data.model.UlistEntry
import app.vndb.data.model.UlistLabel
import app.vndb.data.model.VisualNovel

class VndbRepository(private val client: VndbClient) {

    suspend fun stats(): DbStats = client.stats()

    suspend fun authInfo(): AuthInfo = client.authInfo()

    suspend fun randomQuote(): Quote? {
        val page = client.queryQuote(
            VndbQuery(
                filters = VndbFilter.pred("random", "=", 1),
                fields = VndbClient.QUOTE_FIELDS,
                results = 1,
            ),
        )
        return page.results.firstOrNull()
    }

    suspend fun topRated(page: Int = 1, results: Int = 12): VndbPage<VisualNovel> =
        client.queryVn(
            VndbQuery(
                filters = VndbFilter.and(
                    VndbFilter.pred("votecount", ">=", 80),
                    VndbFilter.pred("rating", ">=", 70),
                ),
                fields = VndbClient.VN_LIST_FIELDS,
                sort = "rating",
                reverse = true,
                results = results,
                page = page,
            ),
        )

    suspend fun recentlyReleased(page: Int = 1, results: Int = 12): VndbPage<VisualNovel> =
        client.queryVn(
            VndbQuery(
                filters = VndbFilter.and(
                    VndbFilter.pred("released", "!=", "TBA"),
                    VndbFilter.pred("released", "<=", "today"),
                ),
                fields = VndbClient.VN_LIST_FIELDS,
                sort = "released",
                reverse = true,
                results = results,
                page = page,
            ),
        )

    suspend fun mostVoted(page: Int = 1, results: Int = 12): VndbPage<VisualNovel> =
        client.queryVn(
            VndbQuery(
                filters = null,
                fields = VndbClient.VN_LIST_FIELDS,
                sort = "votecount",
                reverse = true,
                results = results,
                page = page,
            ),
        )

    suspend fun searchVn(query: String, page: Int, filters: SearchFilters): VndbPage<VisualNovel> {
        val filter = buildVnFilters(query, filters)
        val sort = if (query.isBlank() && filters.sort == "searchrank") "rating" else filters.sort
        return client.queryVn(
            VndbQuery(
                filters = filter,
                fields = VndbClient.VN_LIST_FIELDS,
                sort = sort,
                reverse = filters.reverse,
                results = 20,
                page = page,
            ),
        )
    }

    suspend fun searchCharacters(query: String, page: Int): VndbPage<Character> =
        client.queryCharacter(
            VndbQuery(
                filters = query.takeIf { it.isNotBlank() }?.let { VndbFilter.search(it) },
                fields = VndbClient.CHARACTER_LIST_FIELDS,
                sort = if (query.isBlank()) "id" else "searchrank",
                reverse = query.isBlank(),
                results = 20,
                page = page,
            ),
        )

    suspend fun searchProducers(query: String, page: Int): VndbPage<Producer> =
        client.queryProducer(
            VndbQuery(
                filters = query.takeIf { it.isNotBlank() }?.let { VndbFilter.search(it) },
                fields = VndbClient.PRODUCER_FIELDS,
                sort = if (query.isBlank()) "id" else "searchrank",
                reverse = query.isBlank(),
                results = 20,
                page = page,
            ),
        )

    suspend fun searchStaff(query: String, page: Int): VndbPage<Staff> =
        client.queryStaff(
            VndbQuery(
                filters = VndbFilter.and(
                    VndbFilter.pred("ismain", "=", 1),
                    query.takeIf { it.isNotBlank() }?.let { VndbFilter.search(it) },
                ),
                fields = VndbClient.STAFF_FIELDS,
                sort = if (query.isBlank()) "id" else "searchrank",
                reverse = query.isBlank(),
                results = 20,
                page = page,
            ),
        )

    suspend fun searchTags(query: String, page: Int): VndbPage<Tag> =
        client.queryTag(
            VndbQuery(
                filters = query.takeIf { it.isNotBlank() }?.let { VndbFilter.search(it) },
                fields = VndbClient.TAG_FIELDS,
                sort = if (query.isBlank()) "vn_count" else "searchrank",
                reverse = query.isBlank() || query.isNotBlank(),
                results = 20,
                page = page,
            ),
        )

    suspend fun vnDetail(id: String): VisualNovel {
        val core = client.queryVn(
            VndbQuery(
                filters = VndbFilter.id(id),
                fields = VndbClient.VN_DETAIL_CORE,
                results = 1,
            ),
        ).results.first()
        val media = runCatching {
            client.queryVn(
                VndbQuery(
                    filters = VndbFilter.id(id),
                    fields = VndbClient.VN_DETAIL_MEDIA,
                    results = 1,
                ),
            ).results.first()
        }.getOrNull()
        val staff = runCatching {
            client.queryVn(
                VndbQuery(
                    filters = VndbFilter.id(id),
                    fields = VndbClient.VN_DETAIL_STAFF,
                    results = 1,
                ),
            ).results.first()
        }.getOrNull()
        return core.copy(
            screenshots = media?.screenshots.orEmpty(),
            relations = media?.relations.orEmpty(),
            staff = staff?.staff.orEmpty(),
            va = staff?.va.orEmpty(),
        )
    }

    suspend fun updateUlist(id: String, vote: Int?, labels: List<Int>?, notes: String?) {
        client.patchUlist(
            id,
            UlistPatch(vote = vote, notes = notes, labels = labels),
        )
    }

    suspend fun ulistEntry(user: String, vnId: String): UlistEntry? =
        client.queryUlist(
            VndbQuery(
                user = user,
                filters = VndbFilter.id(vnId),
                fields = VndbClient.ULIST_FIELDS,
                results = 1,
            ),
        ).results.firstOrNull()

    suspend fun characterDetail(id: String): Character =
        client.queryCharacter(
            VndbQuery(
                filters = VndbFilter.id(id),
                fields = VndbClient.CHARACTER_DETAIL_FIELDS,
                results = 1,
            ),
        ).results.first()

    suspend fun producerDetail(id: String): Producer =
        client.queryProducer(
            VndbQuery(
                filters = VndbFilter.id(id),
                fields = VndbClient.PRODUCER_FIELDS,
                results = 1,
            ),
        ).results.first()

    suspend fun staffDetail(id: String): Staff =
        client.queryStaff(
            VndbQuery(
                filters = VndbFilter.and(VndbFilter.id(id), VndbFilter.pred("ismain", "=", 1)),
                fields = VndbClient.STAFF_FIELDS,
                results = 1,
            ),
        ).results.first()

    suspend fun tagDetail(id: String): Tag =
        client.queryTag(
            VndbQuery(
                filters = VndbFilter.id(id),
                fields = VndbClient.TAG_FIELDS,
                results = 1,
            ),
        ).results.first()

    suspend fun vnByDeveloper(producerId: String, page: Int): VndbPage<VisualNovel> =
        client.queryVn(
            VndbQuery(
                filters = VndbFilter.pred("developer", "=", VndbFilter.id(producerId).let {
                    // nested producer filter
                    kotlinx.serialization.json.buildJsonArray {
                        add(kotlinx.serialization.json.JsonPrimitive("id"))
                        add(kotlinx.serialization.json.JsonPrimitive("="))
                        add(kotlinx.serialization.json.JsonPrimitive(producerId))
                    }
                }),
                fields = VndbClient.VN_LIST_FIELDS,
                sort = "released",
                reverse = true,
                results = 20,
                page = page,
            ),
        )

    suspend fun vnByStaff(staffId: String, page: Int): VndbPage<VisualNovel> =
        client.queryVn(
            VndbQuery(
                filters = VndbFilter.pred(
                    "staff",
                    "=",
                    kotlinx.serialization.json.buildJsonArray {
                        add(kotlinx.serialization.json.JsonPrimitive("id"))
                        add(kotlinx.serialization.json.JsonPrimitive("="))
                        add(kotlinx.serialization.json.JsonPrimitive(staffId))
                    },
                ),
                fields = VndbClient.VN_LIST_FIELDS,
                sort = "released",
                reverse = true,
                results = 20,
                page = page,
            ),
        )

    suspend fun vnByTag(tagId: String, page: Int): VndbPage<VisualNovel> =
        client.queryVn(
            VndbQuery(
                filters = VndbFilter.pred("tag", "=", tagId),
                fields = VndbClient.VN_LIST_FIELDS,
                sort = "rating",
                reverse = true,
                results = 20,
                page = page,
            ),
        )

    suspend fun releasesForVn(vnId: String): List<Release> =
        client.queryRelease(
            VndbQuery(
                filters = VndbFilter.pred(
                    "vn",
                    "=",
                    kotlinx.serialization.json.buildJsonArray {
                        add(kotlinx.serialization.json.JsonPrimitive("id"))
                        add(kotlinx.serialization.json.JsonPrimitive("="))
                        add(kotlinx.serialization.json.JsonPrimitive(vnId))
                    },
                ),
                fields = VndbClient.RELEASE_FIELDS,
                sort = "released",
                reverse = true,
                results = 100,
            ),
        ).results

    suspend fun charactersForVn(vnId: String): List<Character> {
        val all = mutableListOf<Character>()
        var page = 1
        var more = true
        while (more && page <= 10) {
            val chunk = client.queryCharacter(
                VndbQuery(
                    filters = VndbFilter.pred(
                        "vn",
                        "=",
                        kotlinx.serialization.json.buildJsonArray {
                            add(kotlinx.serialization.json.JsonPrimitive("id"))
                            add(kotlinx.serialization.json.JsonPrimitive("="))
                            add(kotlinx.serialization.json.JsonPrimitive(vnId))
                        },
                    ),
                    fields = VndbClient.CHARACTER_LIST_FIELDS,
                    results = 100,
                    page = page,
                ),
            )
            all += chunk.results
            more = chunk.more
            page += 1
        }
        return all
    }

    suspend fun userList(user: String, label: Int?, page: Int): VndbPage<UlistEntry> =
        client.queryUlist(
            VndbQuery(
                user = user,
                filters = label?.let { VndbFilter.pred("label", "=", it) },
                fields = VndbClient.ULIST_FIELDS,
                sort = "lastmod",
                reverse = true,
                results = 20,
                page = page,
            ),
        )

    suspend fun userLabels(user: String?): List<UlistLabel> =
        client.ulistLabels(user).labels

    private fun buildVnFilters(query: String, filters: SearchFilters): VndbFilter? {
        val parts = buildList {
            if (query.isNotBlank()) add(VndbFilter.search(query))
            filters.language?.let { add(VndbFilter.pred("lang", "=", it)) }
            filters.platform?.let { add(VndbFilter.pred("platform", "=", it)) }
            filters.minRating?.let { add(VndbFilter.pred("rating", ">=", it)) }
            filters.length?.let { add(VndbFilter.pred("length", "=", it)) }
            filters.releasedFrom?.let { add(VndbFilter.pred("released", ">=", it)) }
            filters.releasedTo?.let { add(VndbFilter.pred("released", "<=", it)) }
            if (filters.hasDescription) add(VndbFilter.pred("has_description", "=", 1))
            if (filters.finishedOnly) add(VndbFilter.pred("devstatus", "=", 0))
        }
        return when (parts.size) {
            0 -> null
            1 -> parts.first()
            else -> VndbFilter.And(parts)
        }
    }
}

fun SearchKind.path(): String = when (this) {
    SearchKind.VN -> "vn"
    SearchKind.CHARACTER -> "character"
    SearchKind.PRODUCER -> "producer"
    SearchKind.STAFF -> "staff"
    SearchKind.TAG -> "tag"
}

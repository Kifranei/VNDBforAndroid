package app.vndb.data.api

import app.vndb.data.model.AuthInfo
import app.vndb.data.model.Character
import app.vndb.data.model.DbStats
import app.vndb.data.model.Producer
import app.vndb.data.model.Quote
import app.vndb.data.model.Release
import app.vndb.data.model.Staff
import app.vndb.data.model.Tag
import app.vndb.data.model.UlistEntry
import app.vndb.data.model.UlistLabelsResponse
import app.vndb.data.model.VisualNovel
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.UserAgent
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.request.header
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import android.util.Log

class VndbException(val status: Int, override val message: String) : RuntimeException(message)

class VndbClient(
    private val tokenProvider: () -> String?,
) {
    val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        explicitNulls = false
        encodeDefaults = false
        coerceInputValues = true
    }

    private val client = HttpClient(OkHttp) {
        expectSuccess = false
        install(UserAgent) {
            agent = "VNDBforAndroid/1.0 (https://github.com/Croilan/VNDBforAndroid)"
        }
        install(HttpTimeout) {
            requestTimeoutMillis = 20_000
            connectTimeoutMillis = 10_000
            socketTimeoutMillis = 20_000
        }
        install(HttpRequestRetry) {
            retryOnServerErrors(maxRetries = 2)
            exponentialDelay()
        }
        install(ContentNegotiation) { json(json) }
        install(Logging) {
            logger = object : Logger {
                override fun log(message: String) {
                    Log.d(TAG, message)
                }
            }
            level = LogLevel.INFO
        }
        defaultRequest {
            url(BASE_URL)
            header(HttpHeaders.Accept, "application/json")
            contentType(ContentType.Application.Json)
        }
    }

    private val rateMutex = Mutex()
    private var lastRequestAt = 0L

    suspend fun stats(): DbStats = get("stats")

    suspend fun authInfo(): AuthInfo = get("authinfo")

    suspend fun queryVn(query: VndbQuery): VndbPage<VisualNovel> = post("vn", query)

    suspend fun queryRelease(query: VndbQuery): VndbPage<Release> = post("release", query)

    suspend fun queryProducer(query: VndbQuery): VndbPage<Producer> = post("producer", query)

    suspend fun queryCharacter(query: VndbQuery): VndbPage<Character> = post("character", query)

    suspend fun queryStaff(query: VndbQuery): VndbPage<Staff> = post("staff", query)

    suspend fun queryTag(query: VndbQuery): VndbPage<Tag> = post("tag", query)

    suspend fun queryQuote(query: VndbQuery): VndbPage<Quote> = post("quote", query)

    suspend fun queryUlist(query: VndbQuery): VndbPage<UlistEntry> = post("ulist", query)

    suspend fun ulistLabels(user: String? = null): UlistLabelsResponse {
        val path = buildString {
            append("ulist_labels")
            if (!user.isNullOrBlank()) append("?user=").append(user).append("&fields=count")
            else append("?fields=count")
        }
        return get(path)
    }

    suspend fun patchUlist(id: String, body: UlistPatch) {
        throttle()
        val response = client.request("ulist/$id") {
            method = HttpMethod.Patch
            applyAuth()
            setBody(body)
        }
        if (!response.status.isSuccess()) {
            throw VndbException(response.status.value, response.bodyAsText())
        }
    }

    private suspend inline fun <reified T> get(path: String): T {
        throttle()
        val response = client.request(path) {
            method = HttpMethod.Get
            applyAuth()
        }
        return decode(response)
    }

    private suspend inline fun <reified T> post(path: String, query: VndbQuery): T {
        throttle()
        val response = client.request(path) {
            method = HttpMethod.Post
            applyAuth()
            setBody(query)
        }
        return decode(response)
    }

    private suspend inline fun <reified T> decode(response: io.ktor.client.statement.HttpResponse): T {
        val text = response.bodyAsText()
        if (!response.status.isSuccess()) {
            throw VndbException(response.status.value, text.ifBlank { response.status.description })
        }
        return try {
            json.decodeFromString(text)
        } catch (e: Exception) {
            throw VndbException(response.status.value, e.message ?: text.take(200))
        }
    }

    private fun io.ktor.client.request.HttpRequestBuilder.applyAuth() {
        tokenProvider()?.takeIf { it.isNotBlank() }?.let {
            header(HttpHeaders.Authorization, "Token $it")
        }
    }

    private suspend fun throttle() {
        rateMutex.withLock {
            val now = System.currentTimeMillis()
            val wait = MIN_INTERVAL_MS - (now - lastRequestAt)
            if (wait > 0) delay(wait)
            lastRequestAt = System.currentTimeMillis()
        }
    }

    fun close() = client.close()

    companion object {
        const val BASE_URL = "https://api.vndb.org/kana/"
        const val SITE_URL = "https://vndb.org"
        private const val MIN_INTERVAL_MS = 350L
        private const val TAG = "VndbClient"

        const val VN_LIST_FIELDS =
            "title,alttitle,titles{lang,title,latin,main,official},image{id,url,thumbnail,sexual,violence,dims},rating,votecount,released,platforms,languages,length,length_minutes,olang,devstatus"

        const val VN_DETAIL_CORE =
            "$VN_LIST_FIELDS,aliases,description,average,length_votes," +
                "tags{id,name,rating,spoiler,lie,category}," +
                "developers{id,name,original}," +
                "extlinks{url,label,name}"

        const val VN_DETAIL_MEDIA =
            "screenshots{url,thumbnail,sexual,violence,dims}," +
                "relations{relation,relation_official,title,alttitle,image{url,thumbnail,sexual}}"

        const val VN_DETAIL_STAFF =
            "staff{id,aid,name,original,role,note}"

        const val VN_DETAIL_FIELDS = "$VN_DETAIL_CORE,$VN_DETAIL_MEDIA,$VN_DETAIL_STAFF"

        const val CHARACTER_LIST_FIELDS =
            "name,original,image{url,sexual,violence},vns{id,role}"

        const val CHARACTER_DETAIL_FIELDS =
            "name,original,aliases,description,image{url,sexual,violence},blood_type,height,weight,bust,waist,hips,cup,age,birthday,sex," +
                "vns{id,title,alttitle,spoiler,role}," +
                "traits{id,name,spoiler,lie,group_id,group_name,sexual}"

        const val PRODUCER_FIELDS = "name,original,aliases,lang,type,description,extlinks{url,label,name}"

        const val STAFF_FIELDS =
            "aid,ismain,name,original,lang,gender,description,extlinks{url,label,name},aliases{aid,name,latin,ismain}"

        const val TAG_FIELDS = "name,aliases,description,category,searchable,applicable,vn_count"

        const val RELEASE_FIELDS =
            "title,alttitle,languages{lang,title,latin,mtl,main},platforms,released,minage,patch,freeware,official,has_ero,voiced,notes,engine,extlinks{url,label}," +
                "producers{id,name,developer,publisher},vns{id,title,rtype}"

        const val ULIST_FIELDS =
            "added,voted,lastmod,vote,started,finished,notes,labels{id,label},vn{id,$VN_LIST_FIELDS}"

        const val QUOTE_FIELDS = "quote,score,vn{id,title,alttitle,image{url,thumbnail,sexual}},character{id,name,original}"
    }
}

package com.llz121517.meapet.client

/**
 * HTTP 引擎抽象接口。
 *
 * 生产环境可注入 [KtorHttpClientEngine]；单元测试可注入 Fake/Mock 实现，
 * 从而避免任何真实网络 I/O 与具体 HTTP 客户端库耦合。
 */
interface HttpClientEngine {
    suspend fun execute(request: HttpRequest): HttpResponse
    fun close()
}

data class HttpRequest(
    val method: HttpMethod,
    val url: String,
    val headers: Map<String, String> = emptyMap(),
    val body: RequestBody? = null
)

enum class HttpMethod {
    GET,
    POST
}

sealed class RequestBody {
    data class Json(val content: String) : RequestBody()
    data class Multipart(val parts: List<MultipartPart>) : RequestBody()
}

data class MultipartPart(
    val name: String,
    val filename: String?,
    val contentType: String?,
    val body: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as MultipartPart
        return name == other.name &&
            filename == other.filename &&
            contentType == other.contentType &&
            body.contentEquals(other.body)
    }

    override fun hashCode(): Int {
        var result = name.hashCode()
        result = 31 * result + (filename?.hashCode() ?: 0)
        result = 31 * result + (contentType?.hashCode() ?: 0)
        result = 31 * result + body.contentHashCode()
        return result
    }
}

data class HttpResponse(
    val statusCode: Int,
    val body: ByteArray,
    val contentType: String?
) {
    fun bodyAsText(): String = body.decodeToString()

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as HttpResponse
        return statusCode == other.statusCode &&
            body.contentEquals(other.body) &&
            contentType == other.contentType
    }

    override fun hashCode(): Int {
        var result = statusCode
        result = 31 * result + body.contentHashCode()
        result = 31 * result + (contentType?.hashCode() ?: 0)
        return result
    }
}

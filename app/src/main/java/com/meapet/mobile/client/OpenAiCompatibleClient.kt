package com.meapet.mobile.client

import com.meapet.mobile.client.exception.ApiException

/**
 * OpenAI 兼容 HTTP 客户端。
 *
 * 特点：
 * - 仅负责 HTTP 通信与请求体序列化，不处理业务逻辑；
 * - 所有 API 返回原始 JSON 字符串或二进制字节数组，由调用方自行解析；
 * - HTTP 引擎通过 [HttpClientEngine] 抽象注入，默认使用 Ktor CIO；
 * - 所有公开方法均为 `suspend`，原生协程支持；
 * - 路径统一由 [apiUrl] 自动拼接：用户只需填基址（可带或不带 `/v1`），
 *   客户端负责补上 `/v1/...` 后续路径。
 *
 * @param apiKey API 密钥
 * @param baseUrl 基础 URL，例如 `https://api.openai.com` 或 `https://xxx.com/v1`（尾部 `/` 与 `/v1` 会自动规范化）
 * @param engine HTTP 引擎，单元测试可注入 Fake 实现
 */
class OpenAiCompatibleClient(
    private val apiKey: String,
    baseUrl: String,
    private val engine: HttpClientEngine = KtorHttpClientEngine()
) {

    /**
     * 规范化后的基址（**不含** `/v1`）：
     * - 去掉空白与尾部 `/`
     * - 若末尾已是 `/v1` 则剥掉，避免拼出 `/v1/v1/...`
     */
    private val baseUrl: String = normalizeBaseUrl(baseUrl)

    /** `GET /v1/models` */
    suspend fun listModels(): String {
        val request = HttpRequest(
            method = HttpMethod.GET,
            url = apiUrl("models"),
            headers = authHeaders()
        )
        return executeExpectText(request)
    }

    /** `POST /v1/chat/completions` */
    suspend fun chatCompletion(requestBody: String): String {
        val request = HttpRequest(
            method = HttpMethod.POST,
            url = apiUrl("chat/completions"),
            headers = authHeaders(),
            body = RequestBody.Json(requestBody)
        )
        return executeExpectText(request)
    }

    /** `POST /v1/audio/transcriptions` */
    suspend fun createTranscription(parts: List<MultipartPart>): String {
        val request = HttpRequest(
            method = HttpMethod.POST,
            url = apiUrl("audio/transcriptions"),
            headers = authHeaders(),
            body = RequestBody.Multipart(parts)
        )
        return executeExpectText(request)
    }

    /** `POST /v1/audio/speech` */
    suspend fun createSpeech(requestBody: String): ByteArray {
        val request = HttpRequest(
            method = HttpMethod.POST,
            url = apiUrl("audio/speech"),
            headers = authHeaders(),
            body = RequestBody.Json(requestBody)
        )
        return executeExpectOk(request).body
    }

    /**
     * 拼出完整 API 地址：`{base}/v1/{path}`。
     *
     * @param path `/v1` 之后的路径片段，可带或不带前导 `/`，
     *   例如 `"models"` / `"chat/completions"` / `"/audio/speech"`
     */
    private fun apiUrl(path: String): String {
        val cleaned = path.trim().trimStart('/')
        require(cleaned.isNotEmpty()) { "API path must not be empty" }
        return "$baseUrl/v1/$cleaned"
    }

    companion object {
        /** 见 [OpenAiCompatibleClient.baseUrl] 说明。 */
        internal fun normalizeBaseUrl(raw: String): String {
            var url = raw.trim().trimEnd('/')
            // 用户常把 `/v1` 或 `/v1/` 一起填进来；只剥末尾这一段，保留中间的代理前缀
            if (url.endsWith("/v1", ignoreCase = true)) {
                url = url.dropLast(3).trimEnd('/')
            }
            return url
        }
    }

    /** 关闭底层 HTTP 引擎，释放资源。 */
    fun close() {
        engine.close()
    }

    private fun authHeaders(): Map<String, String> =
        mapOf("Authorization" to "Bearer $apiKey")

    private suspend fun executeExpectText(request: HttpRequest): String {
        val response = executeExpectOk(request)
        return response.bodyAsText()
    }

    private suspend fun executeExpectOk(request: HttpRequest): HttpResponse {
        val response = engine.execute(request)
        if (response.statusCode !in 200..299) {
            throw ApiException(
                statusCode = response.statusCode,
                responseBody = response.bodyAsText(),
                message = "API request failed with status ${response.statusCode}"
            )
        }
        return response
    }
}

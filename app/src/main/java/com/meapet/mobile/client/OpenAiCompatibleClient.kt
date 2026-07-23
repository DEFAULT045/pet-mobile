package com.meapet.mobile.client

import com.meapet.mobile.client.exception.ApiException

/**
 * OpenAI 兼容 HTTP 客户端。
 *
 * 特点：
 * - 仅负责 HTTP 通信与请求体序列化，不处理业务逻辑；
 * - 所有 API 返回原始 JSON 字符串或二进制字节数组，由调用方自行解析；
 * - HTTP 引擎通过 [HttpClientEngine] 抽象注入，默认使用 Ktor CIO；
 * - 所有公开方法均为 `suspend`，原生协程支持。
 *
 * @param apiKey API 密钥
 * @param baseUrl 基础 URL，例如 `https://api.openai.com`（尾部 `/` 或多余的 `/v1` 后缀会被自动规范化）
 * @param engine HTTP 引擎，单元测试可注入 Fake 实现
 */
class OpenAiCompatibleClient(
    private val apiKey: String,
    baseUrl: String,
    private val engine: HttpClientEngine = KtorHttpClientEngine()
) {

    /** 规范化后的基础 URL：去掉尾部 `/`；若用户填的地址已带 `/v1` 后缀则剥掉，避免重复拼接。 */
    private val baseUrl: String = baseUrl.trimEnd('/').removeSuffix("/v1").trimEnd('/')

    /** `GET /v1/models` */
    suspend fun listModels(): String {
        val request = HttpRequest(
            method = HttpMethod.GET,
            url = "$baseUrl/v1/models",
            headers = authHeaders()
        )
        return executeExpectText(request)
    }

    /** `POST /v1/chat/completions` */
    suspend fun chatCompletion(requestBody: String): String {
        val request = HttpRequest(
            method = HttpMethod.POST,
            url = "$baseUrl/v1/chat/completions",
            headers = authHeaders(),
            body = RequestBody.Json(requestBody)
        )
        return executeExpectText(request)
    }

    /** `POST /v1/audio/transcriptions` */
    suspend fun createTranscription(parts: List<MultipartPart>): String {
        val request = HttpRequest(
            method = HttpMethod.POST,
            url = "$baseUrl/v1/audio/transcriptions",
            headers = authHeaders(),
            body = RequestBody.Multipart(parts)
        )
        return executeExpectText(request)
    }

    /** `POST /v1/audio/speech` */
    suspend fun createSpeech(requestBody: String): ByteArray {
        val request = HttpRequest(
            method = HttpMethod.POST,
            url = "$baseUrl/v1/audio/speech",
            headers = authHeaders(),
            body = RequestBody.Json(requestBody)
        )
        return executeExpectOk(request).body
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

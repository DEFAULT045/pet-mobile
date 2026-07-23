package com.meapet.mobile.client.test

import com.meapet.mobile.client.HttpClientEngine
import com.meapet.mobile.client.HttpRequest
import com.meapet.mobile.client.HttpResponse

/**
 * 单元测试使用的 Fake HTTP 引擎。
 *
 * 记录所有收到的请求，并按 [handler] 返回预设响应，便于验证请求构造与异常路径。
 */
class FakeHttpClientEngine(
    private val handler: suspend (HttpRequest) -> HttpResponse
) : HttpClientEngine {

    private val _requests = mutableListOf<HttpRequest>()
    val requests: List<HttpRequest> get() = _requests.toList()

    override suspend fun execute(request: HttpRequest): HttpResponse {
        _requests.add(request)
        return handler(request)
    }

    override fun close() {}
}

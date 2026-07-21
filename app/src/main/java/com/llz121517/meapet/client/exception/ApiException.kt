package com.llz121517.meapet.client.exception

/**
 * OpenAI 兼容 API 的统一异常封装。
 *
 * @property statusCode HTTP 状态码
 * @property responseBody 服务端返回的原始响应体（JSON 字符串）
 */
class ApiException(
    val statusCode: Int,
    val responseBody: String,
    message: String
) : RuntimeException(message)

package com.eggetteluo.bank_server.config

import jakarta.servlet.DispatcherType
import jakarta.servlet.FilterChain
import jakarta.servlet.RequestDispatcher
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import org.springframework.web.util.ContentCachingRequestWrapper
import org.springframework.web.util.ContentCachingResponseWrapper
import org.springframework.web.filter.OncePerRequestFilter
import java.nio.charset.Charset
import kotlin.math.max

@Component
@Order(1)
class RequestLogFilter : OncePerRequestFilter() {
    private val log = LoggerFactory.getLogger(RequestLogFilter::class.java)
    private val maxBodyLength = 2000
    private val cacheLimit = 1024 * 1024

    override fun shouldNotFilterErrorDispatch(): Boolean = false

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val requestWrapper = if (request is ContentCachingRequestWrapper) request else ContentCachingRequestWrapper(request, cacheLimit)
        val responseWrapper = if (response is ContentCachingResponseWrapper) response else ContentCachingResponseWrapper(response)
        val start = System.currentTimeMillis()
        var error: Exception? = null

        try {
            filterChain.doFilter(requestWrapper, responseWrapper)
        } catch (ex: Exception) {
            error = ex
            throw ex
        } finally {
            val status = responseWrapper.status
            val costMs = max(0, System.currentTimeMillis() - start)
            val query = requestWrapper.queryString?.let { "?$it" } ?: ""
            val path = "${requestWrapper.requestURI}$query"
            val reqBody = sanitizeAndCut(extractBody(requestWrapper.contentAsByteArray, requestWrapper.characterEncoding))
            val respBody = sanitizeAndCut(extractBody(responseWrapper.contentAsByteArray, responseWrapper.characterEncoding))
            val dispatchType = requestWrapper.dispatcherType
            val servletEx = requestWrapper.getAttribute(RequestDispatcher.ERROR_EXCEPTION) as? Throwable
            val servletErrMsg = requestWrapper.getAttribute(RequestDispatcher.ERROR_MESSAGE)?.toString()
            val errorType = error?.javaClass?.simpleName ?: servletEx?.javaClass?.simpleName ?: "-"
            val errorMsg = error?.message ?: servletEx?.message ?: servletErrMsg ?: "-"

            // 某些 4xx/5xx 会在 ERROR 分发阶段才生成响应体；
            // REQUEST 分发阶段如果还没有响应体，先不打失败日志，避免出现 respBody/error 全是 '-'
            if (dispatchType == DispatcherType.REQUEST && status >= 400 && respBody == "-") {
                responseWrapper.copyBodyToResponse()
                return
            }

            if (status >= 400 || error != null) {
                log.error(
                    "HTTP {} {} -> {} ({} ms) dispatch={} ip={} reqBody={} respBody={} errorType={} errorMsg={}",
                    requestWrapper.method,
                    path,
                    status,
                    costMs,
                    dispatchType,
                    requestWrapper.remoteAddr,
                    reqBody,
                    respBody,
                    errorType,
                    errorMsg,
                )
            } else {
                log.info(
                    "HTTP {} {} -> {} ({} ms) dispatch={} ip={} reqBody={} respBody={}",
                    requestWrapper.method,
                    path,
                    status,
                    costMs,
                    dispatchType,
                    requestWrapper.remoteAddr,
                    reqBody,
                    respBody,
                )
            }

            responseWrapper.copyBodyToResponse()
        }
    }

    private fun extractBody(bytes: ByteArray, encoding: String?): String {
        if (bytes.isEmpty()) return "-"
        return try {
            String(bytes, Charset.forName(encoding ?: "UTF-8"))
        } catch (_: Exception) {
            "<non-text-body>"
        }
    }

    private fun sanitizeAndCut(body: String): String {
        val sanitized = body
            .replace(Regex("\"password\"\\s*:\\s*\"[^\"]*\"", RegexOption.IGNORE_CASE), "\"password\":\"******\"")
            .replace(Regex("\"withdrawPassword\"\\s*:\\s*\"[^\"]*\"", RegexOption.IGNORE_CASE), "\"withdrawPassword\":\"******\"")
            .replace(Regex("\"withdraw_pwd\"\\s*:\\s*\"[^\"]*\"", RegexOption.IGNORE_CASE), "\"withdraw_pwd\":\"******\"")
            .replace(Regex("\"withdrawPwd\"\\s*:\\s*\"[^\"]*\"", RegexOption.IGNORE_CASE), "\"withdrawPwd\":\"******\"")
            .replace(Regex("\"token\"\\s*:\\s*\"[^\"]*\"", RegexOption.IGNORE_CASE), "\"token\":\"******\"")
            .replace(Regex("\"idCard\"\\s*:\\s*\"[^\"]*\"", RegexOption.IGNORE_CASE), "\"idCard\":\"******\"")
        return if (sanitized.length > maxBodyLength) {
            sanitized.take(maxBodyLength) + "...(truncated)"
        } else {
            sanitized
        }
    }
}

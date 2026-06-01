package com.eggetteluo.bank_server.config

import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.MissingServletRequestParameterException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.server.ResponseStatusException
import java.time.OffsetDateTime

@RestControllerAdvice
class GlobalExceptionHandler {
    data class ApiErrorResponse(
        val timestamp: OffsetDateTime = OffsetDateTime.now(),
        val status: Int,
        val error: String,
        val message: String,
        val path: String,
    )

    // 业务主动抛出的异常（如：联系电话已存在）
    @ExceptionHandler(ResponseStatusException::class)
    fun handleResponseStatusException(
        ex: ResponseStatusException,
        request: HttpServletRequest,
    ): ResponseEntity<ApiErrorResponse> {
        val statusCode = ex.statusCode
        val status = HttpStatus.valueOf(statusCode.value())
        val message = ex.reason ?: status.reasonPhrase
        return ResponseEntity.status(status).body(
            ApiErrorResponse(
                status = status.value(),
                error = status.reasonPhrase,
                message = message,
                path = request.requestURI,
            ),
        )
    }

    // 请求参数缺失/格式错误等常见 400
    @ExceptionHandler(
        MissingServletRequestParameterException::class,
        IllegalArgumentException::class,
    )
    fun handleBadRequest(
        ex: Exception,
        request: HttpServletRequest,
    ): ResponseEntity<ApiErrorResponse> {
        val status = HttpStatus.BAD_REQUEST
        return ResponseEntity.status(status).body(
            ApiErrorResponse(
                status = status.value(),
                error = status.reasonPhrase,
                message = ex.message ?: "请求参数错误",
                path = request.requestURI,
            ),
        )
    }

    // 兜底异常
    @ExceptionHandler(Exception::class)
    fun handleOtherException(
        ex: Exception,
        request: HttpServletRequest,
    ): ResponseEntity<ApiErrorResponse> {
        val status = HttpStatus.INTERNAL_SERVER_ERROR
        return ResponseEntity.status(status).body(
            ApiErrorResponse(
                status = status.value(),
                error = status.reasonPhrase,
                message = ex.message ?: "服务器内部错误",
                path = request.requestURI,
            ),
        )
    }
}


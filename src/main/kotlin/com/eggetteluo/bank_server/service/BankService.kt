package com.eggetteluo.bank_server.service

import com.eggetteluo.bank_server.dto.BalanceResponse
import com.eggetteluo.bank_server.dto.LoginRequest
import com.eggetteluo.bank_server.dto.LoginResponse
import com.eggetteluo.bank_server.dto.NoTradeAccountView
import com.eggetteluo.bank_server.dto.OpenAccountRequest
import com.eggetteluo.bank_server.dto.OpenAccountResponse
import com.eggetteluo.bank_server.dto.TradeRecordView
import com.eggetteluo.bank_server.dto.TradeRequest
import com.eggetteluo.bank_server.dto.TradeStatsResponse
import com.eggetteluo.bank_server.dto.TransferRequest
import org.springframework.dao.DataAccessException
import org.springframework.http.HttpStatus
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.queryForObject
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.math.BigDecimal
import java.sql.Timestamp
import java.time.Instant
import java.time.OffsetDateTime
import java.util.Base64
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

@Service
class BankService(
    private val jdbcTemplate: JdbcTemplate,
) {
    private data class SessionUser(
        val userId: Long,
        val cardNo: String,
        val expiresAt: Instant,
    )

    private val sessions = ConcurrentHashMap<String, SessionUser>()
    private val sessionTtlSeconds = 8 * 60 * 60L

    @Transactional
    fun openAccount(request: OpenAccountRequest): OpenAccountResponse {
        validateBasic(request.idCard, request.phone, request.withdrawPassword, request.openAmount)
        val depositId = request.depositId ?: 1L

        if (jdbcTemplate.queryForObject<Long>(
                "SELECT COUNT(1) FROM bank_schema.user_info WHERE phone = ?",
                request.phone
            )!! > 0L
        ) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "联系电话已存在")
        }

        val userId = jdbcTemplate.queryForObject<Long>(
            """
            INSERT INTO bank_schema.user_info(user_name, id_card_enc, id_card_mask, phone, address)
            VALUES (?, ?, ?, ?, ?)
            RETURNING user_id
            """.trimIndent(),
            request.userName,
            encodeBase64(request.idCard),
            request.idCard,
            request.phone,
            request.address,
        ) ?: throw ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "开户失败")

        val cardNo = jdbcTemplate.queryForObject<String>("SELECT bank_schema.fn_generate_card_no()")
            ?: throw ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "生成卡号失败")

        jdbcTemplate.update(
            """
            INSERT INTO bank_schema.card_info(card_no, currency, deposit_id, open_amount, balance, withdraw_pwd_enc, is_lost, user_id)
            VALUES (?, 'CNY', ?, ?, ?, ?, FALSE, ?)
            """.trimIndent(),
            cardNo,
            depositId,
            request.openAmount,
            request.openAmount,
            encodeBase64(request.withdrawPassword),
            userId,
        )

        return OpenAccountResponse(userId = userId, cardNo = cardNo)
    }

    fun login(request: LoginRequest): LoginResponse {
        val cardNo = request.cardNo.trim()
        val password = request.password.trim()
        if (!Regex("^10103576[0-9]{8}$").matches(cardNo)) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "卡号格式不正确")
        }
        if (!Regex("^[0-9]{6}$").matches(password)) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "密码必须为6位数字")
        }

        val auth = jdbcTemplate.query(
            """
            SELECT user_id
            FROM bank_schema.card_info
            WHERE card_no = ?
              AND withdraw_pwd_enc = ?
            """.trimIndent(),
            { rs, _ -> rs.getLong("user_id") },
            cardNo,
            encodeBase64(password),
        ).firstOrNull() ?: throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "银行卡号或密码错误")

        val token = UUID.randomUUID().toString().replace("-", "")
        sessions[token] = SessionUser(
            userId = auth,
            cardNo = cardNo,
            expiresAt = Instant.now().plusSeconds(sessionTtlSeconds),
        )
        return LoginResponse(token = token, userId = auth, cardNo = cardNo)
    }

    @Transactional
    fun deposit(authorization: String?, request: TradeRequest): Long {
        val session = requireSession(authorization)
        assertOwner(session.userId, request.cardNo)
        validateAmount(request.amount)
        return callDepositOrWithdraw(request.cardNo, "存入", request.amount, request.remark)
    }

    @Transactional
    fun withdraw(authorization: String?, request: TradeRequest): Long {
        val session = requireSession(authorization)
        assertOwner(session.userId, request.cardNo)
        validateAmount(request.amount)
        return callDepositOrWithdraw(request.cardNo, "支取", request.amount, request.remark)
    }

    @Transactional
    fun transfer(authorization: String?, request: TransferRequest): String {
        val session = requireSession(authorization)
        assertOwner(session.userId, request.fromCardNo)
        validateAmount(request.amount)
        return try {
            jdbcTemplate.queryForObject<String>(
                "SELECT bank_schema.fn_transfer(?, ?, ?, ?)",
                request.fromCardNo,
                request.toCardNo,
                request.amount,
                request.remark,
            ) ?: "转账成功"
        } catch (e: DataAccessException) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, extractMessage(e))
        }
    }

    fun getBalance(authorization: String?, cardNo: String): BalanceResponse {
        val session = requireSession(authorization)
        assertOwner(session.userId, cardNo)
        val row = jdbcTemplate.query(
            "SELECT card_no, balance, is_lost FROM bank_schema.card_info WHERE card_no = ?",
            { rs, _ ->
                BalanceResponse(
                    cardNo = rs.getString("card_no"),
                    balance = rs.getBigDecimal("balance"),
                    isLost = rs.getBoolean("is_lost"),
                )
            },
            cardNo,
        ).firstOrNull() ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "卡号不存在")
        return row
    }

    fun queryTrades(authorization: String?, cardNo: String, page: Int, size: Int): List<TradeRecordView> {
        val session = requireSession(authorization)
        assertOwner(session.userId, cardNo)
        val safePage = if (page < 1) 1 else page
        val safeSize = when {
            size < 1 -> 10
            size > 100 -> 100
            else -> size
        }
        val offset = (safePage - 1) * safeSize

        return jdbcTemplate.query(
            """
            SELECT trade_id, trade_time, trade_type, card_no, amount, remark
            FROM bank_schema.trade_info
            WHERE card_no = ?
            ORDER BY trade_time DESC
            LIMIT ? OFFSET ?
            """.trimIndent(),
            { rs, _ ->
                TradeRecordView(
                    tradeId = rs.getLong("trade_id"),
                    tradeTime = rs.getObject("trade_time", OffsetDateTime::class.java),
                    tradeType = rs.getString("trade_type"),
                    cardNo = rs.getString("card_no"),
                    amount = rs.getBigDecimal("amount"),
                    remark = rs.getString("remark"),
                )
            },
            cardNo,
            safeSize,
            offset,
        )
    }

    fun printStatement(
        authorization: String?,
        cardNo: String,
        start: OffsetDateTime,
        end: OffsetDateTime
    ): List<TradeRecordView> {
        val session = requireSession(authorization)
        assertOwner(session.userId, cardNo)
        return try {
            jdbcTemplate.query(
                "SELECT * FROM bank_schema.fn_print_statement(?, ?, ?)",
                { rs, _ ->
                    TradeRecordView(
                        tradeId = 0,
                        tradeTime = rs.getObject("交易时间", OffsetDateTime::class.java),
                        tradeType = rs.getString("交易类型"),
                        cardNo = rs.getString("卡号"),
                        amount = rs.getBigDecimal("交易金额"),
                        remark = rs.getString("备注"),
                    )
                },
                cardNo,
                Timestamp.from(start.toInstant()),
                Timestamp.from(end.toInstant()),
            )
        } catch (e: DataAccessException) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, extractMessage(e))
        }
    }

    fun stats(start: OffsetDateTime, end: OffsetDateTime): List<TradeStatsResponse> {
        return jdbcTemplate.query(
            """
            SELECT
                trade_type,
                COUNT(*) AS cnt,
                COALESCE(SUM(amount), 0) AS total_amount
            FROM bank_schema.trade_info
            WHERE trade_time >= ? AND trade_time <= ?
            GROUP BY trade_type
            ORDER BY trade_type
            """.trimIndent(),
            { rs, _ ->
                TradeStatsResponse(
                    type = rs.getString("trade_type"),
                    count = rs.getLong("cnt"),
                    totalAmount = rs.getBigDecimal("total_amount"),
                )
            },
            Timestamp.from(start.toInstant()),
            Timestamp.from(end.toInstant()),
        )
    }

    fun noTradeAccounts(): List<NoTradeAccountView> {
        return jdbcTemplate.query(
            """
            SELECT c.card_no, u.user_name, u.phone, c.balance
            FROM bank_schema.card_info c
            JOIN bank_schema.user_info u ON u.user_id = c.user_id
            WHERE NOT EXISTS (
                SELECT 1 FROM bank_schema.trade_info t WHERE t.card_no = c.card_no
            )
            ORDER BY c.card_no
            """.trimIndent(),
        ) { rs, _ ->
            NoTradeAccountView(
                cardNo = rs.getString("card_no"),
                userName = rs.getString("user_name"),
                phone = rs.getString("phone"),
                balance = rs.getBigDecimal("balance"),
            )
        }
    }

    private fun callDepositOrWithdraw(cardNo: String, type: String, amount: BigDecimal, remark: String?): Long {
        return try {
            jdbcTemplate.queryForObject<Long>(
                "SELECT bank_schema.fn_deposit_or_withdraw(?, ?, ?, ?)",
                cardNo,
                type,
                amount,
                remark,
            ) ?: throw ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "交易失败")
        } catch (e: DataAccessException) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, extractMessage(e))
        }
    }

    private fun validateBasic(idCard: String, phone: String, pwd: String, openAmount: BigDecimal) {
        if (!Regex("^[0-9]{17}[0-9Xx]$").matches(idCard)) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "身份证格式不正确")
        }
        if (!Regex("^[0-9]{11}$").matches(phone)) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "手机号格式不正确")
        }
        if (!Regex("^[0-9]{6}$").matches(pwd)) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "取款密码必须为6位数字")
        }
        if (openAmount < BigDecimal.ONE) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "开卡金额不能低于1元")
        }
    }

    private fun validateAmount(amount: BigDecimal) {
        if (amount <= BigDecimal.ZERO) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "金额必须大于0")
        }
    }

    private fun requireSession(authorization: String?): SessionUser {
        if (authorization.isNullOrBlank() || !authorization.startsWith("Bearer ")) {
            throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "未登录，请先登录")
        }
        val token = authorization.removePrefix("Bearer ").trim()
        val session = sessions[token] ?: throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "登录已失效，请重新登录")
        if (session.expiresAt.isBefore(Instant.now())) {
            sessions.remove(token)
            throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "登录已过期，请重新登录")
        }
        return session
    }

    private fun assertOwner(userId: Long, cardNo: String) {
        val count = jdbcTemplate.queryForObject<Long>(
            "SELECT COUNT(1) FROM bank_schema.card_info WHERE card_no = ? AND user_id = ?",
            cardNo,
            userId,
        ) ?: 0L
        if (count == 0L) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "无权操作该银行卡")
        }
    }

    private fun extractMessage(e: DataAccessException): String {
        val raw = e.rootCause?.message ?: e.message ?: "数据库操作失败"
        return raw.replace("ERROR:", "").trim()
    }

    private fun encodeBase64(value: String): String =
        Base64.getEncoder().encodeToString(value.toByteArray(Charsets.UTF_8))
}

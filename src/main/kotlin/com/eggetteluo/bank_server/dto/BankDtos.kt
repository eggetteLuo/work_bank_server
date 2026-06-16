package com.eggetteluo.bank_server.dto

import com.fasterxml.jackson.annotation.JsonAlias
import java.math.BigDecimal
import java.time.OffsetDateTime

data class OpenAccountRequest(
    // 兼容前端可能传入的字段名 user_name
    @JsonAlias("user_name")
    val userName: String,
    // 兼容前端可能传入的字段名 id_card
    @JsonAlias("id_card")
    val idCard: String,
    // 兼容前端可能传入的字段名 phone_number
    @JsonAlias("phone_number")
    val phone: String,
    val address: String,
    // 兼容前端可能传入的字段名 open_money
    @JsonAlias("open_money")
    val openAmount: BigDecimal,
    // 兼容前端可能传入的字段名 withdrawPwd / withdraw_pwd
    @JsonAlias("withdrawPwd", "withdraw_pwd")
    val withdrawPassword: String,
    // 兼容前端可能传入的字段名 deposit_type_id
    @JsonAlias("deposit_type_id")
    val depositId: Long? = null,
)

data class OpenAccountResponse(
    val userId: Long,
    val cardNo: String,
)

data class LoginRequest(
    val cardNo: String,
    val password: String,
)

data class LoginResponse(
    val token: String,
    val userId: Long,
    val cardNo: String,
)

data class TradeRequest(
    val cardNo: String,
    val amount: BigDecimal,
    val remark: String? = null,
)

data class TransferRequest(
    val fromCardNo: String,
    val toCardNo: String,
    val amount: BigDecimal,
    val remark: String? = null,
)

data class BalanceResponse(
    val cardNo: String,
    val balance: BigDecimal,
    val isLost: Boolean,
)

data class TradeRecordView(
    val tradeId: Long,
    val tradeTime: OffsetDateTime,
    val tradeType: String,
    val cardNo: String,
    val amount: BigDecimal,
    val remark: String?,
)

data class TradeStatsResponse(
    val type: String,
    val count: Long,
    val totalAmount: BigDecimal,
)

data class NoTradeAccountView(
    val cardNo: String,
    val userName: String,
    val phone: String,
    val balance: BigDecimal,
)

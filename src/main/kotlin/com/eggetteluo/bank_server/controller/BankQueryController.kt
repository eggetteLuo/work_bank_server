package com.eggetteluo.bank_server.controller

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
import com.eggetteluo.bank_server.service.BankService
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.OffsetDateTime

@RestController
@RequestMapping("/api")
class BankQueryController(
    private val bankService: BankService,
) {
    // 服务连通性检测接口
    @GetMapping("/ping")
    fun ping(): Map<String, String> = mapOf("status" to "ok")

    // 开户接口：创建客户并生成银行卡
    @PostMapping("/accounts/open")
    fun openAccount(@RequestBody request: OpenAccountRequest): OpenAccountResponse = bankService.openAccount(request)

    // 登录接口：银行卡号 + 6位密码，成功后返回 token
    @PostMapping("/auth/login")
    fun login(@RequestBody request: LoginRequest): LoginResponse = bankService.login(request)

    // 余额查询接口：按卡号查询当前余额与挂失状态
    @GetMapping("/accounts/balance")
    fun balance(
        @RequestHeader(name = "Authorization", required = false) authorization: String?,
        @RequestParam cardNo: String,
    ): BalanceResponse = bankService.getBalance(authorization, cardNo)

    // 存款接口：调用数据库函数写入交易并更新余额
    @PostMapping("/trade/deposit")
    fun deposit(
        @RequestHeader(name = "Authorization", required = false) authorization: String?,
        @RequestBody request: TradeRequest,
    ): Map<String, Any> {
        val tradeId = bankService.deposit(authorization, request)
        return mapOf("message" to "存款成功", "tradeId" to tradeId)
    }

    // 取款接口：余额不足时会返回错误信息
    @PostMapping("/trade/withdraw")
    fun withdraw(
        @RequestHeader(name = "Authorization", required = false) authorization: String?,
        @RequestBody request: TradeRequest,
    ): Map<String, Any> {
        val tradeId = bankService.withdraw(authorization, request)
        return mapOf("message" to "取款成功", "tradeId" to tradeId)
    }

    // 转账接口：在数据库事务中完成扣款、入账与流水记录
    @PostMapping("/trade/transfer")
    fun transfer(
        @RequestHeader(name = "Authorization", required = false) authorization: String?,
        @RequestBody request: TransferRequest,
    ): Map<String, String> {
        val result = bankService.transfer(authorization, request)
        return mapOf("message" to result)
    }

    // 交易流水分页查询接口
    @GetMapping("/trade/records")
    fun tradeRecords(
        @RequestHeader(name = "Authorization", required = false) authorization: String?,
        @RequestParam cardNo: String,
        @RequestParam(defaultValue = "1") page: Int,
        @RequestParam(defaultValue = "10") size: Int,
    ): List<TradeRecordView> = bankService.queryTrades(authorization, cardNo, page, size)

    // 对账单查询接口：按时间区间查询指定卡号流水
    @GetMapping("/trade/statement")
    fun statement(
        @RequestHeader(name = "Authorization", required = false) authorization: String?,
        @RequestParam cardNo: String,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) start: OffsetDateTime,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) end: OffsetDateTime,
    ): List<TradeRecordView> = bankService.printStatement(authorization, cardNo, start, end)

    // 交易统计接口：统计时间段内存入/支取笔数与总金额
    @GetMapping("/stats/trade")
    fun stats(
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) start: OffsetDateTime,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) end: OffsetDateTime,
    ): List<TradeStatsResponse> = bankService.stats(start, end)

    // 未发生交易账户查询接口
    @GetMapping("/stats/no-trade-accounts")
    fun noTradeAccounts(): List<NoTradeAccountView> = bankService.noTradeAccounts()
}

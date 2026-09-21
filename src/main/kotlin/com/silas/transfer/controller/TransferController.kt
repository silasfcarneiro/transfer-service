package com.silas.transfer.controller

import com.silas.transfer.dto.request.CreateTransferRequest
import com.silas.transfer.dto.response.TransferResponse
import com.silas.transfer.service.TransferService
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/transfers")
class TransferController(
    private val transferService: TransferService
) {

    @PostMapping
    fun create(
        @RequestBody request: CreateTransferRequest,
        @RequestHeader("Idempotency-Key") idempotencyKey: String
    ): ResponseEntity<TransferResponse> {
        val transfer = transferService.transfer(request, idempotencyKey)
        val body = TransferResponse(transfer.id, transfer.status)
        return ResponseEntity.status(HttpStatus.CREATED).body(body)
    }
}
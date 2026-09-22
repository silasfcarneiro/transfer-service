package com.silas.transfer

import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import

@SpringBootTest
@Import(TestcontainersConfig::class)
class TransferServiceApplicationTests {

    @Test
    fun contextLoads() {
    }
}
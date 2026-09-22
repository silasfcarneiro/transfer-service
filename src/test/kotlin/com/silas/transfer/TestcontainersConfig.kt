package com.silas.transfer

import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.context.annotation.Bean
import org.testcontainers.containers.MongoDBContainer
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.kafka.KafkaContainer

@TestConfiguration(proxyBeanMethods = false)
class TestcontainersConfig {

    @Bean
    @ServiceConnection
    fun postgresContainer(): PostgreSQLContainer<*> =
        PostgreSQLContainer("postgres:16")

    @Bean
    @ServiceConnection
    fun mongoContainer(): MongoDBContainer =
        MongoDBContainer("mongo:7")

    // Kafka SEM @ServiceConnection - a conexão é injetada manualmente no teste
    @Bean
    fun kafkaContainer(): KafkaContainer =
        KafkaContainer("apache/kafka:3.8.0")
}
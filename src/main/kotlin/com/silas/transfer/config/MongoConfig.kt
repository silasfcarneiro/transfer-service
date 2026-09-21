package com.silas.transfer.config

import com.mongodb.MongoClientSettings
import com.mongodb.client.MongoClient
import com.mongodb.client.MongoClients
import org.bson.UuidRepresentation
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.mongodb.MongoDatabaseFactory
import org.springframework.data.mongodb.core.SimpleMongoClientDatabaseFactory

@Configuration
class MongoConfig {

    @Bean
    fun mongoClient(): MongoClient {
        val settings = MongoClientSettings.builder()
            .applyConnectionString(com.mongodb.ConnectionString("mongodb://localhost:27017/transfer"))
            .uuidRepresentation(UuidRepresentation.STANDARD)
            .build()
        return MongoClients.create(settings)
    }

    @Bean
    fun mongoDatabaseFactory(mongoClient: MongoClient): MongoDatabaseFactory =
        SimpleMongoClientDatabaseFactory(mongoClient, "transfer")
}
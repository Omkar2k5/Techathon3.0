package com.example.edunet.data.remote

import com.mongodb.ConnectionString
import com.mongodb.MongoClientSettings
import com.mongodb.ServerApi
import com.mongodb.ServerApiVersion
import com.mongodb.kotlin.client.coroutine.MongoClient
import com.mongodb.kotlin.client.coroutine.MongoDatabase

object MongoClientProvider {

    private const val CONNECTION_STRING =
        "mongodb://root:root123@ac-ywiif9y-shard-00-00.h1ehyle.mongodb.net:27017,ac-ywiif9y-shard-00-01.h1ehyle.mongodb.net:27017,ac-ywiif9y-shard-00-02.h1ehyle.mongodb.net:27017/?appName=Techathon&authSource=admin&replicaSet=atlas-uz8cvc-shard-0&tls=true"

    private val clientSettings = MongoClientSettings.builder()
        .applyConnectionString(ConnectionString(CONNECTION_STRING))
        .applyToSocketSettings { builder ->
            builder.connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
            builder.readTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
        }
        .applyToClusterSettings { builder ->
            builder.serverSelectionTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
        }
        .applyToSslSettings { builder ->
            builder.enabled(true)
            builder.invalidHostNameAllowed(true) // Required for explicit node IPs in Android
        }
        .serverApi(
            ServerApi.builder()
                .version(ServerApiVersion.V1)
                .build()
        )
        .build()

    private val client: MongoClient by lazy {
        MongoClient.create(clientSettings)
    }

    fun getDatabase(name: String = "edunet"): MongoDatabase = client.getDatabase(name)
}


package pl.lejdi.plannerkmp.core.network

import io.ktor.client.engine.HttpClientEngineFactory

expect fun httpClientEngine(): HttpClientEngineFactory<*>

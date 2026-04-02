package no.jamph.ragumami

import com.google.gson.Gson

object Routes {
    private data class RoutesConfig(
        val frontendUrl: String,
        val ragApiUrl: String,
        val ollamaUrl: String,
        val defaultModel: String? = null
    )

    private val config: RoutesConfig = Gson().fromJson(
        Routes::class.java.getResourceAsStream("/routes.json")!!.reader(),
        RoutesConfig::class.java
    )

    val frontendUrl: String   = config.frontendUrl
    val ragApiUrl: String     = config.ragApiUrl
    val ollamaUrl: String     = config.ollamaUrl
    val defaultModel: String? = config.defaultModel

    val frontendHost: String get() = frontendUrl.removePrefix("https://").removePrefix("http://").trimEnd('/')
    val ragApiHost: String   get() = ragApiUrl.removePrefix("https://").removePrefix("http://").trimEnd('/')
}

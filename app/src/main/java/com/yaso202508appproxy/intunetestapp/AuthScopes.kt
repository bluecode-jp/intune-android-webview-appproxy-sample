package com.yaso202508appproxy.intunetestapp

enum class AuthScopes(
    val scopes: List<String>
) {
    PROXY(listOf(BuildConfig.PROXY_SCOPE)),
    GRAPH(listOf("User.Read"));

    companion object {
        fun getAll() = entries.toList()
    }
}
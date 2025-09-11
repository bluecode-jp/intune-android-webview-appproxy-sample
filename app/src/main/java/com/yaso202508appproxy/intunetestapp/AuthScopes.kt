package com.yaso202508appproxy.intunetestapp

enum class AuthScopes(
    val displayName: String,
    val scopes: List<String>
) {
    PROXY("PROXY", listOf(BuildConfig.PROXY_SCOPE)),
    GRAPH("GRAPH", listOf("User.Read"));

    companion object {
        fun getAll() = entries.toList()
    }
}
package com.yaso202508appproxy.intunetestapp

import com.microsoft.identity.client.IAccount
import com.microsoft.identity.client.IAuthenticationResult
import java.lang.Exception
import java.text.SimpleDateFormat

interface AppLogger {
    fun info(msg: String)
    fun error(msg: String, exception: Exception? = null)
}

fun String.truncate() = "${this.take(10)}...${this.takeLast(10)}"

fun Boolean.isSuccessStr() = if (this) "success" else "fail"

fun IAuthenticationResult.toLog() = arrayOf(
    "- scope = ${this.scope.joinToString(" ")}",
    "- scheme = ${this.authenticationScheme}",
    "- accessToken = ${this.accessToken.truncate()}",
    "- expiresOn = ${SimpleDateFormat("yyyy/MM/dd HH:mm:ss").format(this.expiresOn)}",
).joinToString(System.lineSeparator())

fun IAccount.toLog() = arrayOf(
    "- id = ${this.id}",
    "- username = ${this.username}",
    "- authority = ${this.authority}",
    "- tenantId = ${this.tenantId}",
    "- idToken = ${this.idToken?.truncate()}",
).joinToString(System.lineSeparator())
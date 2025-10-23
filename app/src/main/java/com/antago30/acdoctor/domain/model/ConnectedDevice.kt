package com.antago30.acdoctor.domain.model

data class ConnectedDevice(
    val deviceId: String,
    val name: String,
    val latestMessage: String = "",
    val isActive: Boolean = false,
    val isError: Boolean = false
)
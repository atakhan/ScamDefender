package com.scamdefender.core.domain

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class RiskLevel {
    @SerialName("SAFE")
    SAFE,

    @SerialName("MONITORING")
    MONITORING,

    @SerialName("SUSPICIOUS")
    SUSPICIOUS,

    @SerialName("HIGH_RISK")
    HIGH_RISK,

    @SerialName("CRITICAL")
    CRITICAL,
}

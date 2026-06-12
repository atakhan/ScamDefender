package com.scamdefender.core.domain

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class AttackStage {
    @SerialName("STAGE_0")
    STAGE_0,

    @SerialName("STAGE_1")
    STAGE_1,

    @SerialName("STAGE_2")
    STAGE_2,

    @SerialName("STAGE_3")
    STAGE_3,

    @SerialName("STAGE_4")
    STAGE_4,

    @SerialName("STAGE_5")
    STAGE_5,

    @SerialName("STAGE_6")
    STAGE_6,
}

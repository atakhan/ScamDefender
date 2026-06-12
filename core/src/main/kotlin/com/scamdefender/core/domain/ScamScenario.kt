package com.scamdefender.core.domain

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class ScamScenario {
    @SerialName("bank_fraud_impersonation")
    BANK_FRAUD_IMPERSONATION,

    @SerialName("police_authority_scam")
    POLICE_AUTHORITY_SCAM,

    @SerialName("child_in_trouble")
    CHILD_IN_TROUBLE,

    @SerialName("investment_fraud")
    INVESTMENT_FRAUD,

    @SerialName("tech_support_scam")
    TECH_SUPPORT_SCAM,

    @SerialName("delivery_marketplace_scam")
    DELIVERY_MARKETPLACE_SCAM,

    @SerialName("financial_transfer_scam")
    FINANCIAL_TRANSFER_SCAM,
}

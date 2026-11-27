package com.ivy.data.db.entity

import androidx.annotation.Keep
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.ivy.base.kotlinxserilzation.KSerializerUUID
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.util.UUID

/**
 * One thing the user is saving up for.
 *
 * Replaces the single "savings goal" buffer, which could only ever express one
 * number for the whole wallet. People save for several things at once - a
 * phone and a monitor, not "savings" - and a single figure cannot say how far
 * along either of them is.
 *
 * [savedAmount] is held here rather than derived from transactions on purpose.
 * Money set aside for a goal usually sits in the same account as everything
 * else; it is an intention, not a separate balance, and inferring it from the
 * ledger would either require a dedicated account per goal or quietly claim
 * transactions the user never assigned.
 */
@Suppress("DataClassDefaultValues")
@Keep
@Serializable
@Entity(tableName = "savings_goals")
data class SavingsGoalEntity(
    @SerialName("name")
    val name: String,
    @SerialName("targetAmount")
    val targetAmount: Double,
    @SerialName("savedAmount")
    val savedAmount: Double,
    /** Null means "in the wallet's base currency". */
    @SerialName("currency")
    val currency: String?,
    @SerialName("colorArgb")
    val colorArgb: Int?,
    @SerialName("iconName")
    val iconName: String?,
    /** Epoch millis of a date the user wants it by; null for no deadline. */
    @SerialName("targetDateMillis")
    val targetDateMillis: Long?,
    @SerialName("archived")
    val archived: Boolean = false,
    @SerialName("orderNum")
    val orderNum: Double = 0.0,
    @PrimaryKey
    @SerialName("id")
    @Serializable(with = KSerializerUUID::class)
    val id: UUID = UUID.randomUUID(),
)

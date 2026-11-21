package com.ivy.design.l0_system

import com.ivy.design.system.colors.ResolvedColors

/**
 * Adapts the resolved user palette onto the legacy [IvyColors] interface.
 *
 * This bridge is the reason a custom colour scheme works at all: the large
 * majority of screens in this app still read their colours from `UI.colors`
 * (Home, Accounts, Categories, Reports, Settings, Onboarding, every modal), and
 * only the newer screens go through Material3. Driving both from one
 * [ResolvedColors] keeps them consistent.
 *
 * The interface has 15 members, so the adapter is cheap - and no screen needed
 * to change.
 */
fun ResolvedColors.asLegacyIvyColors(): IvyColors {
    val resolved = this
    return object : IvyColors {
        override val pure = resolved.surface
        override val pureInverse = resolved.onSurface
        override val gray = resolved.gray
        override val medium = resolved.surfaceVariant
        override val mediumInverse = if (resolved.isLight) {
            MediumBlack
        } else {
            MediumWhite
        }

        override val primary = resolved.primary.primary

        // `primary1` is the muted companion the legacy UI pairs with `primary`:
        // a dark tint on light backgrounds, a light tint on dark ones.
        override val primary1 = if (resolved.isLight) {
            resolved.primary.dark
        } else {
            resolved.primary.light
        }

        override val green = resolved.income.primary
        override val green1 = if (resolved.isLight) {
            resolved.income.extraLight
        } else {
            resolved.income.extraDark
        }

        override val orange = resolved.warning.primary
        override val orange1 = if (resolved.isLight) {
            resolved.warning.extraLight
        } else {
            resolved.warning.extraDark
        }

        override val red = resolved.expense.primary
        override val red1 = if (resolved.isLight) {
            resolved.expense.extraLight
        } else {
            resolved.expense.extraDark
        }
        override val red1Inverse = if (resolved.isLight) {
            resolved.expense.extraDark
        } else {
            resolved.expense.extraLight
        }

        override val isLight = resolved.isLight
    }
}

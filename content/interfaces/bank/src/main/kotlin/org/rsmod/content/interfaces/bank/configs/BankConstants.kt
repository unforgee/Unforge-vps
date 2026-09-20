@file:Suppress("ConstPropertyName")

package org.rsmod.content.interfaces.bank.configs

internal typealias bank_constants = BankConstants

object BankConstants {
    // rsprot 239's UpdateInvPartial pool is limited to 5713 transmitted slots.
    // A larger server-side array alone would crash when slot 5713 is updated.
    const val default_capacity = 5_713
    const val purchasable_capacity = 0
}

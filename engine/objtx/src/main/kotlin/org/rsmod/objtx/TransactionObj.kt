package org.rsmod.objtx

public data class TransactionObj(
    public val id: Int,
    public val count: Int = 1,
    public val vars: Int = 0,
    public val instanceId: Long = NO_INSTANCE,
) {
    public val hasVars: Boolean
        get() = vars > 0

    public val hasInstance: Boolean
        get() = instanceId != NO_INSTANCE

    public val hasState: Boolean
        get() = hasVars || hasInstance

    private companion object {
        private const val NO_INSTANCE: Long = 0L
    }
}

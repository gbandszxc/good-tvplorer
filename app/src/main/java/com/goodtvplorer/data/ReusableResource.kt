package com.goodtvplorer.data

internal data class ResourceLease<T>(val value: T, val generation: Long)

internal class ReusableResource<T>(
    private val factory: () -> T,
    private val usable: (T) -> Boolean,
    private val close: (T) -> Unit,
) {
    private var value: T? = null
    private var generation = 0L
    private var permanentlyClosed = false

    fun acquire(): ResourceLease<T> = synchronized(this) {
        check(!permanentlyClosed) { "资源已关闭" }
        value?.takeIf(usable)?.let { return@synchronized ResourceLease(it, generation) }
        value?.let(close)
        value = null
        factory().let {
            value = it
            generation++
            ResourceLease(it, generation)
        }
    }

    fun invalidate(failedGeneration: Long) = synchronized(this) {
        if (failedGeneration == generation) {
            value?.let(close)
            value = null
        }
    }

    fun close() = synchronized(this) {
        permanentlyClosed = true
        value?.let(close)
        value = null
    }
}

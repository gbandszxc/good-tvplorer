package com.goodtvplorer.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class ReusableResourceTest {
    @Test
    fun `acquire reuses live resource`() {
        var created = 0
        val resources = slot { FakeResource(++created) }

        val first = resources.acquire()
        val second = resources.acquire()

        assertSame(first.value, second.value)
        assertEquals(1, created)
    }

    @Test
    fun `stale failure cannot close replacement resource`() {
        var created = 0
        val resources = slot { FakeResource(++created) }
        val stale = resources.acquire()
        resources.invalidate(stale.generation)
        val current = resources.acquire()

        resources.invalidate(stale.generation)

        assertTrue(stale.value.closed)
        assertFalse(current.value.closed)
        assertEquals(2, created)
    }

    @Test
    fun `invalidate current closes it without creating a replacement`() {
        var created = 0
        val resources = slot { FakeResource(++created) }
        val current = resources.acquire().value

        resources.invalidateCurrentIf { true }

        assertTrue(current.closed)
        assertEquals(1, created)
        assertEquals(2, resources.acquire().value.id)
    }

    @Test
    fun `invalidate current leaves active resource alone`() {
        val resources = slot { FakeResource(1) }
        val current = resources.acquire().value

        resources.invalidateCurrentIf { false }

        assertFalse(current.closed)
        assertSame(current, resources.acquire().value)
    }

    @Test
    fun `permanent close prevents reacquire`() {
        val resources = slot { FakeResource(1) }
        val resource = resources.acquire().value

        resources.close()

        assertTrue(resource.closed)
        assertFailsWith<IllegalStateException> { resources.acquire() }
    }

    private fun slot(factory: () -> FakeResource) = ReusableResource(
        factory = factory,
        usable = { !it.closed },
        close = { it.closed = true },
    )

    private data class FakeResource(val id: Int, var closed: Boolean = false)
}

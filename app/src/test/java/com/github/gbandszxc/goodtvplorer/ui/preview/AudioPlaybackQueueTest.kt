package com.github.gbandszxc.goodtvplorer.ui.preview

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AudioPlaybackQueueTest {
    @Test
    fun `sequential advances until the end then stops`() {
        assertEquals(1, nextQueueIndex(3, 0, AudioPlayMode.Sequential))
        assertEquals(2, nextQueueIndex(3, 1, AudioPlayMode.Sequential))
        assertNull(nextQueueIndex(3, 2, AudioPlayMode.Sequential))
    }

    @Test
    fun `repeat one always returns the current index`() {
        assertEquals(0, nextQueueIndex(3, 0, AudioPlayMode.RepeatOne))
        assertEquals(2, nextQueueIndex(3, 2, AudioPlayMode.RepeatOne))
        assertEquals(0, nextQueueIndex(1, 0, AudioPlayMode.RepeatOne))
    }

    @Test
    fun `repeat all wraps from the last item back to the first`() {
        assertEquals(1, nextQueueIndex(3, 0, AudioPlayMode.RepeatAll))
        assertEquals(2, nextQueueIndex(3, 1, AudioPlayMode.RepeatAll))
        assertEquals(0, nextQueueIndex(3, 2, AudioPlayMode.RepeatAll))
        assertEquals(0, nextQueueIndex(1, 0, AudioPlayMode.RepeatAll))
    }

    @Test
    fun `shuffle never returns the current index and stays in range`() {
        repeat(50) { seed ->
            val random = Random(seed)
            val next = nextQueueIndex(5, 2, AudioPlayMode.Shuffle, random)
            assertTrue(next != null && next in 0 until 5)
            assertNotEquals(2, next)
        }
    }

    @Test
    fun `shuffle returns the only item when the queue has a single entry`() {
        assertEquals(0, nextQueueIndex(1, 0, AudioPlayMode.Shuffle, Random(7)))
    }

    @Test
    fun `empty queue or out of range index yields no next item`() {
        AudioPlayMode.entries.forEach { mode ->
            assertNull(nextQueueIndex(0, 0, mode))
            assertNull(nextQueueIndex(3, -1, mode))
            assertNull(nextQueueIndex(3, 3, mode))
        }
    }

    @Test
    fun `previous moves backwards and clamps to the first item`() {
        assertEquals(0, previousQueueIndex(3, 1))
        assertEquals(1, previousQueueIndex(3, 2))
        assertEquals(0, previousQueueIndex(3, 0))
        assertEquals(0, previousQueueIndex(1, 0))
    }

    @Test
    fun `previous is null for an empty queue or out of range index`() {
        assertNull(previousQueueIndex(0, 0))
        assertNull(previousQueueIndex(3, -1))
        assertNull(previousQueueIndex(3, 3))
    }

    @Test
    fun `play mode cycles through every mode and wraps around`() {
        var mode = AudioPlayMode.Sequential
        val visited = List(AudioPlayMode.entries.size) {
            mode.also { mode = mode.next() }
        }
        assertEquals(AudioPlayMode.entries.toList(), visited)
        assertEquals(AudioPlayMode.Sequential, mode)
    }
}

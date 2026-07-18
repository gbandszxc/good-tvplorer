package com.github.gbandszxc.goodtvplorer

import android.Manifest
import kotlin.test.Test
import kotlin.test.assertContentEquals

class MediaPermissionsTest {
    @Test
    fun requestsOnlyMissingPermissionsForEachPlatform() {
        assertContentEquals(
            arrayOf(
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VIDEO,
                Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED,
                Manifest.permission.READ_MEDIA_AUDIO,
            ),
            missingMediaPermissions(34, emptySet()),
        )
        assertContentEquals(
            arrayOf(Manifest.permission.READ_MEDIA_AUDIO),
            missingMediaPermissions(34, setOf(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)),
        )
        assertContentEquals(
            arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO),
            missingMediaPermissions(33, setOf(Manifest.permission.READ_MEDIA_AUDIO)),
        )
        assertContentEquals(
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE),
            missingMediaPermissions(32, emptySet()),
        )
    }
}

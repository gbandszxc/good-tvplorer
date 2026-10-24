package com.github.gbandszxc.goodtvplorer.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AudioEmbeddedPictureTest {
    @Test
    fun `提取 ID3v2_3 APIC 封面并保留 UTF-8 描述后的数据`() {
        val tag = id3Tag(
            version = 3,
            frames = listOf(
                "TIT2" to textFrame("歌曲"),
                "APIC" to apic(description = "封面描述", pictureType = 0, data = JPEG),
            ),
        )

        assertEquals(tag.size, AudioBinaryTags.id3TagLength(tag))
        val picture = assertNotNull(AudioBinaryTags.id3Picture(tag))
        assertEquals("image/jpeg", picture.mimeType)
        assertTrue(JPEG.contentEquals(picture.data))
    }

    @Test
    fun `提取 ID3v2_2 PIC 封面并把 JPG 格式映射为 MIME`() {
        val tag = id3Tag(version = 2, frames = listOf("PIC" to pic(format = "JPG", pictureType = 0, data = JPEG)))

        val picture = assertNotNull(AudioBinaryTags.id3Picture(tag))
        assertEquals("image/jpeg", picture.mimeType)
        assertTrue(JPEG.contentEquals(picture.data))
    }

    @Test
    fun `多张 APIC 时优先 front cover 类型`() {
        val tag = id3Tag(
            version = 3,
            frames = listOf(
                "APIC" to apic(description = "", pictureType = 0, data = OTHER),
                "APIC" to apic(description = "", pictureType = 3, data = JPEG),
            ),
        )

        val picture = assertNotNull(AudioBinaryTags.id3Picture(tag))
        assertTrue(JPEG.contentEquals(picture.data))
    }

    @Test
    fun `解析 FLAC PICTURE 块体中的图片与 MIME`() {
        val body = flacPictureBody(mime = "image/png", data = PNG)

        val picture = assertNotNull(AudioBinaryTags.flacPicture(body))
        assertEquals("image/png", picture.mimeType)
        assertTrue(PNG.contentEquals(picture.data))

        val blocks = AudioBinaryTags.flacBlockHeaders("fLaC".encodeToByteArray() + flacBlock(type = 6, body = body))
        assertEquals(listOf(AudioBinaryTags.Block(6, 8, 8 + body.size)), blocks)
    }

    @Test
    fun `块体超出前缀时仍返回块头供精确读取`() {
        // 块头声明 1000 字节，但前缀只带了 8 字节块体。
        val header = byteArrayOf(0x86.toByte(), 0x00, 0x03, 0xe8.toByte())
        val prefix = "fLaC".encodeToByteArray() + header + ByteArray(8)

        val blocks = AudioBinaryTags.flacBlockHeaders(prefix)

        assertEquals(listOf(AudioBinaryTags.Block(6, 8, 1008)), blocks)
    }

    @Test
    fun `截断或越界数据返回 null 且不抛异常`() {
        val valid = id3Tag(version = 3, frames = listOf("APIC" to apic("", 3, data = JPEG)))
        assertNull(AudioBinaryTags.id3Picture(valid.copyOfRange(0, valid.size - 4)))
        assertNull(AudioBinaryTags.id3Picture(byteArrayOf(0x49, 0x44, 0x33, 3, 0, 0, 0, 0)))
        assertNull(AudioBinaryTags.id3TagLength(byteArrayOf(0x49, 0x44, 0x33, 3, 0, 0, 0x80.toByte(), 0, 0, 0)))

        val body = flacPictureBody(mime = "image/jpeg", data = JPEG)
        assertNull(AudioBinaryTags.flacPicture(body.copyOfRange(0, body.size - 2)))
        assertNull(AudioBinaryTags.flacPicture(ByteArray(4)))
        assertNull(AudioBinaryTags.flacPicture(byteArrayOf(0, 0, 0, 3, 0, 0, 0)))
    }

    private fun id3Tag(version: Int, frames: List<Pair<String, ByteArray>>): ByteArray {
        val body = frames.fold(ByteArray(0)) { acc, (id, data) -> acc + id3Frame(version, id, data) }
        return byteArrayOf(
            0x49, 0x44, 0x33, version.toByte(), 0, 0,
            ((body.size shr 21) and 0x7f).toByte(),
            ((body.size shr 14) and 0x7f).toByte(),
            ((body.size shr 7) and 0x7f).toByte(),
            (body.size and 0x7f).toByte(),
        ) + body
    }

    private fun id3Frame(version: Int, id: String, data: ByteArray): ByteArray {
        val idBytes = id.encodeToByteArray()
        val size = if (version == 2) {
            byteArrayOf(((data.size shr 16) and 0xff).toByte(), ((data.size shr 8) and 0xff).toByte(), (data.size and 0xff).toByte())
        } else if (version == 4) {
            byteArrayOf(
                ((data.size shr 21) and 0x7f).toByte(),
                ((data.size shr 14) and 0x7f).toByte(),
                ((data.size shr 7) and 0x7f).toByte(),
                (data.size and 0x7f).toByte(),
            )
        } else {
            byteArrayOf((data.size shr 24).toByte(), (data.size shr 16).toByte(), (data.size shr 8).toByte(), data.size.toByte())
        }
        val flags = if (version == 2) ByteArray(0) else ByteArray(2)
        return idBytes + size + flags + data
    }

    private fun textFrame(text: String): ByteArray = byteArrayOf(3) + text.encodeToByteArray()

    private fun apic(description: String, pictureType: Int, data: ByteArray, mime: String = "image/jpeg"): ByteArray =
        byteArrayOf(3) + mime.encodeToByteArray() + 0 + byteArrayOf(pictureType.toByte()) + description.encodeToByteArray() + 0 + data

    private fun pic(format: String, pictureType: Int, data: ByteArray, description: String = ""): ByteArray =
        byteArrayOf(0) + format.encodeToByteArray() + byteArrayOf(pictureType.toByte()) + description.encodeToByteArray() + 0 + data

    private fun flacBlock(type: Int, body: ByteArray): ByteArray = byteArrayOf(
        (0x80 or type).toByte(),
        ((body.size shr 16) and 0xff).toByte(),
        ((body.size shr 8) and 0xff).toByte(),
        (body.size and 0xff).toByte(),
    ) + body

    private fun flacPictureBody(mime: String, data: ByteArray, description: String = "cover"): ByteArray {
        val mimeBytes = mime.encodeToByteArray()
        val descriptionBytes = description.encodeToByteArray()
        return u32(3) + u32(mimeBytes.size) + mimeBytes + u32(descriptionBytes.size) + descriptionBytes +
            u32(600) + u32(600) + u32(24) + u32(0) + u32(data.size) + data
    }

    private fun u32(value: Int): ByteArray = byteArrayOf(
        (value shr 24).toByte(),
        (value shr 16).toByte(),
        (value shr 8).toByte(),
        value.toByte(),
    )

    private companion object {
        val JPEG = byteArrayOf(0xff.toByte(), 0xd8.toByte(), 0xff.toByte(), 0xe0.toByte(), 1, 2, 3, 0xff.toByte(), 0xd9.toByte())
        val PNG = byteArrayOf(0x89.toByte(), 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a, 4, 5, 6)
        val OTHER = byteArrayOf(0xff.toByte(), 0xd8.toByte(), 9, 9, 0xff.toByte(), 0xd9.toByte())
    }
}

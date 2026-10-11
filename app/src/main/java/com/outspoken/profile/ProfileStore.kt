package com.outspoken.profile

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ImageDecoder
import android.net.Uri
import java.io.File

/**
 * Keeps the profile and its photo in the app's private folder, which other apps cannot read and
 * which is not in Downloads or the shared storage the logs use.
 */
class ProfileStore(private val context: Context) {

    private val file get() = File(context.filesDir, "profile.txt")
    private val photoFile get() = File(context.filesDir, "profile-photo.jpg")

    fun load(): PatientProfile = runCatching { decodeProfile(file.readText()) }.getOrDefault(PatientProfile())

    fun save(profile: PatientProfile) {
        val partial = File(context.filesDir, "profile.txt.part")
        partial.writeText(encodeProfile(profile))
        check(partial.renameTo(file) || run { file.delete(); partial.renameTo(file) }) { "could not save the profile" }
    }

    fun photo(): Bitmap? = photoFile.takeIf { it.exists() }?.let { BitmapFactory.decodeFile(it.path) }

    /**
     * Copies a picked image in, upright, scaled down to [PHOTO_PX] on its longer side. Says how big
     * the picture was and what was saved, for the log.
     *
     * The profile showed a strip of the picked photo over black (05:49:37 phone run). The first
     * version read it with BitmapFactory, which gives back a picture whose data ends early as if
     * it were whole, the rest left see-through, and see-through saves as black in a JPEG; it also
     * ignored the camera's rotation tag. ImageDecoder turns the picture upright and throws when
     * the data ends early, so a broken picture is reported in the log instead of saved.
     */
    fun savePhoto(uri: Uri): String {
        var size = ""
        val source = ImageDecoder.createSource(context.contentResolver, uri)
        val bitmap = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            size = "${info.size.width}x${info.size.height} ${info.mimeType}"
            // A software bitmap, so it can be scaled and drawn onto white below.
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            var sample = 1
            while (maxOf(info.size.width, info.size.height) / (sample * 2) >= PHOTO_PX) sample *= 2
            decoder.setTargetSampleSize(sample)
        }
        val saved = writePhoto(bitmap)
        return "picture $size, saved $saved"
    }

    /** Saves [bitmap] as the photo, scaled down like a picked one; says the saved size. */
    fun savePhoto(bitmap: Bitmap): String = writePhoto(bitmap)

    private fun writePhoto(bitmap: Bitmap): String {
        val scale = PHOTO_PX.toFloat() / maxOf(bitmap.width, bitmap.height)
        val sized = if (scale < 1f) Bitmap.createScaledBitmap(bitmap, (bitmap.width * scale).toInt(), (bitmap.height * scale).toInt(), true) else bitmap
        // A JPEG has no see-through pixels; left alone they turn black.
        val opaque = if (sized.hasAlpha()) {
            Bitmap.createBitmap(sized.width, sized.height, Bitmap.Config.ARGB_8888).also {
                Canvas(it).apply {
                    drawColor(Color.WHITE)
                    drawBitmap(sized, 0f, 0f, null)
                }
            }
        } else {
            sized
        }
        // Written aside and then swapped in, so the photo on show is never a half-written file.
        val partial = File(context.filesDir, "profile-photo.jpg.part")
        partial.outputStream().use { check(opaque.compress(Bitmap.CompressFormat.JPEG, 90, it)) { "could not encode the photo" } }
        check(partial.renameTo(photoFile) || run { photoFile.delete(); partial.renameTo(photoFile) }) { "could not save the photo" }
        return "${opaque.width}x${opaque.height}"
    }

    fun clearPhoto() {
        photoFile.delete()
    }

    private companion object {
        const val PHOTO_PX = 600
    }
}

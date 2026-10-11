package com.outspoken.profile

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
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

    /** Copies a picked image in, scaled down to [PHOTO_PX] on its longer side. */
    fun savePhoto(uri: Uri) {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= PHOTO_PX) sample *= 2
        val bitmap = context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: error("could not read the picture")
        val scale = PHOTO_PX.toFloat() / maxOf(bitmap.width, bitmap.height)
        val sized = if (scale < 1f) Bitmap.createScaledBitmap(bitmap, (bitmap.width * scale).toInt(), (bitmap.height * scale).toInt(), true) else bitmap
        photoFile.outputStream().use { sized.compress(Bitmap.CompressFormat.JPEG, 90, it) }
    }

    fun clearPhoto() {
        photoFile.delete()
    }

    private companion object {
        const val PHOTO_PX = 600
    }
}

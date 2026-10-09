package com.outspoken.setup

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * The models on this phone: which are downloaded, which one is chosen, and picking up finished
 * browser downloads. Seeing the Downloads folder needs "All files access", asked for once.
 */
class ModelShelf(private val context: Context) {

    private val prefs = context.getSharedPreferences("models", Context.MODE_PRIVATE)
    private val appDir: File? get() = context.getExternalFilesDir(null)

    /** The model to load at start and after a download; null means the largest file there. */
    var chosen: ModelChoice?
        get() = ModelCatalog.byFileName(prefs.getString(CHOSEN, null))
        set(value) = prefs.edit().putString(CHOSEN, value?.fileName).apply()

    /** The model the browser was last asked to download, loaded by itself when it arrives. */
    var waitingFor: ModelChoice?
        get() = ModelCatalog.byFileName(prefs.getString(WAITING, null))
        set(value) = prefs.edit().putString(WAITING, value?.fileName).apply()

    fun canSeeDownloads(): Boolean = Environment.isExternalStorageManager()

    fun askToSeeDownloads() {
        val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:${context.packageName}"))
        start(intent) || start(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
    }

    /** Opens the download in the phone's browser. False when no app can open the link. */
    fun download(choice: ModelChoice): Boolean {
        waitingFor = choice
        return start(Intent(Intent.ACTION_VIEW, Uri.parse(choice.url)))
    }

    fun installed(choice: ModelChoice): File? =
        appDir?.let { File(it, choice.fileName) }?.takeIf { it.length() == choice.sizeBytes }

    /** The file to load: the chosen model if it is here, otherwise the largest model file. */
    fun fileToLoad(): File? = chosen?.let(::installed) ?: findModelFile(appDir)

    /**
     * Moves finished downloads from Downloads into the app's folder and returns the models moved.
     * Moving within the same storage is instant; a copy is the fallback.
     */
    suspend fun collectDownloads(onMoving: (ModelChoice) -> Unit = {}): List<ModelChoice> = withContext(Dispatchers.IO) {
        val dir = appDir ?: return@withContext emptyList()
        if (!canSeeDownloads()) return@withContext emptyList()
        val downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val files = downloads.listFiles()?.map { it.name to it.length() } ?: return@withContext emptyList()
        ModelCatalog.all.mapNotNull { choice ->
            if (installed(choice) != null) return@mapNotNull null
            val name = ModelCatalog.findDownload(choice, files) ?: return@mapNotNull null
            onMoving(choice)
            val source = File(downloads, name)
            val target = File(dir, choice.fileName)
            if (!source.renameTo(target)) {
                val partial = File(dir, "${choice.fileName}.part")
                source.copyTo(partial, overwrite = true)
                check(partial.renameTo(target)) { "could not save ${choice.fileName}" }
                source.delete()
            }
            choice
        }
    }

    private fun start(intent: Intent): Boolean = try {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (e: ActivityNotFoundException) {
        false
    }

    private companion object {
        const val CHOSEN = "chosen"
        const val WAITING = "waiting"
    }
}

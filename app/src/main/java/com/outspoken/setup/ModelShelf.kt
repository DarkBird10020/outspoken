package com.outspoken.setup

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.Settings
import java.io.File

/**
 * The models on this phone and which one is chosen. Models stay where the browser saved them, in
 * Downloads, and are loaded from there: moving them into the app's own folder lost a 2.6 GB file
 * on the phone (03:20, a copy cut short when the app came back), and that folder is wiped when the
 * app is uninstalled. The choice is kept in Documents/Outspoken so it survives a reinstall too.
 * Seeing Downloads needs "All files access", asked for once per install.
 */
class ModelShelf(private val context: Context) {

    private val prefs = context.getSharedPreferences("models", Context.MODE_PRIVATE)
    private val appDir: File? get() = context.getExternalFilesDir(null)

    /** Where the browser saves the models, and where a picked model file is copied to. */
    val downloadsDir: File get() = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)

    private val choiceFile: File
        get() = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), "Outspoken/model.txt")

    /** The model picked in the app; null means none picked yet (see [ModelCatalog.atStart]). */
    var chosen: ModelChoice?
        get() {
            val saved = if (canSeeDownloads()) runCatching { choiceFile.readText().trim() }.getOrNull() else null
            return ModelCatalog.byFileName(saved ?: prefs.getString(CHOSEN, null))
        }
        set(value) {
            prefs.edit().putString(CHOSEN, value?.fileName).apply()
            if (canSeeDownloads()) {
                runCatching {
                    choiceFile.parentFile?.mkdirs()
                    choiceFile.writeText(value?.fileName.orEmpty())
                }
            }
        }

    /** The model the browser was last asked to download, loaded by itself when it arrives. */
    var waitingFor: ModelChoice?
        get() = ModelCatalog.byFileName(prefs.getString(WAITING, null))
        set(value) = prefs.edit().putString(WAITING, value?.fileName).apply()

    fun canSeeDownloads(): Boolean = Environment.isExternalStorageManager()

    /** True once per install: the first time a model could not be loaded for want of access. */
    fun shouldAskForAccess(): Boolean {
        if (canSeeDownloads() || prefs.getBoolean(ASKED, false)) return false
        prefs.edit().putBoolean(ASKED, true).apply()
        return true
    }

    fun askToSeeDownloads() {
        val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:${context.packageName}"))
        start(intent) || start(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
    }

    /** Opens the download in the phone's browser. False when no app can open the link. */
    fun download(choice: ModelChoice): Boolean {
        waitingFor = choice
        return start(Intent(Intent.ACTION_VIEW, Uri.parse(choice.url)))
    }

    private val check = ModelCheck.at(context.cacheDir)

    /**
     * The finished model file, in Downloads, or in the app's folder from older builds. A copy found
     * damaged before is skipped, so a fresh download saved as "name (1)" is used instead.
     */
    fun installed(choice: ModelChoice): File? {
        if (canSeeDownloads()) {
            val files = downloadsDir.listFiles()?.sortedBy { it.name }?.map { it.name to it.length() }.orEmpty()
            ModelCatalog.findDownloads(choice, files).map { File(downloadsDir, it) }
                .firstOrNull { !check.knownDamaged(it) }?.let { return it }
        }
        return appDir?.let { File(it, choice.fileName) }
            ?.takeIf { it.length() == choice.sizeBytes && !check.knownDamaged(it) }
    }

    /**
     * The file to load: the chosen model if it is here, then E2B, then any other model file. Files
     * picked with "Choose model file" are copied to Downloads under their own name, so Downloads is
     * searched too; before, a model that was not E2B or E4B was not found at the next start.
     */
    fun fileToLoad(): File? =
        ModelCatalog.atStart(chosen) { installed(it) != null }?.let(::installed)
            ?: findModelFile(appDir)
            ?: if (canSeeDownloads()) findModelFile(downloadsDir) else null

    private fun start(intent: Intent): Boolean = try {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (e: ActivityNotFoundException) {
        false
    }

    private companion object {
        const val CHOSEN = "chosen"
        const val WAITING = "waiting"
        const val ASKED = "askedForAccess"
    }
}

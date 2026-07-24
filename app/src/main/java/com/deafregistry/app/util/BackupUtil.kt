package com.deafregistry.app.util

import android.content.Context
import android.net.Uri
import com.deafregistry.app.data.local.AppDatabase
import java.io.File

/** Local on-device backup/restore of the Room SQLite database file (Storage Access Framework). */
object BackupUtil {

    private fun dbFile(context: Context): File = context.getDatabasePath(AppDatabase.DB_NAME)

    fun backupTo(context: Context, destination: Uri) {
        context.contentResolver.openOutputStream(destination)?.use { out ->
            dbFile(context).inputStream().use { input -> input.copyTo(out) }
        }
    }

    fun restoreFrom(context: Context, source: Uri) {
        AppDatabase.closeInstance()
        context.contentResolver.openInputStream(source)?.use { input ->
            dbFile(context).outputStream().use { out -> input.copyTo(out) }
        }
    }
}

package com.androidkris.ide.workspace

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings

/** Helpers for the all-files-access permission used to open arbitrary project folders. */
object StoragePermission {

    fun hasAllFilesAccess(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            // Pre-R relies on legacy external storage (requestLegacyExternalStorage).
            true
        }

    /** Intent to the system "All files access" screen for this app. */
    fun requestIntent(context: Context): Intent =
        Intent(
            Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
            Uri.parse("package:${context.packageName}"),
        )

    /** Default browse root (`/storage/emulated/0`). */
    fun externalRoot(): String = Environment.getExternalStorageDirectory().absolutePath
}

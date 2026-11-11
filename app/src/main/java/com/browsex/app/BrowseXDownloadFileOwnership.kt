package com.browsex.app

import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File

/** Deletes only destinations whose ownership can be established from BrowseX's provider or MediaStore. */
internal object BrowseXDownloadFileOwnership {
    fun delete(context: Context, uri: Uri): Boolean {
        return try {
            when (uri.scheme?.lowercase()) {
                "content" -> when (uri.authority) {
                    "${context.packageName}.files" ->
                        context.contentResolver.delete(uri, null, null) > 0

                    "media" -> {
                        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                            false
                        } else {
                            val ownerColumn = MediaStore.MediaColumns.OWNER_PACKAGE_NAME
                            val owner = context.contentResolver.query(
                                uri, arrayOf(ownerColumn), null, null, null
                            )?.use { cursor ->
                                if (cursor.moveToFirst()) {
                                    cursor.getString(cursor.getColumnIndexOrThrow(ownerColumn))
                                } else null
                            }
                            owner == context.packageName &&
                                context.contentResolver.delete(uri, null, null) > 0
                        }
                    }

                    else -> false
                }

                "file" -> {
                    val path = uri.path ?: return false
                    val file = File(path).canonicalFile
                    val downloads = Environment
                        .getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                        .canonicalFile
                    file.parentFile?.canonicalFile == downloads && file.isFile && file.delete()
                }

                else -> false
            }
        } catch (error: Exception) {
            android.util.Log.w("BrowseX", "Could not delete owned download file", error)
            false
        }
    }
}

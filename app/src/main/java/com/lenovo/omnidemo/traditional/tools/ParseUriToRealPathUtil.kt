package com.lenovo.omnidemo.traditional.tools


import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.util.Log
import androidx.annotation.RequiresApi

object ParseUriToRealPathUtil {

    @RequiresApi(Build.VERSION_CODES.Q)
    fun getRealPathFromUri(context: Context, uri: Uri): String? {
        return when {
            isExternalStorageDocument(uri) -> {
                Log.e("FileUtil", "getRealPathFromURI: isExternalStorageDocument")
                val docId = DocumentsContract.getDocumentId(uri)
                val split = docId.split(":".toRegex()).toTypedArray()
                val type = split[0]
                when (type) {
                    "primary" -> {
                        val storageDir = System.getenv("EXTERNAL_STORAGE")
                        val relativePath = if (split.size > 1) split[1] else ""
                        "${storageDir}/$relativePath"
                    }
                    else -> {
                        // Handle non-primary volumes
                        val documentId = DocumentsContract.getDocumentId(uri)
                        val relativePath = documentId.split(":")[1]
                        val volumes = context.contentResolver.query(
                            MediaStore.Files.getContentUri("external"),
                            arrayOf(MediaStore.MediaColumns.DATA),
                            MediaStore.MediaColumns.RELATIVE_PATH + " = ?",
                            arrayOf(relativePath),
                            null
                        )
                        volumes?.use {
                            if (it.moveToFirst()) {
                                it.getString(0)
                            } else {
                                null
                            }
                        }
                    }
                }
            }
            isDownloadsDocument(uri) -> {
                val fileName = getFilePath(context, uri)
                if (fileName != null) {
                    val path = context.getExternalFilesDir(null)?.absolutePath
                    "$path/Download/$fileName"
                } else {
                    null
                }
            }
            isMediaDocument(uri) -> {
                Log.e("FileUtil", "getRealPathFromURI: isMediaDocument")
                val docId = DocumentsContract.getDocumentId(uri)
                val split = docId.split(":".toRegex()).toTypedArray()
                val type = split[0]
                var contentUri: Uri? = null
                when (type) {
                    "image" -> contentUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                    "video" -> contentUri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                    "audio" -> contentUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
                }
                Log.e("FileUtil", "getRealPathFromURI: $contentUri")
                if (contentUri != null) {
                    val selection = "_id=?"
                    val selectionArgs = arrayOf(split[1])
                    return getDataColumn(context, contentUri, selection, selectionArgs)
                }
                return null
            }
            else -> getDataColumn(context, uri, null, null)
        }
    }

    private fun isExternalStorageDocument(uri: Uri): Boolean {
        return "com.android.externalstorage.documents" == uri.authority
    }

    private fun isDownloadsDocument(uri: Uri): Boolean {
        return "com.android.providers.downloads.documents" == uri.authority
    }

    private fun isMediaDocument(uri: Uri): Boolean {
        return "com.android.providers.media.documents" == uri.authority
    }

    private fun getDataColumn(
        context: Context, uri: Uri, selection: String?,
        selectionArgs: Array<String>?
    ): String? {
        var cursor: Cursor? = null
        val column = MediaStore.Files.FileColumns.DATA
        val projection = arrayOf(column)
        try {
            cursor = context.contentResolver.query(uri, projection, selection, selectionArgs, null)
            if (cursor != null && cursor.moveToFirst()) {
                val index = cursor.getColumnIndexOrThrow(column)
                return cursor.getString(index)
            }
        } catch (e: Exception) {
            Log.e("FileUtil", "getDataColumn: ", e)
        } finally {
            cursor?.close()
        }
        return null
    }


    private fun getFilePath(context: Context, uri: Uri): String? {
        val cursor = context.contentResolver.query(uri, null, null, null, null)
        cursor.use {
            if (it != null && it.moveToFirst()) {
                val displayNameIndex = it.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
                return if (displayNameIndex != -1) {
                    it.getString(displayNameIndex)
                } else {
                    null
                }
            }
        }
        return null
    }
}
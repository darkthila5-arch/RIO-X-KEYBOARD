package com.rio.board

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import java.io.File
import java.io.FileNotFoundException

/**
 * Tiny read-only content provider that hands the made stickers/GIFs to other apps
 * (needed for InputConnection.commitContent and for the share sheet).
 */
class RioFileProvider : ContentProvider() {

    companion object {
        fun authority(ctx: Context) = ctx.packageName + ".stickers"

        fun uriFor(ctx: Context, f: File): Uri =
            Uri.parse("content://" + authority(ctx) + "/" + Uri.encode(f.name))
    }

    override fun onCreate(): Boolean = true

    private fun fileFor(uri: Uri): File? {
        val ctx = context ?: return null
        val name = uri.lastPathSegment ?: return null
        val dir = StickerStore.dir(ctx)
        val f = File(dir, name)
        return try {
            if (f.isFile && f.canonicalFile.parentFile == dir.canonicalFile) f else null
        } catch (e: Exception) {
            null
        }
    }

    override fun getType(uri: Uri): String? = fileFor(uri)?.let { StickerStore.mimeOf(it) }

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        val f = fileFor(uri) ?: throw FileNotFoundException(uri.toString())
        return ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    override fun query(
        uri: Uri, projection: Array<String>?, selection: String?,
        selectionArgs: Array<String>?, sortOrder: String?
    ): Cursor? {
        val f = fileFor(uri) ?: return null
        val cols = projection ?: arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)
        val c = MatrixCursor(cols)
        c.addRow(Array<Any?>(cols.size) { i ->
            when (cols[i]) {
                OpenableColumns.DISPLAY_NAME -> f.name
                OpenableColumns.SIZE -> f.length()
                else -> null
            }
        })
        return c
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<String>?): Int = 0
}

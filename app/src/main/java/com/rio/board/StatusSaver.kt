package com.rio.board

import android.content.ContentValues
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import java.io.File

/**
 * Reads the WhatsApp ".Statuses" folder (the folder the user allowed in the RIO BOARD app)
 * and copies a chosen status into the gallery. WhatsApp puts a status in that folder
 * once you have watched it.
 */
object StatusSaver {
    const val PREF_TREE = "status_tree"

    class Item(val uri: Uri, val name: String, val mime: String, val modified: Long) {
        val isVideo: Boolean
            get() = mime.startsWith("video/") || name.lowercase().let { it.endsWith(".mp4") || it.endsWith(".3gp") || it.endsWith(".mkv") }
    }

    fun treeUri(prefs: SharedPreferences): Uri? {
        val s = prefs.getString(PREF_TREE, null) ?: return null
        return try {
            Uri.parse(s)
        } catch (e: Exception) {
            null
        }
    }

    private fun isMedia(name: String, mime: String): Boolean {
        if (mime.startsWith("image/") || mime.startsWith("video/")) return true
        val n = name.lowercase()
        return n.endsWith(".jpg") || n.endsWith(".jpeg") || n.endsWith(".png") || n.endsWith(".webp") ||
            n.endsWith(".mp4") || n.endsWith(".3gp") || n.endsWith(".mkv")
    }

    /** Blocking: call from a background thread. Newest first. */
    fun list(ctx: Context, tree: Uri): List<Item> {
        val out = ArrayList<Item>()
        val treeId = DocumentsContract.getTreeDocumentId(tree)
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, treeId)
        val cols = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED
        )
        ctx.contentResolver.query(children, cols, null, null, null)?.use { c ->
            while (c.moveToNext()) {
                val id = c.getString(0) ?: continue
                val name = c.getString(1) ?: continue
                val mime = c.getString(2) ?: ""
                if (mime == DocumentsContract.Document.MIME_TYPE_DIR) continue
                if (!isMedia(name, mime)) continue
                out.add(Item(DocumentsContract.buildDocumentUriUsingTree(tree, id), name, mime, c.getLong(3)))
            }
        }
        out.sortByDescending { it.modified }
        return out
    }

    /** Blocking: small preview picture of a status. */
    fun thumb(ctx: Context, item: Item): Bitmap? {
        return try {
            if (item.isVideo) {
                val r = MediaMetadataRetriever()
                try {
                    r.setDataSource(ctx, item.uri)
                    r.getFrameAtTime(0)
                } finally {
                    r.release()
                }
            } else {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                ctx.contentResolver.openInputStream(item.uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
                var sample = 1
                while (bounds.outWidth / sample > 320 || bounds.outHeight / sample > 320) sample *= 2
                val o = BitmapFactory.Options().apply { inSampleSize = sample }
                ctx.contentResolver.openInputStream(item.uri)?.use { BitmapFactory.decodeStream(it, null, o) }
            }
        } catch (e: Exception) {
            null
        } catch (e: OutOfMemoryError) {
            null
        }
    }

    /** Blocking: copies the status into Pictures/RIO Status or Movies/RIO Status. */
    fun saveToGallery(ctx: Context, item: Item): Boolean {
        return try {
            val ext = item.name.substringAfterLast('.', if (item.isVideo) "mp4" else "jpg").lowercase()
            val mime = if (item.mime.startsWith("image/") || item.mime.startsWith("video/")) item.mime
            else if (item.isVideo) "video/mp4" else "image/jpeg"
            val outName = "RIO_Status_" + System.currentTimeMillis() + "." + ext
            val input = ctx.contentResolver.openInputStream(item.uri) ?: return false
            input.use { ins ->
                if (Build.VERSION.SDK_INT >= 29) {
                    val cv = ContentValues()
                    cv.put(MediaStore.MediaColumns.DISPLAY_NAME, outName)
                    cv.put(MediaStore.MediaColumns.MIME_TYPE, mime)
                    cv.put(
                        MediaStore.MediaColumns.RELATIVE_PATH,
                        (if (item.isVideo) Environment.DIRECTORY_MOVIES else Environment.DIRECTORY_PICTURES) + "/RIO Status"
                    )
                    cv.put(MediaStore.MediaColumns.IS_PENDING, 1)
                    val collection = if (item.isVideo) MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                    else MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                    val dest = ctx.contentResolver.insert(collection, cv) ?: return false
                    val os = ctx.contentResolver.openOutputStream(dest) ?: return false
                    os.use { out -> ins.copyTo(out) }
                    val done = ContentValues()
                    done.put(MediaStore.MediaColumns.IS_PENDING, 0)
                    ctx.contentResolver.update(dest, done, null, null)
                } else {
                    val base = Environment.getExternalStoragePublicDirectory(
                        if (item.isVideo) Environment.DIRECTORY_MOVIES else Environment.DIRECTORY_PICTURES
                    )
                    val dir = File(base, "RIO Status")
                    dir.mkdirs()
                    val f = File(dir, outName)
                    f.outputStream().use { out -> ins.copyTo(out) }
                    MediaScannerConnection.scanFile(ctx, arrayOf(f.path), arrayOf(mime), null)
                }
            }
            true
        } catch (e: Exception) {
            false
        }
    }
}

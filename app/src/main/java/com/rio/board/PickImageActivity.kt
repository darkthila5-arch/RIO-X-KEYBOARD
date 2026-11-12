package com.rio.board

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import java.io.File

/** Lets the user pick a photo for the keyboard background. */
class PickImageActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val i = Intent(Intent.ACTION_GET_CONTENT)
        i.type = "image/*"
        i.addCategory(Intent.CATEGORY_OPENABLE)
        try {
            startActivityForResult(Intent.createChooser(i, "Choose keyboard background"), 11)
        } catch (e: Exception) {
            Toast.makeText(this, "No gallery app found", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == RESULT_OK) {
            val uri = data?.data
            if (uri != null && saveBackground(uri)) {
                getSharedPreferences("rio_board", MODE_PRIVATE).edit().putBoolean("bgon", true).apply()
                RioBoardService.instance?.reloadBackground()
                Toast.makeText(this, "Keyboard background set", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Could not load that image", Toast.LENGTH_SHORT).show()
            }
        }
        finish()
    }

    private fun saveBackground(uri: Uri): Boolean {
        return try {
            val bounds = BitmapFactory.Options()
            bounds.inJustDecodeBounds = true
            contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            var sample = 1
            while (bounds.outWidth / sample > 1400) sample *= 2
            val opts = BitmapFactory.Options()
            opts.inSampleSize = sample
            val bmp = contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
                ?: return false
            File(filesDir, "kb_bg.jpg").outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 88, it) }
            true
        } catch (e: Exception) {
            false
        }
    }
}

package com.mario.sentieri

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import java.io.File

object OfflineMaps {
    fun getTile(mbtilesPath: String, z: Int, x: Int, y: Int): ByteArray? {
        try {
            val file = File(mbtilesPath)
            if (!file.exists()) return null
            val db = SQLiteDatabase.openDatabase(file.absolutePath, null, SQLiteDatabase.OPEN_READONLY)
            val tmsY = (1 shl z) - 1 - y
            val cursor = db.rawQuery("SELECT tile_data FROM tiles WHERE zoom_level = ? AND tile_column = ? AND tile_row = ?", arrayOf(z.toString(), x.toString(), tmsY.toString()))
            var data: ByteArray? = null
            if (cursor.moveToFirst()) {
                data = cursor.getBlob(0)
            }
            cursor.close()
            db.close()
            return data
        } catch (_: Exception) {
            return null
        }
    }
}

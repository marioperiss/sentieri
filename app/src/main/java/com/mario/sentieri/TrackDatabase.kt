package com.mario.sentieri

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class TrackDatabase(context: Context) : SQLiteOpenHelper(context, "sentieri.db", null, 1) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS points (id INTEGER PRIMARY KEY AUTOINCREMENT, lat REAL, lng REAL, alt REAL)")
        db.execSQL("CREATE TABLE IF NOT EXISTS diary (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT, date TEXT, distance REAL, minutes INTEGER, altitude INTEGER, notes TEXT)")
        db.execSQL("CREATE TABLE IF NOT EXISTS pois (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT, type TEXT, lat REAL, lng REAL, altitude INTEGER, description TEXT)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS points")
        db.execSQL("DROP TABLE IF EXISTS diary")
        db.execSQL("DROP TABLE IF EXISTS pois")
        onCreate(db)
    }

    fun savePoints(points: List<TrackPoint>) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.execSQL("DELETE FROM points")
            points.forEach { p ->
                db.execSQL("INSERT INTO points (lat, lng, alt) VALUES (?, ?, ?)", arrayOf(p.lat, p.lng, p.altitude))
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
            db.close()
        }
    }

    fun loadPoints(): MutableList<TrackPoint> {
        val list = mutableListOf<TrackPoint>()
        val db = readableDatabase
        val cursor = db.rawQuery("SELECT lat, lng, alt FROM points", null)
        while (cursor.moveToNext()) {
            list.add(TrackPoint(cursor.getDouble(0), cursor.getDouble(1), cursor.getDouble(2)))
        }
        cursor.close()
        db.close()
        return list
    }

    fun saveDiary(diary: List<Hike>) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.execSQL("DELETE FROM diary")
            diary.forEach { h ->
                db.execSQL("INSERT INTO diary (name, date, distance, minutes, altitude, notes) VALUES (?, ?, ?, ?, ?, ?)",
                    arrayOf(h.name, h.date, h.distance, h.minutes, h.altitude, h.notes))
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
            db.close()
        }
    }

    fun loadDiary(): MutableList<Hike> {
        val list = mutableListOf<Hike>()
        val db = readableDatabase
        val cursor = db.rawQuery("SELECT name, date, distance, minutes, altitude, notes FROM diary", null)
        while (cursor.moveToNext()) {
            list.add(Hike(cursor.getString(0), cursor.getString(1), cursor.getDouble(2), cursor.getInt(3), cursor.getInt(4), cursor.getString(5)))
        }
        cursor.close()
        db.close()
        return list
    }

    fun savePois(pois: List<Poi>) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.execSQL("DELETE FROM pois")
            pois.forEach { p ->
                db.execSQL("INSERT INTO pois (name, type, lat, lng, altitude, description) VALUES (?, ?, ?, ?, ?, ?)",
                    arrayOf(p.name, p.type, p.lat, p.lng, p.altitude, p.description))
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
            db.close()
        }
    }

    fun loadPois(): MutableList<Poi> {
        val list = mutableListOf<Poi>()
        val db = readableDatabase
        val cursor = db.rawQuery("SELECT name, type, lat, lng, altitude, description FROM pois", null)
        while (cursor.moveToNext()) {
            list.add(Poi(cursor.getString(0), cursor.getString(1), cursor.getDouble(2), cursor.getDouble(3), cursor.getInt(4), cursor.getString(5)))
        }
        cursor.close()
        db.close()
        return list
    }
}

package com.example.slumpmetre

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

data class Record(
    val id: String,
    val ts: Long,
    val slumpMm: Int,
    val heightMm: Int,
    val baseMm: Int,
    val angleDeg: Double,
    val refMm: Double,
    val imagePath: String
)

class RecordStore(private val context: Context) {

    private val file = File(context.filesDir, "records.json")
    private val photoDir = File(context.filesDir, "photos").apply { mkdirs() }

    @Synchronized
    fun load(): List<Record> {
        if (!file.exists()) return emptyList()
        return runCatching {
            val arr = JSONArray(file.readText())
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                Record(
                    o.getString("id"), o.getLong("ts"), o.getInt("slumpMm"),
                    o.getInt("heightMm"), o.getInt("baseMm"), o.getDouble("angleDeg"),
                    o.getDouble("refMm"), o.getString("imagePath")
                )
            }.sortedByDescending { it.ts }
        }.getOrDefault(emptyList())
    }

    @Synchronized
    fun add(r: Record) {
        val arr = if (file.exists()) runCatching { JSONArray(file.readText()) }.getOrElse { JSONArray() } else JSONArray()
        arr.put(JSONObject().apply {
            put("id", r.id); put("ts", r.ts); put("slumpMm", r.slumpMm)
            put("heightMm", r.heightMm); put("baseMm", r.baseMm)
            put("angleDeg", r.angleDeg); put("refMm", r.refMm); put("imagePath", r.imagePath)
        })
        file.writeText(arr.toString())
    }

    fun persistPhoto(sourcePath: String): String {
        val target = File(photoDir, "${UUID.randomUUID()}.jpg")
        File(sourcePath).copyTo(target, overwrite = true)
        return target.absolutePath
    }
}

package com.example.todaytodo

import android.app.Activity
import android.app.Instrumentation
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import java.io.File
import java.time.LocalDate

/** Runs against isolated preferences and files, never the user's diary. */
class DiaryInstrumentation : Instrumentation() {
    override fun onCreate(arguments: Bundle?) { super.onCreate(arguments); start() }

    override fun onStart() {
        val root = File(targetContext.cacheDir, "diary-test-${System.nanoTime()}").apply { mkdirs() }
        val prefNames = mutableListOf<String>()
        fun isolated(name: String): Context = object : ContextWrapper(targetContext) {
            override fun getFilesDir() = File(root, name).apply { mkdirs() }
            override fun getSharedPreferences(key: String, mode: Int): android.content.SharedPreferences {
                val pref = root.name + name + key
                prefNames.add(pref)
                return super.getSharedPreferences(pref, mode)
            }
        }
        try {
            val first = isolated("first")
            val photos = DiaryPhotos(first)
            val ids = (1..3).map { index ->
                val source = File(root, "source$index.png")
                val bitmap = Bitmap.createBitmap(2000, 1000, Bitmap.Config.ARGB_8888)
                bitmap.eraseColor(android.graphics.Color.rgb(index * 70, 80, 120))
                source.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
                bitmap.recycle()
                photos.importPhoto(Uri.fromFile(source)).also { source.delete() }
            }
            check(ids.distinct().size == 3)
            check(ids.all { photos.file(it).isFile })
            photos.thumbnail(ids.first())!!.let { check(it.width <= 1600); it.recycle() }
            val store = TodoStore(first)
            val entry = DiaryEntry(LocalDate.of(2000, 1, 2), "한글 일기\n두 번째 줄", ids)
            store.saveDiaries(listOf(entry))
            check(TodoStore(first).loadDiaries() == listOf(entry))
            check(runCatching { store.saveDiaries(listOf(entry.copy(photos = ids + ids.first()))) }.isFailure)
            check(store.loadDiaries() == listOf(entry))
            val backup = store.createBackup(emptyList())
            val second = isolated("second")
            val restoredStore = TodoStore(second)
            val restored = restoredStore.readBackup(backup)
            restoredStore.saveDiaries(restored.diaries)
            check(restoredStore.loadDiaries() == listOf(entry))
            check(ids.all { DiaryPhotos(second).file(it).readBytes().contentEquals(photos.file(it).readBytes()) })
            val older = """{"app":"TodayTodo","version":3,"todos":[],"diaries":[{"date":"2000-01-01","content":"이전 일기"}]}"""
            check(restoredStore.readBackup(older).diaries.single().photos.isEmpty())
            check(runCatching { DiaryPhotos.validateId("../outside.jpg") }.isFailure)
            val invalid = org.json.JSONObject(backup).put("diaryPhotos", org.json.JSONObject()).toString()
            check(runCatching { restoredStore.readBackup(invalid) }.isFailure)
            store.saveDiaries(listOf(entry.copy(content = "", photos = ids.take(2))))
            check(store.loadDiaries().single().photos.size == 2)
            check(!photos.file(ids.last()).exists())
            store.saveDiaries(emptyList())
            check(store.loadDiaries().isEmpty())
            check(ids.none { photos.file(it).exists() })
            finish(Activity.RESULT_OK, Bundle().apply { putString("stream", "PASS: three photos, size limit, persistence, original deletion, backup round-trip, legacy backup, invalid backup, removal, photo-only diary, deletion") })
        } catch (error: Throwable) {
            finish(Activity.RESULT_CANCELED, Bundle().apply { putString("stream", error.stackTraceToString()) })
        } finally {
            prefNames.distinct().forEach { targetContext.deleteSharedPreferences(it) }
            root.deleteRecursively()
        }
    }
}

package com.folio.reader.data

import android.content.Context
import org.json.JSONArray
import java.io.File

/**
 * 最近/收藏的本地库:JSON 持久化到 filesDir/library.json,内存缓存。
 * 不用 Room/KSP——小列表,org.json 足够且零工具链风险。容错:读坏当空库,绝不崩。
 */
object LibraryStore {

    private const val FILE = "library.json"
    private const val CAP = 200

    private var cache: MutableList<FileRecord>? = null

    private fun file(ctx: Context) = File(ctx.filesDir, FILE)

    @Synchronized
    private fun load(ctx: Context) {
        if (cache != null) return
        val list = mutableListOf<FileRecord>()
        val f = file(ctx)
        if (f.exists()) {
            try {
                val arr = JSONArray(f.readText())
                for (i in 0 until arr.length()) list.add(FileRecord.fromJson(arr.getJSONObject(i)))
            } catch (_: Exception) {
                // 文件损坏 → 当作空库,不崩
            }
        }
        cache = list
    }

    @Synchronized
    private fun save(ctx: Context) {
        try {
            val arr = JSONArray()
            cache?.forEach { arr.put(it.toJson()) }
            file(ctx).writeText(arr.toString())
        } catch (_: Exception) {
        }
    }

    @Synchronized
    fun recent(ctx: Context): List<FileRecord> {
        load(ctx)
        return cache!!.sortedByDescending { it.lastOpened }
    }

    @Synchronized
    fun favorites(ctx: Context): List<FileRecord> = recent(ctx).filter { it.isFavorite }

    @Synchronized
    fun find(ctx: Context, id: String): FileRecord? {
        load(ctx)
        return cache!!.firstOrNull { it.id == id }
    }

    /** 打开文件时写入/更新。已存在则只更新时间/路径/大小,保留收藏与进度。 */
    @Synchronized
    fun upsert(ctx: Context, rec: FileRecord) {
        load(ctx)
        val existing = cache!!.firstOrNull { it.id == rec.id }
        if (existing != null) {
            existing.lastOpened = rec.lastOpened
            existing.progress = existing.progress // 保留
        } else {
            cache!!.add(rec)
            enforceCapacity(ctx)
        }
        save(ctx)
    }

    /** 仅刷新打开时间(从卡片再次打开时用)。 */
    @Synchronized
    fun touch(ctx: Context, id: String, now: Long) {
        load(ctx)
        cache!!.firstOrNull { it.id == id }?.let { it.lastOpened = now; save(ctx) }
    }

    /** 给文件加/去某标签(toggle)。 */
    @Synchronized
    fun toggleTag(ctx: Context, id: String, tag: String) {
        val t = tag.trim(); if (t.isEmpty()) return
        load(ctx)
        cache!!.firstOrNull { it.id == id }?.let { r ->
            r.tags = if (r.tags.contains(t)) r.tags - t else (r.tags + t).distinct()
            save(ctx)
        }
    }

    /** 当前所有标签(去重+排序)。 */
    @Synchronized
    fun allTags(ctx: Context): List<String> {
        load(ctx)
        return cache!!.flatMap { it.tags }.filter { it.isNotBlank() }.distinct().sortedBy { it.lowercase() }
    }

    /** 重命名标签:所有含旧名的文件改为新名(去重)。 */
    @Synchronized
    fun renameTag(ctx: Context, old: String, newName: String) {
        val n = newName.trim(); if (n.isEmpty() || n == old) return
        load(ctx)
        var changed = false
        cache!!.forEach { r ->
            if (r.tags.contains(old)) { r.tags = (r.tags - old + n).distinct(); changed = true }
        }
        if (changed) save(ctx)
    }

    /** 删除标签:从所有文件移除(不删文件)。 */
    @Synchronized
    fun deleteTag(ctx: Context, tag: String) {
        load(ctx)
        var changed = false
        cache!!.forEach { r ->
            if (r.tags.contains(tag)) { r.tags = r.tags - tag; changed = true }
        }
        if (changed) save(ctx)
    }

    @Synchronized
    fun toggleFavorite(ctx: Context, id: String) {
        load(ctx)
        cache!!.firstOrNull { it.id == id }?.let { it.isFavorite = !it.isFavorite; save(ctx) }
    }

    @Synchronized
    fun rename(ctx: Context, id: String, newName: String) {
        load(ctx)
        val idx = cache!!.indexOfFirst { it.id == id }
        if (idx >= 0) {
            cache!![idx] = cache!![idx].copy(name = newName)
            save(ctx)
        }
    }

    /** 编辑保存后更新文件大小(内容变了)。 */
    @Synchronized
    fun updateSize(ctx: Context, id: String, size: Long) {
        load(ctx)
        val idx = cache!!.indexOfFirst { it.id == id }
        if (idx >= 0) { cache!![idx] = cache!![idx].copy(size = size); save(ctx) }
    }

    @Synchronized
    fun updateProgress(ctx: Context, id: String, progress: Float) {
        load(ctx)
        cache!!.firstOrNull { it.id == id }?.let { it.progress = progress; save(ctx) }
    }

    @Synchronized
    fun delete(ctx: Context, id: String) {
        load(ctx)
        val rec = cache!!.firstOrNull { it.id == id } ?: return
        deleteStored(rec)
        cache!!.remove(rec)
        save(ctx)
    }

    private fun deleteStored(rec: FileRecord) {
        try {
            val f = File(rec.storedPath)
            if (f.isDirectory) f.deleteRecursively() else f.delete()
        } catch (_: Exception) {
        }
    }

    /** 超过上限时,从最旧的非收藏条目开始淘汰(连带删盘文件)。 */
    private fun enforceCapacity(ctx: Context) {
        val list = cache ?: return
        if (list.size <= CAP) return
        val removable = list.filter { !it.isFavorite }.sortedBy { it.lastOpened }
        var over = list.size - CAP
        for (rec in removable) {
            if (over <= 0) break
            deleteStored(rec)
            list.remove(rec)
            over--
        }
    }
}

package com.folio.reader.data

import com.folio.reader.file.FileType
import org.json.JSONArray
import org.json.JSONObject

/**
 * 库记录:一条"最近/收藏"条目。持久化为 JSON。
 * id 作为去重键(name|size);storedPath 是持久化文件(md/html)或解压目录(zip);
 * entryPath 仅 zip 用(入口相对路径)。
 */
data class FileRecord(
    val id: String,
    val name: String,
    val type: FileType,
    val size: Long,
    val storedPath: String,
    val entryPath: String?,
    var lastOpened: Long,
    var isFavorite: Boolean,
    var progress: Float,
    var tags: List<String> = emptyList(),
    // 书架/文档归类覆盖:"book"/"doc"/null(null=按类型自动判)
    var category: String? = null
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("type", type.name)
        put("size", size)
        put("storedPath", storedPath)
        put("entryPath", entryPath ?: JSONObject.NULL)
        put("lastOpened", lastOpened)
        put("isFavorite", isFavorite)
        put("progress", progress.toDouble())
        put("tags", JSONArray().apply { tags.forEach { put(it) } })
        put("category", category ?: JSONObject.NULL)
    }

    companion object {
        fun fromJson(o: JSONObject): FileRecord = FileRecord(
            id = o.getString("id"),
            name = o.getString("name"),
            type = runCatching { FileType.valueOf(o.getString("type")) }
                .getOrDefault(FileType.UNSUPPORTED),
            size = o.getLong("size"),
            storedPath = o.getString("storedPath"),
            entryPath = if (o.isNull("entryPath")) null else o.getString("entryPath"),
            lastOpened = o.getLong("lastOpened"),
            isFavorite = o.getBoolean("isFavorite"),
            progress = o.optDouble("progress", 0.0).toFloat(),
            tags = parseTags(o),
            category = if (o.isNull("category")) null else o.optString("category").ifBlank { null }
        )

        /** 标签解析 + 向后兼容:无 tags 但有旧 group → 迁移为单标签。 */
        private fun parseTags(o: JSONObject): List<String> = when {
            o.has("tags") && !o.isNull("tags") -> {
                val arr = o.getJSONArray("tags")
                (0 until arr.length()).map { arr.getString(it) }
                    .map { it.trim() }.filter { it.isNotEmpty() }.distinct()
            }
            !o.isNull("group") -> o.optString("group").trim().ifBlank { null }?.let { listOf(it) } ?: emptyList()
            else -> emptyList()
        }
    }
}

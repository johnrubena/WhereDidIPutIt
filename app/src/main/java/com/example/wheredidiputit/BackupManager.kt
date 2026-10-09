
package com.example.wheredidiputit

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

object BackupManager {

    private const val MAX_ENTRY_SIZE = 20L * 1024 * 1024
    private const val MAX_TOTAL_SIZE = 100L * 1024 * 1024

    /**
     * Creates one ZIP backup containing item data and available photos.
     */
    fun createBackup(
        context: Context,
        items: List<Item>,
        outputStream: OutputStream
    ) {
        ZipOutputStream(outputStream).use { zip ->
            val jsonItems = JSONArray()

            items.forEach { item ->
                val jsonItem = JSONObject().apply {
                    put("id", item.id)
                    put("name", item.name)
                    put("location", item.location)
                    put("specificPlace", item.specificPlace)
                    put("container", item.container)
                    put("notes", item.notes)
                    put("category", item.category)
                    put("createdAt", item.createdAt)
                    put("updatedAt", item.updatedAt)
                    put("isImportant", item.isImportant)
                }

                val photoPath = item.photoPath

                if (!photoPath.isNullOrBlank()) {
                    val photoFile = File(photoPath)

                    if (photoFile.isFile && photoFile.exists()) {
                        val extension = photoFile.extension
                            .takeIf {
                                it.matches(Regex("[A-Za-z0-9]{1,10}"))
                            } ?: "jpg"

                        val photoFileName = "item_${item.id}.$extension"

                        zip.putNextEntry(
                            ZipEntry("photos/$photoFileName")
                        )

                        photoFile.inputStream().buffered().use { input ->
                            input.copyTo(zip)
                        }

                        zip.closeEntry()

                        jsonItem.put("photoFileName", photoFileName)
                    }
                }

                jsonItems.put(jsonItem)
            }

            val backupJson = JSONObject().apply {
                put("formatVersion", 1)
                put("items", jsonItems)
            }

            zip.putNextEntry(ZipEntry("items.json"))
            zip.write(
                backupJson.toString(2).toByteArray(Charsets.UTF_8)
            )
            zip.closeEntry()
        }
    }

    /**
     * Reads a ZIP backup and extracts its photos into this app's
     * private photo directory. The returned items are not saved to Room.
     */
    fun restoreBackup(
        context: Context,
        inputStream: InputStream
    ): List<Item> {
        val entries = mutableMapOf<String, ByteArray>()
        var totalSize = 0L

        ZipInputStream(inputStream).use { zip ->
            var entry = zip.nextEntry

            while (entry != null) {
                if (!entry.isDirectory) {
                    val entryName = entry.name

                    val isItemsJson = entryName == "items.json"

                    val photoName = entryName
                        .removePrefix("photos/")

                    val isPhoto = entryName.startsWith("photos/") &&
                            photoName.matches(
                                Regex("[A-Za-z0-9_.-]{1,100}")
                            ) &&
                            !photoName.contains("..")

                    if (isItemsJson || isPhoto) {
                        require(entryName !in entries) {
                            "The backup contains duplicate file entries."
                        }

                        val buffer = ByteArrayOutputStream()
                        val chunk = ByteArray(8192)
                        var entrySize = 0L

                        while (true) {
                            val count = zip.read(chunk)
                            if (count == -1) break

                            entrySize += count
                            totalSize += count

                            require(entrySize <= MAX_ENTRY_SIZE) {
                                "A backup file entry is too large."
                            }

                            require(totalSize <= MAX_TOTAL_SIZE) {
                                "The backup is too large."
                            }

                            buffer.write(chunk, 0, count)
                        }

                        entries[entryName] = buffer.toByteArray()
                    }
                }

                zip.closeEntry()
                entry = zip.nextEntry
            }
        }

        val jsonBytes = entries["items.json"]
            ?: throw IllegalArgumentException(
                "This ZIP file does not contain items.json."
            )

        val json = JSONObject(
            String(jsonBytes, Charsets.UTF_8)
        )

        require(json.optInt("formatVersion", -1) == 1) {
            "This backup format is not supported."
        }

        val jsonItems = json.optJSONArray("items")
            ?: throw IllegalArgumentException(
                "The backup does not contain a valid item list."
            )

        val photoDirectory = File(context.filesDir, "item_photos")

        if (!photoDirectory.exists() && !photoDirectory.mkdirs()) {
            throw IllegalStateException(
                "Could not create the photo folder."
            )
        }

        val restoredItems = mutableListOf<Item>()
        val createdPhotoFiles = mutableListOf<File>()

        try {
            for (i in 0 until jsonItems.length()) {
                val obj = jsonItems.optJSONObject(i) ?: continue

                val name = obj.optString("name", "").trim()
                if (name.isEmpty()) continue

                var restoredPhotoPath: String? = null
                val photoFileName = obj.optString("photoFileName", "")

                if (
                    photoFileName.isNotBlank() &&
                    photoFileName.matches(
                        Regex("[A-Za-z0-9_.-]{1,100}")
                    ) &&
                    !photoFileName.contains("..")
                ) {
                    val photoBytes = entries["photos/$photoFileName"]

                    if (photoBytes != null) {
                        val extension = photoFileName
                            .substringAfterLast(".", "jpg")
                            .takeIf {
                                it.matches(Regex("[A-Za-z0-9]{1,10}"))
                            } ?: "jpg"

                        val photoFile = File.createTempFile(
                            "restored_",
                            ".$extension",
                            photoDirectory
                        )

                        photoFile.writeBytes(photoBytes)
                        createdPhotoFiles.add(photoFile)
                        restoredPhotoPath = photoFile.absolutePath
                    }
                }

                restoredItems.add(
                    Item(
                        id = obj.optInt("id", 0),
                        name = name,
                        location = obj.optString("location", ""),
                        specificPlace = obj.optString(
                            "specificPlace",
                            ""
                        ),
                        container = obj.optString("container", ""),
                        notes = obj.optString("notes", ""),
                        category = obj.optString("category", "Other"),
                        createdAt = obj.optLong(
                            "createdAt",
                            System.currentTimeMillis()
                        ),
                        updatedAt = obj.optLong(
                            "updatedAt",
                            System.currentTimeMillis()
                        ),
                        isImportant = obj.optBoolean(
                            "isImportant",
                            false
                        ),
                        photoPath = restoredPhotoPath
                    )
                )
            }

            return restoredItems
        } catch (exception: Exception) {
            createdPhotoFiles.forEach { it.delete() }
            throw exception
        }
    }
}

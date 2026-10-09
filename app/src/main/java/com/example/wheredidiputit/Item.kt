package com.example.wheredidiputit

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "items")
data class Item(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val name: String,
    val location: String,
    val specificPlace: String,
    val container: String,
    val notes: String,
    val category: String = "Other",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val isImportant: Boolean = false,
    val photoPath: String? = null
)
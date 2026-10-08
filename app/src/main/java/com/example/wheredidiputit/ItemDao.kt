package com.example.wheredidiputit

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import androidx.room.OnConflictStrategy

@Dao
interface ItemDao {

    @Insert
    suspend fun insertItem(item: Item)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrReplaceItem(item: Item)

    @Query("SELECT * FROM items ORDER BY id DESC")
    suspend fun getAllItems(): List<Item>

    @Update
    suspend fun updateItem(item: Item)

    @Delete
    suspend fun deleteItem(item: Item)
}
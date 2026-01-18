package com.saikumar.expensetracker.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "categories")
data class Category(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val type: CategoryType,
    val isEnabled: Boolean = true,
    val isDefault: Boolean = false,
    val icon: String = "default" )// Icon identifier for UI

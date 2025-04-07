package com.kanahia.entitymlkitdemo

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class EntityItem(
    val name: String,
    val type: String,
    val description: String
) : Parcelable
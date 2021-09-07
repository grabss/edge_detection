package com.sample.edgedetection

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.sample.edgedetection.model.Image

fun jsonToImageArray(json: String): ArrayList<Image> {
    val gson = Gson()
    val type = object : TypeToken<ArrayList<Image>>() {}.type
    return gson.fromJson(json, type)
}
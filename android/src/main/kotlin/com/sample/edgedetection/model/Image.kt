package com.sample.edgedetection.model

import android.graphics.Point
import java.io.Serializable

data class Image(
    val id: String,
    val b64: String,
    val originalB64: String
): Serializable
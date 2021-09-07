package com.sample.edgedetection.model

import android.graphics.Point
import java.io.Serializable

data class Image(
    val b64: String,
    val points: List<Point>? = null
): Serializable
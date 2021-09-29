package com.sample.edgedetection


import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

const val SPNAME = "images"
const val IMAGE_ARRAY = "imageArray"
const val SHOULD_UPLOAD = "shouldUpload"
const val CAN_EDIT_IMAGES = "canEditImages"
const val PHOTO_MAX_COUNT = 20
const val SCALE_SIZE = 1280

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
    }
}

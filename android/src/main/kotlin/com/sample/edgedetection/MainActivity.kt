package com.sample.edgedetection


import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

const val SPNAME = "images"
const val IMAGE_ARRAY = "imageArray"
const val DO_UPLOAD = "doUpload"

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
    }
}

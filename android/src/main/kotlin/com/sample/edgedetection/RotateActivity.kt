package com.sample.edgedetection

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Base64
import kotlinx.android.synthetic.main.activity_rotate.*
import org.json.JSONArray
import java.io.ByteArrayOutputStream

class RotateActivity : AppCompatActivity() {
    private lateinit var sp: SharedPreferences
    private lateinit var decodedImg: Bitmap
    private lateinit var jsons: JSONArray
    private var index = 0
    private val matrix = Matrix()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_rotate)

        sp = getSharedPreferences(SPNAME, Context.MODE_PRIVATE)

        // タップされた画像のインデックスを取得
        index = intent.getIntExtra(INDEX, 0)

        val images = sp.getString(SPKEY, null)
        jsons = JSONArray(images)
        val b64Image = jsons[index] as String

        setImage(b64Image)

        setBtnListener()
    }

    private fun setImage(b64Image: String) {
        val imageBytes = Base64.decode(b64Image, Base64.DEFAULT)
        decodedImg = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
        imageView.setImageBitmap(decodedImg)
    }

    private fun setBtnListener() {
        val matrix = Matrix()
        matrix.setRotate(90F, decodedImg.width/2F, decodedImg.height/2F)
        rotateBtn.setOnClickListener {
            decodedImg = Bitmap.createBitmap(decodedImg, 0, 0, decodedImg.width, decodedImg.height, matrix, true)
            imageView.setImageBitmap(decodedImg)
        }

        cancelBtn.setOnClickListener {
            navToImageListScrn()
        }

        decisionBtn.setOnClickListener {
            setUpdatedImage()
            navToImageListScrn()
        }
    }

    private fun setUpdatedImage() {
        val baos = ByteArrayOutputStream()
        decodedImg.compress(Bitmap.CompressFormat.JPEG, 100, baos)
        val b = baos.toByteArray()
        val updatedImg = Base64.encodeToString(b, Base64.DEFAULT)
        jsons.put(index, updatedImg)
        val editor = sp.edit()
        editor.putString(SPKEY, jsons.toString()).apply()
    }

    private fun navToImageListScrn() {
        val intent = Intent(this, ImageListActivity::class.java)
        intent.putExtra(INDEX, index)
        startActivityForResult(intent, 100)
        finish()
    }
}
package com.sample.edgedetection.crop

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.util.Base64
import android.util.Log
import com.google.gson.Gson
import com.sample.edgedetection.IMAGE_ARRAY
import com.sample.edgedetection.SPNAME
import com.sample.edgedetection.jsonToImageArray
import com.sample.edgedetection.model.Image
import com.sample.edgedetection.processor.Corners
import com.sample.edgedetection.processor.TAG
import com.sample.edgedetection.processor.cropPicture
import com.sample.edgedetection.scan.ScanPresenter
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import org.opencv.android.Utils
import org.opencv.core.Mat
import org.opencv.core.Point
import java.io.ByteArrayOutputStream

class BeforehandCropPresenter(val context: Context, private val corners: Corners, private val mat: Mat) {
    private var picture: Mat
    private var croppedPicture: Mat? = null
    private var croppedBitmap: Bitmap? = null
    private var sp: SharedPreferences = context.getSharedPreferences(SPNAME, Context.MODE_PRIVATE)
    private val gson = Gson()

    init {
        println("init BeforehandCropPresenter")
        picture = mat
    }

    fun cropAndSave(image: Image, scanPre: ScanPresenter? = null) {

        if (picture == null) {
            Log.i(TAG, "picture null?")
            return
        }

        if (croppedBitmap != null) {
            Log.i(TAG, "already cropped")
            return
        }

        Observable.create<Mat> {
            it.onNext(cropPicture(picture, corners.corners as List<Point>))
        }
            .subscribeOn(Schedulers.computation())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe { pc ->
                Log.i(TAG, "cropped picture: " + pc.toString())
                croppedPicture = pc
                croppedBitmap = Bitmap.createBitmap(pc.width(), pc.height(), Bitmap.Config.ARGB_8888)
                Utils.matToBitmap(pc, croppedBitmap)
                val baos = ByteArrayOutputStream()
                croppedBitmap!!.compress(Bitmap.CompressFormat.JPEG, 100, baos)
                val b = baos.toByteArray()
                val croppedB64 = Base64.encodeToString(b, Base64.DEFAULT)

                // サムネイル生成
                // ※単体表示用(0.5倍のさらに半分→オリジナルの0.25倍)
                val croppedThumbB64 = getThumbB64(croppedBitmap!!)

                val croppedImg = image.copy(b64 = croppedB64, thumbB64 = croppedThumbB64)
                saveImage(croppedImg)
                scanPre?.addImageToList(croppedImg)
            }
    }

    private fun getThumbB64(croppedBm: Bitmap): String {
        val thumbBm = Bitmap.createScaledBitmap(croppedBm, croppedBm.width/2, croppedBm.height/2, false)
        val thumbBaos = ByteArrayOutputStream()
        thumbBm.compress(Bitmap.CompressFormat.JPEG, 100, thumbBaos)
        val thumbB = thumbBaos.toByteArray()
        return Base64.encodeToString(thumbB, Base64.DEFAULT)
    }

    private fun saveImage(image: Image) {
        var images = mutableListOf<Image>()
        val json = sp.getString(IMAGE_ARRAY, null)
        if (json != null) {
            images = jsonToImageArray(json)
        }
        images.add(image)
        val editor = sp.edit()
        editor.putString(IMAGE_ARRAY, gson.toJson(images)).apply()
    }
}
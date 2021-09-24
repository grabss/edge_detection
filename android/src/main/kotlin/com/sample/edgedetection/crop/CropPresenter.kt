package com.sample.edgedetection.crop

import android.Manifest
import android.app.Activity
import android.content.ContentValues
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.os.Environment
import android.os.SystemClock
import android.provider.MediaStore
import android.util.Base64
import android.util.DisplayMetrics
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.app.ActivityCompat
import com.google.gson.Gson
import com.sample.edgedetection.IMAGE_ARRAY
import com.sample.edgedetection.SPNAME
import com.sample.edgedetection.SourceManager
import com.sample.edgedetection.jsonToImageArray
import com.sample.edgedetection.model.Image
import com.sample.edgedetection.processor.*
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import org.opencv.android.Utils
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.Size
import org.opencv.imgcodecs.Imgcodecs
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream


const val IMAGES_DIR = "smart_scanner"

class CropPresenter(val context: Context, private val iCropView: ICropView.Proxy, private val itemIndex: Int, private val picWidth: Int, private val picHeight: Int) {
    private var picture: Mat
    private var corners: Corners? = null
    private var croppedPicture: Mat? = null
    private var croppedBitmap: Bitmap? = null
    private val index = itemIndex
    private var sp: SharedPreferences = context.getSharedPreferences(SPNAME, Context.MODE_PRIVATE)
    private lateinit var images: ArrayList<Image>
    private lateinit var decodedImg: Bitmap
    private lateinit var imageBytes: ByteArray
    private lateinit var image: Image
    private val gson = Gson()

    init {
        println("CropPresenter")
        val bitmap = getOriginalImage()
        val mat = Mat(Size(bitmap.width.toDouble(), bitmap.height.toDouble()), CvType.CV_8U)
        mat.put(0, 0, imageBytes)
        picture = Imgcodecs.imdecode(mat, Imgcodecs.CV_LOAD_IMAGE_UNCHANGED)
        println("picture size: ${picture.size()}")
        println("picture width: ${picture.width()}")
        println("picture height: ${picture.height()}")
        val size = Size(bitmap.width.toDouble(), bitmap.height.toDouble())
        println("size.width: ${size.width}")
        println("size.height: ${size.height}")
        corners = processPicture(picture)
        println("corners: $corners")
        mat.release()

        println("=============")
        println("pictureSize ${picture?.size()}")
        println("picHeight $picHeight")
        println("picWidth $picWidth")
        println("picture.height ${picture.height()}")
        println("=============")

        // 画面サイズ取得
        val dm = DisplayMetrics()
        (context as Activity).windowManager.defaultDisplay.getMetrics(dm)

        // 画像表示領域の高さ
        val titleHeight = convertDpToPx(40f, context).toInt()
        val footerHeight = convertDpToPx(60f, context).toInt()
        val displayHeight = dm.heightPixels
        val areaHeight = displayHeight - titleHeight - footerHeight
        println("areaHeight: $areaHeight")

        // 画面表示領域の幅
        val areaWidth = dm.widthPixels
        println("areaWidth: $areaWidth")

        // 画像の高さ
        val imageHeight = bitmap.height
        println("imageHeight: $imageHeight")

        // 画像の幅
        val imageWidth = bitmap.width
        println("imageWidth: $imageWidth")

        // 幅に対する高さの比率を算出
        val viewAreaRatio = areaHeight / areaWidth.toDouble()
        println("viewAreaRatio: $viewAreaRatio")
        val imageRatio = imageHeight / imageWidth.toDouble()
        println("imageRatio: $imageRatio")

        // 表示領域の高さの方が高さの比率が高い場合、画像を幅いっぱいに表示
        if (imageRatio < viewAreaRatio) {
            iCropView.getPaper().layoutParams.width = 0
            iCropView.getPaper().layoutParams.height = ConstraintLayout.LayoutParams.WRAP_CONTENT
        }
        iCropView.getPaperRect().onCorners2Crop(corners, picture?.size(), picWidth, picHeight)
        Utils.matToBitmap(picture, bitmap, true)
        iCropView.getPaper().setImageBitmap(bitmap)
    }

    private fun getOriginalImage(): Bitmap {
        val json = sp.getString(IMAGE_ARRAY, null)
        images = jsonToImageArray(json!!)
        image = images[index]
        val b64Image = images[index].originalB64
        imageBytes = Base64.decode(b64Image, Base64.DEFAULT)
        decodedImg = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
        return decodedImg
    }

    fun crop() {
        if (picture == null) {
            Log.i(TAG, "picture null?")
            return
        }

        if (croppedBitmap != null) {
            Log.i(TAG, "already cropped")
            return
        }

        Observable.create<Mat> {
            it.onNext(cropPicture(picture, iCropView.getPaperRect().getCorners2Crop()))
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
                    val updatedB64 = Base64.encodeToString(b, Base64.DEFAULT)
                    val editedImage = image.copy(b64 = updatedB64)
                    images[index] = editedImage
                    val editor = sp.edit()
                    editor.putString(IMAGE_ARRAY, gson.toJson(images)).apply()
//                    iCropView.getCroppedPaper().setImageBitmap(croppedBitmap)
//                    iCropView.getPaper().visibility = View.GONE
//                    iCropView.getPaperRect().visibility = View.GONE
                }
    }
}
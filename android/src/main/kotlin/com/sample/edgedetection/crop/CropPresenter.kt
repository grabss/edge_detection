package com.sample.edgedetection.crop

import android.app.Activity
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.provider.BaseColumns
import android.util.DisplayMetrics
import android.util.Log
import androidx.constraintlayout.widget.ConstraintLayout
import com.sample.edgedetection.helper.DbHelper
import com.sample.edgedetection.helper.ImageTable
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


const val IMAGES_DIR = "smart_scanner"

class CropPresenter(val context: Context, private val iCropView: ICropView.Proxy, private val imageId: String, private val picWidth: Int, private val picHeight: Int) {
    private var picture: Mat
    private var corners: Corners? = null
    private var croppedPicture: Mat? = null
    private var croppedBm: Bitmap? = null
    private val id = imageId
    private lateinit var bm: Bitmap
    private lateinit var imageBytes: ByteArray
    private val dbHelper = DbHelper(context)

    init {
        println("CropPresenter")
        val bitmap = getOriginalBm()
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

    private fun getOriginalBm(): Bitmap {
        val db = dbHelper.readableDatabase
        val selection = "${BaseColumns._ID} = ?"
        val cursor = db.query(
            ImageTable.TABLE_NAME,
            arrayOf(ImageTable.COLUMN_NAME_ORIGINAL_BITMAP),
            selection,
            arrayOf(id),
            null,
            null,
            null
        )
        cursor.moveToFirst()
        val blob = cursor.getBlob(0)
        imageBytes = blob
        bm = BitmapFactory.decodeByteArray(blob, 0, blob.size)
        return bm
    }

    fun crop() {
        val db = dbHelper.writableDatabase
        if (picture == null) {
            Log.i(TAG, "picture null?")
            return
        }

        if (croppedBm != null) {
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
                    croppedBm = Bitmap.createBitmap(pc.width(), pc.height(), Bitmap.Config.ARGB_8888)
                    Utils.matToBitmap(pc, croppedBm)
                    val baos = ByteArrayOutputStream()
                    croppedBm!!.compress(Bitmap.CompressFormat.JPEG, 100, baos)
                    val thumbBm = Bitmap.createScaledBitmap(croppedBm!!, croppedBm!!.width/2, croppedBm!!.height/2, false)
                    val cropped = getBinaryFromBitmap(croppedBm!!)
                    val thumb = getBinaryFromBitmap(thumbBm)
                    val values = getContentValues(cropped, thumb)
                    val selection = "${BaseColumns._ID} = ?"
                    db.update(
                        ImageTable.TABLE_NAME,
                        values,
                        selection,
                        arrayOf(id)
                    )
                }
    }

    private fun getContentValues(croppedBinary: ByteArray, thumbBinary: ByteArray): ContentValues {
        return ContentValues().apply {
            put("${ImageTable.COLUMN_NAME_BITMAP}", croppedBinary)
            put("${ImageTable.COLUMN_NAME_THUMB_BITMAP}", thumbBinary)
        }
    }

    private fun getBinaryFromBitmap(bitmap: Bitmap): ByteArray{
        val byteArrayOutputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, byteArrayOutputStream)
        return byteArrayOutputStream.toByteArray()
    }
}
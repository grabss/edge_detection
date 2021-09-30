package com.sample.edgedetection.crop

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.sample.edgedetection.helper.DbHelper
import com.sample.edgedetection.helper.ImageTable
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
    private val dbHelper = DbHelper(context)

    init {
        println("init BeforehandCropPresenter")
        picture = mat
    }

    fun cropAndSave(scanPre: ScanPresenter? = null, originalBm: Bitmap) {

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
                val thumbBm = Bitmap.createScaledBitmap(croppedBitmap!!, croppedBitmap!!.width/2, croppedBitmap!!.height/2, false)
                if (scanPre == null) {
                    saveImageToDB(originalBm, thumbBm, croppedBitmap!!)
                } else {
                    scanPre.saveImageToDB(originalBm, thumbBm, croppedBitmap!!)
                }
            }
    }

    private fun saveImageToDB(originalBm: Bitmap, thumbBm: Bitmap, croppedBm: Bitmap) {
        val original = getBinaryFromBitmap(originalBm)
        val thumb = getBinaryFromBitmap(thumbBm)
        val cropped = getBinaryFromBitmap(croppedBm)
        val values = getContentValues(originBinary = original, thumbBinary = thumb, croppedBinary = cropped)
        val db = dbHelper.writableDatabase
        db.insert(ImageTable.TABLE_NAME, null, values)
    }

    //値セットを取得
    //@param URI
    //@return 値セット
    private fun getContentValues(originBinary: ByteArray, thumbBinary: ByteArray, croppedBinary: ByteArray): ContentValues {
        return ContentValues().apply {
            put("${ImageTable.COLUMN_NAME_ORIGINAL_BITMAP}", originBinary)
            put("${ImageTable.COLUMN_NAME_THUMB_BITMAP}", thumbBinary)
            put("${ImageTable.COLUMN_NAME_BITMAP}", croppedBinary)
            put("${ImageTable.COLUMN_NAME_ORDER_INDEX}", getMaxOrderIndex() + 1)
        }
    }

    private fun getMaxOrderIndex(): Int {
        val db = dbHelper.readableDatabase
        val order = "${ImageTable.COLUMN_NAME_ORDER_INDEX} DESC"
        val cursor = db.query(
            ImageTable.TABLE_NAME,
            arrayOf(ImageTable.COLUMN_NAME_ORDER_INDEX),
            null,
            null,
            null,
            null,
            order
        )
        return if (cursor.count == 0) {
            0
        } else {
            cursor.moveToFirst()
            val max = cursor.getInt(cursor.getColumnIndexOrThrow(ImageTable.COLUMN_NAME_ORDER_INDEX))
            println("max: $max")
            max
        }
    }

    //Binaryを取得
    //@param Bitmap
    //@return Binary
    private fun getBinaryFromBitmap(bitmap: Bitmap): ByteArray{
        val byteArrayOutputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, byteArrayOutputStream)
        return byteArrayOutputStream.toByteArray()
    }
}
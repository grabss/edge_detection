
package com.sample.edgedetection

import android.content.ContentValues
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.provider.BaseColumns
import android.util.Base64
import com.sample.edgedetection.base.ID
import com.sample.edgedetection.helper.DbHelper
import com.sample.edgedetection.helper.ImageTable
import kotlinx.android.synthetic.main.activity_rotate.*
import java.io.ByteArrayOutputStream
import kotlin.concurrent.thread

class RotateActivity : AppCompatActivity() {
    private lateinit var bm: Bitmap
    private var id = ""
    private val matrix = Matrix()
    private val dbHelper = DbHelper(this)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_rotate)
        setImage()
        setBtnListener()
    }

    override fun onDestroy() {
        super.onDestroy()
        dbHelper.close()
    }

    override fun onBackPressed() {
        toDisableBtns()
        navToImageListScrn()
    }

    private fun setImage() {
        id = intent.getStringExtra(ID).toString()
        val db = dbHelper.readableDatabase
        val selection = "${BaseColumns._ID} = ?"
        val cursor = db.query(
            ImageTable.TABLE_NAME,
            arrayOf(ImageTable.COLUMN_NAME_BITMAP),
            selection,
            arrayOf(id),
            null,
            null,
            null
        )
        cursor.moveToFirst()
        val blob = cursor.getBlob(0)
        bm = BitmapFactory.decodeByteArray(blob, 0, blob.size)
        imageView.setImageBitmap(bm)
    }

    private fun setBtnListener() {
        matrix.setRotate(90F, bm.width/2F, bm.height/2F)
        rotateBtn.setOnClickListener {
            bm = Bitmap.createBitmap(bm, 0, 0, bm.width, bm.height, matrix, true)
            imageView.setImageBitmap(bm)
        }

        cancelBtn.setOnClickListener {
            toDisableBtns()
            navToImageListScrn()
        }

        decisionBtn.setOnClickListener {
            toDisableBtns()
            thread {
                update()
                navToImageListScrn()
            }
        }
    }

    private fun toDisableBtns() {
        cancelBtn.isEnabled = false
        decisionBtn.isEnabled = false
    }

    private fun update() {
        val db = dbHelper.writableDatabase
        val thumbBm = Bitmap.createScaledBitmap(bm, bm.width/2, bm.height/2, false)
        val edited = getBinaryFromBitmap(bm)
        val thumb = getBinaryFromBitmap(thumbBm)
        val values = getContentValues(edited, thumb)
        val selection = "${BaseColumns._ID} = ?"
        db.update(
            ImageTable.TABLE_NAME,
            values,
            selection,
            arrayOf(id)
        )
    }

    private fun getContentValues(editedBinary: ByteArray, thumbBinary: ByteArray): ContentValues {
        return ContentValues().apply {
            put("${ImageTable.COLUMN_NAME_BITMAP}", editedBinary)
            put("${ImageTable.COLUMN_NAME_THUMB_BITMAP}", thumbBinary)
        }
    }

    private fun getBinaryFromBitmap(bitmap: Bitmap): ByteArray{
        val byteArrayOutputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, byteArrayOutputStream)
        return byteArrayOutputStream.toByteArray()
    }

    private fun navToImageListScrn() {
        val intent = Intent(this, ImageListActivity::class.java)
        intent.putExtra(ID, id)
        startActivityForResult(intent, 100)
        finish()
    }
}
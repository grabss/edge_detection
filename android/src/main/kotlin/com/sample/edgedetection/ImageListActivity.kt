package com.sample.edgedetection

import android.app.Activity
import android.app.AlertDialog
import android.content.*
import android.database.Cursor
import android.database.CursorWindow
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.ExifInterface
import android.net.Uri
import android.os.*
import android.provider.BaseColumns
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.util.Base64
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.tabs.TabLayoutMediator
import com.google.gson.Gson
import com.sample.edgedetection.base.*
import com.sample.edgedetection.crop.BeforehandCropPresenter
import com.sample.edgedetection.crop.CropActivity
import com.sample.edgedetection.helper.DbHelper
import com.sample.edgedetection.helper.ImageTable
import com.sample.edgedetection.model.Image
import kotlinx.android.synthetic.main.activity_image_list.*
import android.graphics.*
import com.sample.edgedetection.processor.processPicture
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.Size
import org.opencv.imgcodecs.Imgcodecs
import org.opencv.imgproc.Imgproc
import java.io.ByteArrayOutputStream
import java.lang.Exception
import kotlin.concurrent.thread
import org.opencv.android.Utils

class ImageListActivity : FragmentActivity(), ConfirmDialogFragment.BtnListener {

    private lateinit var viewPager: ViewPager2
    private lateinit var sp: SharedPreferences
    private lateinit var pagerAdapter: ImageListPagerAdapter
    private lateinit var images: ArrayList<Image>
    private val dialog = ConfirmDialogFragment()
    private var id = ""
    private val handler = Handler(Looper.getMainLooper())
    private val dbHelper = DbHelper(this)

    companion object {
        const val EXTRA_DATA = "EXTRA_DATA"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_image_list)
        sp = getSharedPreferences(SPNAME, Context.MODE_PRIVATE)

        // CursorWindowの設定値増加(上限500MB)
        val field = CursorWindow::class.java.getDeclaredField("sCursorWindowSize")
        field.isAccessible = true
        field.set(null, 500 * 1024 * 1024)

        // ギャラリーから選択した画像の加工処理が終わっているかを200ミリ秒毎に確認
        handler.post(result)
        toDisableBtns()
        setBtnListener()
    }

    override fun onDestroy() {
        super.onDestroy()
        dbHelper.close()
    }

    private val result = object: Runnable {
        override fun run() {
            val result = sp.getBoolean(CAN_EDIT_IMAGES, false)
            if (result) {
                images = getImagesFromDB()
                if (images.isEmpty()) {
                    finish()
                    return
                }
                pagerAdapter = ImageListPagerAdapter(images, applicationContext)

                // 編集画面からIDを取得
                id = intent.getStringExtra(ID).toString()

                val index = images.indexOfFirst {
                    it.id == id
                }

                viewPager = pager
                viewPager.adapter = pagerAdapter
                viewPager.post {
                    viewPager.setCurrentItem(index, false)
                }
                TabLayoutMediator(indicator, viewPager) { _, _ -> }.attach()
                toEnableBtns()
                return
            } else {
                handler.postDelayed(this, 200)
            }
        }
    }

    fun getImagesFromDB(): ArrayList<Image> {
        val db = dbHelper.readableDatabase
        val order = "${ImageTable.COLUMN_NAME_ORDER_INDEX} ASC"

        val cursor = db.query(
            ImageTable.TABLE_NAME,
            arrayOf(BaseColumns._ID, ImageTable.COLUMN_NAME_THUMB_BITMAP, ImageTable.COLUMN_NAME_ORDER_INDEX),
            null,
            null,
            null,
            null,
            order
        )
        val imageList = ArrayList<Image>()
        with(cursor) {
            while (moveToNext()) {
                val id = getLong(getColumnIndexOrThrow(BaseColumns._ID)).toString()
                val blob = getBlob(getColumnIndexOrThrow(ImageTable.COLUMN_NAME_THUMB_BITMAP))
                val thumbBm = BitmapFactory.decodeByteArray(blob, 0, blob.size)
                val image = Image(id = id, thumbBm = thumbBm)
                imageList.add(image)
            }
        }
        return imageList
    }

    private fun setBtnListener() {
        trashBtn.setOnClickListener {
            dialog.show(supportFragmentManager, "TAG")
        }
        cropBtn.setOnClickListener {
            navToCropScrn()
        }
        rotateBtn.setOnClickListener {
            navToRotateScrn()
        }
        contrastBtn.setOnClickListener {
            navToContrastScrn()
        }
        sortBtn.setOnClickListener {
            navToSortScrn()
        }
        uploadBtn.setOnClickListener {
            upload()
        }
        toDisableBtns()
    }

    private fun toEnableBtns() {
        trashBtn.isEnabled = true
        cropBtn.isEnabled = true
        rotateBtn.isEnabled = true
        contrastBtn.isEnabled = true
        sortBtn.isEnabled = true
        uploadBtn.isEnabled = true
    }

    private fun toDisableBtns() {
        trashBtn.isEnabled = false
        cropBtn.isEnabled = false
        rotateBtn.isEnabled = false
        contrastBtn.isEnabled = false
        sortBtn.isEnabled = false
        uploadBtn.isEnabled = false
    }

    private fun navToCropScrn() {
        val intent = Intent(this, CropActivity::class.java)
        val image = images[viewPager.currentItem]
        intent.putExtra(ID, image.id)
        startActivity(intent)
        finish()
    }

    // finish()で画像一覧画面をスタックから除外しないとエラー発生。
    // 画像をスタックに積んだままの遷移はNG。
    private fun navToRotateScrn() {
        val intent = Intent(this, RotateActivity::class.java)
        val image = images[viewPager.currentItem]
        intent.putExtra(ID, image.id)
        startActivity(intent)
        finish()
    }

    private fun navToContrastScrn() {
        val intent = Intent(this, ContrastActivity::class.java)
        val image = images[viewPager.currentItem]
        intent.putExtra(ID, image.id)
        startActivity(intent)
        finish()
    }

    private fun navToSortScrn() {
        val intent = Intent(this, SortActivity::class.java)
        val image = images[viewPager.currentItem]
        intent.putExtra(ID, image.id)
        startActivity(intent)
        finish()
    }

    // アップロード実行。Flutterに2次元配列のbyte配列を渡す
    private fun upload() {
        if (images.isEmpty()) {
            return
        }
        uploadBtn.isEnabled = false
        val editor = sp.edit()
        editor.putBoolean(SHOULD_UPLOAD, true).apply()
        val intent = Intent().apply {
            // String型で何らかの値を渡す必要がある
            putExtra(SCANNED_RESULT, "dummy")
            putExtra(EXTRA_DATA, "ここに行って欲しい")
        }
        setResult(RESULT_OK, intent)
        System.gc()
        finish()
    }

    override fun onDecisionClick() {
        val index = viewPager.currentItem
        val image = images[index]
        deleteRowFromDB(image.id)
        images.removeAt(index)
        pagerAdapter.updateData(images)
        viewPager.post {
            viewPager.setCurrentItem(index, true)
        }
        if (images.isEmpty()) {
            toDisableBtns()
            val isFromCamera = sp.getBoolean("isFromCamera", false)
            if (isFromCamera) {
                finish()
            } else {
                openGallery()
            }
        }
    }

    private fun openGallery() {
        val editor = sp.edit()
        editor.clear().apply()
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
            type = "image/*"
            putExtra(Intent.EXTRA_LOCAL_ONLY, true)
            setResult(Activity.RESULT_OK, this)
        }
        startActivityForResult(intent, REQUEST_GALLERY_TAKE)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        println("requestCode: $requestCode")
        println("data: $data")
        if (data == null) {
            finish()
            return
        }

        // カメラ撮影からのアップロード時。
        // ここでは何も処理させない。->2重登録になってしまうため
        if(requestCode == 101) {
            return
        }

        if (requestCode == REQUEST_GALLERY_TAKE && resultCode == RESULT_OK) {
            val editor = sp.edit()
            finish()
            if (data?.clipData != null) {
                println("複数選択")
                val intent = Intent(this, ImageListActivity::class.java)
                startActivityForResult(intent, 999)

                thread {
                    val count = data.clipData!!.itemCount
                    println("count: $count")
                    for (i in 0 until count) {
                        val imageUri = data.clipData!!.getItemAt(i).uri
                        val byte = this.contentResolver.openInputStream(imageUri)?.use { it.readBytes() }
                        var bitmap = BitmapFactory.decodeByteArray(byte, 0, byte!!.size)

                        // 短辺が最大1280ピクセルになるようにリサイズ
                        var w = bitmap.width
                        var h = bitmap.height
                        var aspect = 1.0
                        if (w < h) {
                            aspect = h / w.toDouble()
                        } else if (h < w) {
                            aspect = w / h.toDouble()
                        }

                        if (w == h && SCALE_SIZE < w) {
                            w = SCALE_SIZE
                            h = SCALE_SIZE
                        } else if ((w < h) && (SCALE_SIZE < w)) {
                            w = SCALE_SIZE
                            h = (SCALE_SIZE * aspect).toInt()
                        } else if ((h < w) && (SCALE_SIZE < h)) {
                            h = SCALE_SIZE
                            w = (SCALE_SIZE * aspect).toInt()
                        }
                        val mat = Mat(Size(w.toDouble(), h.toDouble()), CvType.CV_8U)

                        bitmap = Bitmap.createScaledBitmap(bitmap, w, h, true)
                        grayScale(mat, bitmap)
                        val path = getPathFromUri(this, imageUri)
                        val exif = ExifInterface(path!!)
                        val rotatedBm = when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_UNDEFINED)) {
                            ExifInterface.ORIENTATION_ROTATE_90 -> rotate(bitmap, 90F)
                            ExifInterface.ORIENTATION_ROTATE_180 -> rotate(bitmap, 180F)
                            ExifInterface.ORIENTATION_ROTATE_270 -> rotate(bitmap, 270F)
                            ExifInterface.ORIENTATION_NORMAL -> bitmap
                            else -> bitmap
                        }
                        mat.release()
                        val thumbBm = Bitmap.createScaledBitmap(rotatedBm, rotatedBm.width / 3, rotatedBm.height / 3, false)
                        saveImageToDB(originalBm = rotatedBm, thumbBm = thumbBm, croppedBm = rotatedBm)
                    }
                    editor.putBoolean(CAN_EDIT_IMAGES, true).apply()
                }
            } else if (data?.data != null) {
                println("単体選択")
                val intent = Intent(this, ImageListActivity::class.java)
                startActivityForResult(intent, 999)

                thread {
                    // 単体選択時
                    val imageUri = data.data!!
                    val byte = this.contentResolver.openInputStream(imageUri)?.use { it.readBytes() }
                    var bitmap = BitmapFactory.decodeByteArray(byte, 0, byte!!.size)
                    var w = bitmap.width
                    var h = bitmap.height
                    var aspect = 1.0
                    if (w < h) {
                        aspect = h / w.toDouble()
                    } else if (h < w) {
                        aspect = w / h.toDouble()
                    }

                    if (w == h && SCALE_SIZE < w) {
                        w = SCALE_SIZE
                        h = SCALE_SIZE
                    } else if ((w < h) && (SCALE_SIZE < w)) {
                        w = SCALE_SIZE
                        h = (SCALE_SIZE * aspect).toInt()
                    } else if ((h < w) && (SCALE_SIZE < h)) {
                        h = SCALE_SIZE
                        w = (SCALE_SIZE * aspect).toInt()
                    }
                    val mat = Mat(Size(w.toDouble(), h.toDouble()), CvType.CV_8U)

                    bitmap = Bitmap.createScaledBitmap(bitmap, w, h, true)
                    grayScale(mat, bitmap)
                    val path = getPathFromUri(this, imageUri)
                    val exif = ExifInterface(path!!)
                    val rotatedBm = when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_UNDEFINED)) {
                        ExifInterface.ORIENTATION_ROTATE_90 -> rotate(bitmap, 90F)
                        ExifInterface.ORIENTATION_ROTATE_180 -> rotate(bitmap, 180F)
                        ExifInterface.ORIENTATION_ROTATE_270 -> rotate(bitmap, 270F)
                        ExifInterface.ORIENTATION_NORMAL -> bitmap
                        else -> bitmap
                    }
                    mat.release()
                    val thumbBm = Bitmap.createScaledBitmap(rotatedBm, rotatedBm.width / 3, rotatedBm.height / 3, false)
                    saveImageToDB(originalBm = rotatedBm, thumbBm = thumbBm, croppedBm = rotatedBm)
                    editor.putBoolean(CAN_EDIT_IMAGES, true).apply()
                }
            }
        }
    }

    private fun getPathFromUri(context: Context, uri: Uri): String? {
        val isAfterKitKat = Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT
        // DocumentProvider
        Log.e("getPathFromUri", "uri:" + uri.authority!!)
        if (isAfterKitKat && DocumentsContract.isDocumentUri(context, uri)) {
            if ("com.android.externalstorage.documents" == uri.authority) {// ExternalStorageProvider
                val docId = DocumentsContract.getDocumentId(uri)
                val split = docId.split(":".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                val type = split[0]
                if ("primary".equals(type, ignoreCase = true))
                {
                    return (Environment.getExternalStorageDirectory().path + "/" + split[1])
                } else
                {
                    return  "/stroage/" + type + "/" + split[1]
                }
            } else if ("com.android.providers.downloads.documents" == uri.authority) {// DownloadsProvider
                val id = DocumentsContract.getDocumentId(uri)
                val contentUri = ContentUris.withAppendedId(
                    Uri.parse("content://downloads/public_downloads"), java.lang.Long.valueOf(id)
                )
                return getDataColumn(context, contentUri, null, null)
            } else if ("com.android.providers.media.documents" == uri.authority) {// MediaProvider
                val docId = DocumentsContract.getDocumentId(uri)
                val split = docId.split(":".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                var contentUri: Uri? = MediaStore.Files.getContentUri("external")
                val selection = "_id=?"
                val selectionArgs = arrayOf(split[1])
                return getDataColumn(context, contentUri, selection, selectionArgs)
            }
        } else if ("content".equals(uri.scheme!!, ignoreCase = true)) {//MediaStore
            return getDataColumn(context, uri, null, null)
        } else if ("file".equals(uri.scheme!!, ignoreCase = true)) {// File
            return uri.path
        }
        return null
    }

    private fun getDataColumn(
        context: Context, uri: Uri?, selection: String?,
        selectionArgs: Array<String>?
    ): String? {
        var cursor: Cursor? = null
        val projection = arrayOf(MediaStore.Files.FileColumns.DATA)
        try {
            cursor = context.contentResolver.query(
                uri!!, projection, selection, selectionArgs, null
            )
            if (cursor != null && cursor.moveToFirst()) {
                val cindex = cursor.getColumnIndexOrThrow(projection[0])
                return cursor.getString(cindex)
            }
        } finally {
            if (cursor != null)
                cursor.close()
        }
        return null
    }

    private fun grayScale(mat: Mat, bm: Bitmap) {
        Utils.bitmapToMat(bm, mat)
        Imgproc.cvtColor(mat, mat, Imgproc.COLOR_RGB2GRAY)
        Utils.matToBitmap(mat, bm)
    }

    private fun rotate(bm: Bitmap, angle: Float): Bitmap {
        val matrix = Matrix()
        matrix.postRotate(angle)
        return Bitmap.createBitmap(
            bm,
            0,
            0,
            bm.width,
            bm.height,
            matrix,
            true
        )
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

    private fun deleteRowFromDB(id: String) {
        val db = dbHelper.writableDatabase
        db.delete(ImageTable.TABLE_NAME, "${BaseColumns._ID} = ?", arrayOf(id))
    }

    // ダイアログのキャンセルボタンタップ時に処理を加える場合はここに記述
    override fun onCancelClick() {
    }

    private inner class ImageListPagerAdapter(images: ArrayList<Image>, context: Context) : RecyclerView.Adapter<PagerViewHolder>() {
        var images = images
        val context = context

        // 要素数
        override fun getItemCount(): Int = images.size

        override fun getItemId(position: Int): Long {
            return images[position].hashCode().toLong()
        }

        // 画像削除
        fun updateData(newImages: ArrayList<Image>) {
            try {
                // 2回notifyDataSetChanged()を実行しないと、pager部分でエラーになる
                images = ArrayList()
                notifyDataSetChanged()
                images = newImages
                notifyDataSetChanged()
            } catch(e: Exception) {
                print(e)
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PagerViewHolder =
            PagerViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.fragment_image_list, parent, false), context)

        override fun onBindViewHolder(holder: PagerViewHolder, position: Int) {
            holder.bind(images[position])
        }
    }

    class PagerViewHolder(itemView: View, context: Context) : RecyclerView.ViewHolder(itemView) {
        private val imageView: ImageView = itemView.findViewById(R.id.imageView)
        private val dbHelper = DbHelper(itemView.context)

        fun bind(image: Image) {
            val highQualityBm = getHighQualityBm(image)
            imageView.setImageBitmap(highQualityBm)
        }

        private fun getHighQualityBm(image: Image): Bitmap {
            val db = dbHelper.readableDatabase
            val selection = "${BaseColumns._ID} = ?"
            val cursor = db.query(
                ImageTable.TABLE_NAME,
                arrayOf(ImageTable.COLUMN_NAME_BITMAP, ImageTable.COLUMN_NAME_ORDER_INDEX),
                selection,
                arrayOf(image.id),
                null,
                null,
                null
            )
            cursor.moveToFirst()
            val blob = cursor.getBlob(0)
            return BitmapFactory.decodeByteArray(blob, 0, blob.size)
        }
    }
}
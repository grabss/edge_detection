package com.sample.edgedetection.scan

import android.content.ContentValues
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.*
import android.hardware.Camera
import android.hardware.Camera.ShutterCallback
import android.media.MediaPlayer
import android.util.Base64
import android.util.Log
import android.view.SurfaceHolder
import android.widget.Toast
import com.google.gson.Gson
import com.sample.edgedetection.*
import com.sample.edgedetection.base.SCALE_SIZE
import com.sample.edgedetection.crop.BeforehandCropPresenter
import com.sample.edgedetection.helper.DbHelper
import com.sample.edgedetection.helper.ImageTable
import com.sample.edgedetection.model.Image
import com.sample.edgedetection.processor.Corners
import com.sample.edgedetection.processor.processPicture
import io.reactivex.Observable
import io.reactivex.Scheduler
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.Size
import org.opencv.imgcodecs.Imgcodecs
import org.opencv.imgproc.Imgproc
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors


class ScanPresenter constructor(private val context: Context, private val iView: IScanView.Proxy, private val scanActv: ScanActivity) :
    SurfaceHolder.Callback, Camera.PictureCallback, Camera.PreviewCallback {
    private val TAG: String = "ScanPresenter"
    private var mCamera: Camera? = null
    private var param: Camera.Parameters? = null
    private val mSurfaceHolder: SurfaceHolder = iView.getSurfaceView().holder
    private val executor: ExecutorService
    private val proxySchedule: Scheduler
    private var isBusy: Boolean = false
    private var soundSilence: MediaPlayer = MediaPlayer()
    private var matrix: Matrix
    private val dbHelper = DbHelper(context)

    init {
        mSurfaceHolder.addCallback(this)
        executor = Executors.newSingleThreadExecutor()
        proxySchedule = Schedulers.from(executor)
        soundSilence = MediaPlayer.create(this.context, R.raw.silence)
        matrix = Matrix()
        matrix.postRotate(90F)
    }

    fun start() {
        println("start")
        mCamera?.startPreview() ?: Log.i(TAG, "camera null")
    }

    fun stop() {
        mCamera?.stopPreview() ?: Log.i(TAG, "camera null")
    }

    fun shut() {
        isBusy = true
        Log.i(TAG, "try to focus")
        mCamera?.takePicture(ShutterCallback {
            soundSilence.start()
        }, null, this)
        mCamera?.enableShutterSound(false)
        //MediaActionSound().play(MediaActionSound.SHUTTER_CLICK)
    }

    fun updateCamera() {
        if (null == mCamera) {
            return
        }
        mCamera?.stopPreview()
        try {
            mCamera?.setPreviewDisplay(mSurfaceHolder)
        } catch (e: IOException) {
            e.printStackTrace()
            return
        }
        mCamera?.setPreviewCallback(this)
        mCamera?.startPreview()
    }

    fun initCamera() {
        try {
            mCamera = Camera.open(Camera.CameraInfo.CAMERA_FACING_BACK)
        } catch (e: RuntimeException) {
            e.stackTrace
            Toast.makeText(context, "cannot open camera, please grant camera", Toast.LENGTH_SHORT)
                .show()
            return
        }

        param = mCamera?.parameters

        // 露光の最大値と最小値を取得して、スライダーにセット
        val maxExposure = param?.maxExposureCompensation
        scanActv.setSlider(maxExposure)

        val size = getMaxResolution()
        println("sizeWidth: ${size?.width}")
        println("sizeHeight: ${size?.height}")
        param?.setPreviewSize(size?.width ?: 1920, size?.height ?: 1080)
        val display = iView.getDisplay()
        val point = Point()
        display.getRealSize(point)
        val displayWidth = minOf(point.x, point.y)
        println("displayWidth: $displayWidth")
        val displayHeight = maxOf(point.x, point.y)
        println("displayHeight: $displayHeight")
        val displayRatio = displayWidth.div(displayHeight.toFloat())
        println("displayRatio: $displayRatio")
        val previewRatio = size?.height?.div(size.width?.toFloat()) ?: displayRatio
        println("previewRatio: $previewRatio")
        if (displayRatio > previewRatio) {
            println("displayRatio > previewRatio")
            val surfaceParams = iView.getSurfaceView().layoutParams
            println("surfaceParams: $surfaceParams")
            println("surfaceParams height: ${surfaceParams.height}")
            println("surfaceParams width: ${surfaceParams.width}")
            surfaceParams.height = (displayHeight / displayRatio * previewRatio).toInt()
            println("surfaceParams height2: ${surfaceParams.height}")
            iView.getSurfaceView().layoutParams = surfaceParams
        }

        val supportPicSize = mCamera?.parameters?.supportedPictureSizes

        // 端末ごとの撮影可能サイズ
        if (supportPicSize != null) {
            for(support in supportPicSize) {
                println("support width: ${support.width}、support height: ${support.height}")
            }
        }
        supportPicSize?.sortByDescending { it.width.times(it.height) }
        var pictureSize = supportPicSize?.find {
            it.height.toFloat().div(it.width.toFloat()) - previewRatio < 0.01
        }
        println("picture size width: ${pictureSize?.width}")
        println("picture size height: ${pictureSize?.height}")

        if (null == pictureSize) {
            pictureSize = supportPicSize?.get(0)
        }

        if (null == pictureSize) {
            Log.e(TAG, "can not get picture size")
        } else {
            param?.setPictureSize(pictureSize.width, pictureSize.height)
        }
        val pm = context.packageManager
        if (pm.hasSystemFeature(PackageManager.FEATURE_CAMERA_AUTOFOCUS)) {
            param?.focusMode = Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE
            Log.d(TAG, "enabling autofocus")
        } else {
            Log.d(TAG, "autofocus not available")
        }
        param?.flashMode = Camera.Parameters.FLASH_MODE_OFF
        mCamera?.parameters = param
        mCamera?.setDisplayOrientation(90)
    }

    fun toggleFlashMode() {
        if(param?.flashMode == Camera.Parameters.FLASH_MODE_ON) {
            param?.flashMode = Camera.Parameters.FLASH_MODE_OFF
        } else {
            param?.flashMode = Camera.Parameters.FLASH_MODE_ON
        }
        mCamera?.parameters = param
    }

    fun setExposure(value: Int) {
        param?.exposureCompensation = value
        mCamera?.parameters = param
    }

    override fun surfaceCreated(p0: SurfaceHolder) {
        initCamera()
    }

    override fun surfaceChanged(p0: SurfaceHolder, p1: Int, p2: Int, p3: Int) {
        updateCamera()
    }

    override fun surfaceDestroyed(p0: SurfaceHolder) {
        synchronized(this) {
            mCamera?.stopPreview()
            mCamera?.setPreviewCallback(null)
            mCamera?.release()
            mCamera = null
        }
    }

    override fun onPictureTaken(p0: ByteArray?, p1: Camera?) {
        Log.i(TAG, "on picture taken")
        Observable.just(p0)
            .subscribeOn(proxySchedule)
            .subscribe {
                val pictureSize = p1?.parameters?.pictureSize
                Log.i(TAG, "picture size: " + pictureSize.toString())
                Log.i(TAG, "picture size width: " + pictureSize?.width)
                Log.i(TAG, "picture size height: " + pictureSize?.height)

                val footerSpace = pictureSize?.width?.div(10) ?: 550

                val aspect = pictureSize?.width?.div(pictureSize?.height.toDouble())
                if (SCALE_SIZE < pictureSize?.height!!) {
                    pictureSize?.height = SCALE_SIZE
                    pictureSize?.width = (SCALE_SIZE * aspect!!).toInt()
                }
                var bitmap = BitmapFactory.decodeByteArray(p0, 0, p0!!.size)

                println("bitmapWidth: ${bitmap.width}")
                println("bitmapHeight: ${bitmap.height}")
                bitmap = Bitmap.createScaledBitmap(bitmap, pictureSize?.width!!, pictureSize?.height!!, true)
                println("bitmapWidth2: ${bitmap.width}")
                println("bitmapHeight2: ${bitmap.height}")
                bitmap = Bitmap.createBitmap(bitmap, 0, 0, pictureSize?.width!! - footerSpace, pictureSize?.height!!, null, true)
                println("bitmapWidth3: ${bitmap.width}")
                println("bitmapHeight3: ${bitmap.height}")
                val mat = Mat(
                    Size(
                        pictureSize?.width?.minus(footerSpace)?.toDouble() ?: 1920.toDouble(),
                        pictureSize?.height?.toDouble() ?: 1080.toDouble()
                    ), CvType.CV_8U
                )
                grayScale(mat, bitmap)
                println("mat size width: ${mat.size().width}")
                println("mat size height: ${mat.size().height}")
                mat.put(0, 0, p0)

                val pic = Imgcodecs.imdecode(mat, Imgcodecs.CV_LOAD_IMAGE_UNCHANGED)
                Core.rotate(pic, pic, Core.ROTATE_90_CLOCKWISE)
                SourceManager.corners = processPicture(pic)
                mat.release()

                // 矩形編集画面に遷移
//                (context as Activity).startActivityForResult(
//                    Intent(
//                        context,
//                        CropActivity::class.java
//                    ), REQUEST_CODE
//                )
                saveImage(bitmap)
                isBusy = false
                start()
            }
    }

    private fun grayScale(mat: Mat, bm: Bitmap) {
        Utils.bitmapToMat(bm, mat)
        Imgproc.cvtColor(mat, mat, Imgproc.COLOR_RGB2GRAY)
        Utils.matToBitmap(mat, bm)
    }

    private fun saveImage(bm: Bitmap) {
        // 画像を回転
        val rotatedBm = Bitmap.createBitmap(
            bm,
            0,
            0,
            bm.width,
            bm.height,
            matrix,
            true
        )

        val baos = ByteArrayOutputStream()
        rotatedBm.compress(Bitmap.CompressFormat.JPEG, 80, baos)
        val thumbBm = Bitmap.createScaledBitmap(rotatedBm, rotatedBm.width/3, rotatedBm.height/3, false)
        val b = baos.toByteArray()
        val updatedMat = Mat(Size(rotatedBm.width.toDouble(), rotatedBm.height.toDouble()), CvType.CV_8U)
        updatedMat.put(0, 0, b)
        val editMat = Imgcodecs.imdecode(updatedMat, Imgcodecs.CV_LOAD_IMAGE_UNCHANGED)
        val corners = processPicture(editMat)

        // 矩形が取得できた場合、一覧に表示させる画像をクロップ済みのものにする
        if (corners != null) {
            val beforeCropPresenter = BeforehandCropPresenter(context, corners, editMat)
            beforeCropPresenter.cropAndSave(scanPre = this, originalBm = rotatedBm)
        } else {
            saveImageToDB(originalBm = rotatedBm, thumbBm = thumbBm, croppedBm = rotatedBm)
        }
    }

    fun saveImageToDB(originalBm: Bitmap, thumbBm: Bitmap, croppedBm: Bitmap) {
        val original = getBinaryFromBitmap(originalBm)
        val thumb = getBinaryFromBitmap(thumbBm)
        val cropped = getBinaryFromBitmap(croppedBm)
        val values = getContentValues(originBinary = original, thumbBinary = thumb, croppedBinary = cropped)
        val db = dbHelper.writableDatabase
        db.insert(ImageTable.TABLE_NAME, null, values)
        scanActv.updateCount()
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

    override fun onPreviewFrame(p0: ByteArray?, p1: Camera?) {
        if (isBusy) {
            return
        }
//        Log.i(TAG, "on process start")
        isBusy = true
        try {
            Observable.just(p0)
                .observeOn(proxySchedule)
                .doOnError {}
                .subscribe({
                    Log.i(TAG, "start prepare paper")
                    val parameters = p1?.parameters
                    val width = parameters?.previewSize?.width
                    println("previewSizeWidth: $width")
                    val height = parameters?.previewSize?.height
                    println("previewSizeHeight: $height")
                    val yuv = YuvImage(
                        p0, parameters?.previewFormat ?: 0, width ?: 320, height
                            ?: 480, null
                    )
                    val out = ByteArrayOutputStream()
                    yuv.compressToJpeg(Rect(0, 0, width ?: 320, height ?: 480), 100, out)
                    val bytes = out.toByteArray()
                    val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)

                    val img = Mat()
                    Utils.bitmapToMat(bitmap, img)
                    bitmap.recycle()
                    Core.rotate(img, img, Core.ROTATE_90_CLOCKWISE)
                    try {
                        out.close()
                    } catch (e: IOException) {
                        e.printStackTrace()
                    }

                    Observable.create<Corners> {
                        val corner = processPicture(img)
                        isBusy = false
                        if (null != corner && corner.corners.size == 4) {
                            it.onNext(corner)
                        } else {
                            it.onError(Throwable("paper not detected"))
                        }
                    }.observeOn(AndroidSchedulers.mainThread())
                        .subscribe({
                            iView.getPaperRect().onCornersDetected(it)
                        }, {
                            iView.getPaperRect().onCornersNotDetected()
                        })
                }, { throwable -> Log.e(TAG, throwable.message!!) })
        } catch (e: Exception) {
            print(e.message)
        }

    }

    private fun getMaxResolution(): Camera.Size? =
        mCamera?.parameters?.supportedPreviewSizes?.maxBy { it.width }
}
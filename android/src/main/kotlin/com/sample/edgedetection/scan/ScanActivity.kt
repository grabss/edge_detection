package com.sample.edgedetection.scan

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Display
import android.view.MenuItem
import android.view.SurfaceView
import android.widget.SeekBar
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.gson.Gson
import com.sample.edgedetection.*
import com.sample.edgedetection.base.BaseActivity
import com.sample.edgedetection.model.Image
import com.sample.edgedetection.view.PaperRectangle

import kotlinx.android.synthetic.main.activity_scan.*
import org.json.JSONArray
import org.opencv.android.OpenCVLoader
import kotlin.concurrent.thread

class ScanActivity : BaseActivity(), IScanView.Proxy {

    private val REQUEST_CAMERA_PERMISSION = 0
    private val EXIT_TIME = 2000
    private lateinit var mPresenter: ScanPresenter
    private lateinit var sp: SharedPreferences

    private var count = 0
    private val gson = Gson()

    override fun provideContentViewId(): Int = R.layout.activity_scan

    private var needFlash = false

    override fun initPresenter() {
        mPresenter = ScanPresenter(this, this, this)

        sp = getSharedPreferences(SPNAME, Context.MODE_PRIVATE)
        sp.edit().clear().apply()
    }

    override fun prepare() {
        if (!OpenCVLoader.initDebug()) {
            Log.i(TAG, "loading opencv error, exit")
            finish()
        }
        if (ContextCompat.checkSelfPermission(
                this,
                android.Manifest.permission.CAMERA
            ) != PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(
                this,
                android.Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(
                    android.Manifest.permission.CAMERA,
                    android.Manifest.permission.WRITE_EXTERNAL_STORAGE
                ),
                REQUEST_CAMERA_PERMISSION
            )
        } else if (ContextCompat.checkSelfPermission(
                this,
                android.Manifest.permission.CAMERA
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(android.Manifest.permission.CAMERA),
                REQUEST_CAMERA_PERMISSION
            )
        } else if (ContextCompat.checkSelfPermission(
                this,
                android.Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(android.Manifest.permission.WRITE_EXTERNAL_STORAGE),
                REQUEST_CAMERA_PERMISSION
            )
        }

        setBtnListener()
    }

    private fun setBtnListener() {
        flashBtn.setOnClickListener {
            thread {
                mPresenter.toggleFlashMode()
            }
            needFlash = !needFlash
            if (needFlash) {
                flashBtn.setImageResource(R.drawable.ic_baseline_flash_on_24)
            } else {
                flashBtn.setImageResource(R.drawable.ic_baseline_flash_off_24)
            }
        }

        shut.setOnClickListener {
            toDisableBtns()
            mPresenter.shut()
        }

        complete.setOnClickListener{
            toDisableBtns()
            val intent = Intent(application, ImageListActivity::class.java)
            startActivityForResult(intent, REQUEST_CODE)
            val editor = sp.edit()
            editor.putBoolean(CAN_EDIT_IMAGES, true).apply()
        }
    }

    fun setSlider(max: Int?) {
        println("max: $max")
        exposureSlider.progress = max ?: 0

        // Android8.0以上でしかminの値がセットできないため、
        // maxの値を2倍に
        if (max != null) {
            exposureSlider.max = max * 2
        }
        exposureSlider.setOnSeekBarChangeListener(object: SeekBar.OnSeekBarChangeListener {

            // 値変更時に呼ばれる
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val value = (max ?: 0) - progress
                println("value: $value")
                mPresenter.setExposure(value)
            }

            // つまみタッチ時に呼ばれる
            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                println("ドラッグスタート")
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                println("リリース")
            }
        })
    }

    private fun adjustBtnsState() {
        if(count == 0) {
            complete.isEnabled = false
        }
    }

    private fun toDisableBtns() {
        shut.isEnabled = false
        complete.isEnabled = false
    }

    private fun toEnableBtns() {
        shut.isEnabled = true
        complete.isEnabled = true
    }

    fun updateCount() {
        count = getImageCount()

        // UI更新をメインスレッドで行うための記述
        Handler(Looper.getMainLooper()).post  {
            shut.text = count.toString()
            toEnableBtns()
            if (PHOTO_MAX_COUNT <= count) {
//                shut.isEnabled = false
                shut.background = resources.getDrawable(R.drawable.reached_max_count_picture_button, null)
                maxCountDesc.text = resources.getString(R.string.reached_max_count)
            }
        }
    }

    fun saveImage(image: Image) {
        var images = mutableListOf<Image>()
        val json = sp.getString(IMAGE_ARRAY, null)
        if (json != null) {
            images = jsonToImageArray(json)
        }
        images.add(image)
        val editor = sp.edit()
        editor.putString(IMAGE_ARRAY, gson.toJson(images)).apply()
    }

    // 撮影済み画像枚数取得
    private fun getImageCount(): Int {
        val json = sp.getString(IMAGE_ARRAY, null)
        return if (json == null) {
            0
        } else {
            val images = jsonToImageArray(json)
            images.size
        }
    }

    // 初回カメラ起動時、画像一覧画面から戻ってきた場合にのみ呼ばれる
    override fun onStart() {
        println("onStart")
        super.onStart()
        count = getImageCount()
        shut.text = count.toString()
        toEnableBtns()
        adjustBtnsState()
        if (PHOTO_MAX_COUNT <= count) {
//            shut.isEnabled = false
            shut.background = resources.getDrawable(R.drawable.reached_max_count_picture_button, null)
            maxCountDesc.text = resources.getString(R.string.reached_max_count)
        } else {
            shut.background = resources.getDrawable(R.drawable.picture_button, null)
            maxCountDesc.text = resources.getString(R.string.max_count_desc)
        }
        mPresenter.initImageArray()
        mPresenter.start()
        mPresenter.initImageArray()
        mPresenter.start()
    }

    override fun onStop() {
        println("onStop")
        super.onStop()
        mPresenter.stop()
    }

    override fun exit() {
        println("exit")
        finish()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {

        var allGranted = false
        var indexPermission = -1

        if (requestCode == REQUEST_CAMERA_PERMISSION) {
            if (grantResults.count() == 1) {
                if (permissions.indexOf(android.Manifest.permission.CAMERA) >= 0) {
                    indexPermission = permissions.indexOf(android.Manifest.permission.CAMERA)
                }
                if (permissions.indexOf(android.Manifest.permission.WRITE_EXTERNAL_STORAGE) >= 0) {
                    indexPermission =
                        permissions.indexOf(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
                }
                if (indexPermission >= 0 && grantResults[indexPermission] == PackageManager.PERMISSION_GRANTED) {
                    allGranted = true
                }
            }

            if (grantResults.count() == 2 && (
                        grantResults[permissions.indexOf(android.Manifest.permission.CAMERA)] == PackageManager.PERMISSION_GRANTED
                                && grantResults[permissions.indexOf(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)] == PackageManager.PERMISSION_GRANTED)
            ) {
                allGranted = true
            }
        }

        if (allGranted) {
            showMessage(R.string.camera_grant)
            mPresenter.initCamera()
            mPresenter.updateCamera()
        }

        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

    }

    override fun getDisplay(): Display = windowManager.defaultDisplay

    override fun getSurfaceView(): SurfaceView = surface

    override fun getPaperRect(): PaperRectangle = paper_rect

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        println("=====onActivityResult=====")
        val shouldUpload = sp.getBoolean(SHOULD_UPLOAD, false)
        if (shouldUpload) {
            setResult(Activity.RESULT_OK, Intent().putExtra(SCANNED_RESULT, "any"))
            finish()
        }
        super.onActivityResult(requestCode, resultCode, data)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {

        if (item.itemId == android.R.id.home) {
            onBackPressed()
            return true
        }

        return super.onOptionsItemSelected(item)
    }
}
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
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.sample.edgedetection.*
import com.sample.edgedetection.base.BaseActivity
import com.sample.edgedetection.view.PaperRectangle

import kotlinx.android.synthetic.main.activity_scan.*
import org.json.JSONArray
import org.opencv.android.OpenCVLoader

class ScanActivity : BaseActivity(), IScanView.Proxy {

    private val REQUEST_CAMERA_PERMISSION = 0
    private val EXIT_TIME = 2000

    private lateinit var mPresenter: ScanPresenter

    private lateinit var sp: SharedPreferences

    private var count = 0


    override fun provideContentViewId(): Int = R.layout.activity_scan

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

        shut.setOnClickListener {
            toDisableBtns()
            mPresenter.shut()
        }

        complete.setOnClickListener{
            toDisableBtns()
            mPresenter.complete()
            val intent = Intent(application, ImageListActivity::class.java)
            startActivityForResult(intent, REQUEST_CODE)
        }
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
        count++

        // UI更新をメインスレッドで行うための記述
        Handler(Looper.getMainLooper()).post  {
            shut.text = count.toString()
            toEnableBtns()
        }
    }

    private fun saveImage(image: String) {
        val jsons = JSONArray()
        jsons.put(image)
        val editor = sp.edit()
        editor.putString(IMAGE_ARRAY, jsons.toString()).apply()
    }

    // 撮影済み画像枚数取得
    private fun getImageCount(): Int {
        val images: String? = sp.getString(IMAGE_ARRAY, null)
        return if (images == null) {
            0
        } else {
            JSONArray(images).length()
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
        mPresenter.initJsonArray()
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
//        if (resultCode == Activity.RESULT_OK) {
//            if (null != data && null != data.extras) {
//                println("=====onActivityResult2=====")
//                setResult(Activity.RESULT_OK, Intent().putExtra(SCANNED_RESULT, "any"))
//                finish()
//            }
//        }
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
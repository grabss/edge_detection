package com.sample.edgedetection

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.provider.BaseColumns
import android.util.Base64
import com.sample.edgedetection.base.SHOULD_UPLOAD
import com.sample.edgedetection.base.SPNAME
import com.sample.edgedetection.helper.DbHelper
import com.sample.edgedetection.helper.ImageTable
import com.sample.edgedetection.model.Image
import com.sample.edgedetection.scan.ScanActivity
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.PluginRegistry
import org.json.JSONArray

class EdgeDetectionDelegate(activity: Activity) : PluginRegistry.ActivityResultListener {

    private var activity: Activity = activity
    var result: MethodChannel.Result? = null
    private var methodCall: MethodCall? = null
    private val sp = activity.getSharedPreferences(SPNAME, Context.MODE_PRIVATE)
    private val dbHelper = DbHelper(activity)

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?): Boolean {
        println("=====onActivityResult3=====")
        println("requestCode: $requestCode")
        if (requestCode == REQUEST_CODE) {
            println("=====onActivityResult4=====")
            if (resultCode == Activity.RESULT_OK) {
                println("=====onActivityResult5=====")
                if (null != data && null != data.extras) {
                    println("=====onActivityResult6=====")
                    val json: String? = ""
                    finishWithSuccess(json)

                    // 本来はString型で単体画像ファイルのパスを渡していた
//                    val filePath = data.extras!!.getString(SCANNED_RESULT)
                }
            } else if (resultCode == Activity.RESULT_CANCELED) {
                println("RESULT_CANCEL")
                clearMethodCallAndResult()
                val shouldUpload = sp.getBoolean(SHOULD_UPLOAD, false)
                if (shouldUpload) {
                    finishWithSuccess(null)
                }
            }
            return true
        }

        return false
    }

    fun openCameraActivity(call: MethodCall, result: MethodChannel.Result) {
        if (!setPendingMethodCallAndResult(call, result)) {
            finishWithAlreadyActiveError()
            return
        }
        val db = dbHelper.writableDatabase
        db.delete(ImageTable.TABLE_NAME, null, null)
        var intent = Intent(Intent(activity.applicationContext, ScanActivity::class.java))
        activity.startActivityForResult(intent, REQUEST_CODE)
    }

    private fun setPendingMethodCallAndResult(
        methodCall: MethodCall,
        result: MethodChannel.Result
    ): Boolean {
        if (this.result != null) {
            return false
        }

        this.methodCall = methodCall
        this.result = result
        return true
    }

    private fun finishWithAlreadyActiveError() {
        finishWithError("already_active", "Edge detection is already active")
    }

    private fun finishWithError(errorCode: String, errorMessage: String) {
        result?.error(errorCode, errorMessage, null)
        clearMethodCallAndResult()
    }

    private fun finishWithSuccess(json: String?) {
        println("finishWithSuccess")
        val emptyList = ArrayList<String>()

        // Flutterに空配列を渡す
        result?.success(emptyList)
        clearMethodCallAndResult()
    }

    private fun clearMethodCallAndResult() {
        methodCall = null
        result = null
    }


}
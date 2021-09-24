package com.sample.edgedetection.crop

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.ImageView
import com.sample.edgedetection.*
import com.sample.edgedetection.base.BaseActivity
import com.sample.edgedetection.view.PaperRectangle
import kotlinx.android.synthetic.main.activity_crop.*
import kotlin.concurrent.thread


class CropActivity : BaseActivity(), ICropView.Proxy {

    private lateinit var mPresenter: CropPresenter
    private lateinit var sp: SharedPreferences
    var index = 0

    override fun prepare() {
        sp = getSharedPreferences(SPNAME, Context.MODE_PRIVATE)
        setBtnListener()
    }

    override fun onBackPressed() {
        toDisableBtns()
        navToImageListScrn()
    }

    private fun setBtnListener() {
        cancelBtn.setOnClickListener {
            toDisableBtns()
            navToImageListScrn()
        }
        decisionBtn.setOnClickListener {
            toDisableBtns()
            thread {
                mPresenter.crop()
                navToImageListScrn()
            }
        }
    }

    private fun toDisableBtns() {
        cancelBtn.isEnabled = false
        decisionBtn.isEnabled = false
    }

    private fun navToImageListScrn() {
        val intent = Intent(this, ImageListActivity::class.java)
        intent.putExtra(INDEX, index)
        startActivityForResult(intent, 100)
        finish()
    }

    override fun provideContentViewId(): Int = R.layout.activity_crop


    override fun initPresenter() {
        index = intent.getIntExtra(INDEX, 0)
        mPresenter = CropPresenter(this, this, index, 1, 1)
        paper.viewTreeObserver.addOnGlobalLayoutListener {
            val width = paper.width
            val height = paper.height
            mPresenter = CropPresenter(this, this, index, width, height)
        }
    }

    override fun getPaper(): ImageView = paper

    override fun getPaperRect(): PaperRectangle = paper_rect

    override fun getCroppedPaper(): ImageView = picture_cropped
}
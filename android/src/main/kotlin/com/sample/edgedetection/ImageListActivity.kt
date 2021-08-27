package com.sample.edgedetection

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.tabs.TabLayoutMediator
import kotlinx.android.synthetic.main.activity_image_list.*
import org.json.JSONArray

class ImageListActivity : FragmentActivity() {

    private lateinit var viewPager: ViewPager2
    private lateinit var sp: SharedPreferences


    companion object {
        const val EXTRA_DATA = "EXTRA_DATA"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_image_list)

        sp = getSharedPreferences(SPNAME, Context.MODE_PRIVATE)

        val imageCount = getImageCount()

        val pagerAdapter = ImageListPagerAdapter(this, imageCount)

        viewPager = findViewById(R.id.pager)
        viewPager.adapter = pagerAdapter

        setListener()

        TabLayoutMediator(indicator, viewPager) { _, _ -> }.attach()
    }

    private fun getImageCount(): Int {
        val images: String? = sp.getString(SPKEY, null)
        return if (images != null) {
            val a = JSONArray(images)
            a.length()
        } else {
            0
        }
    }

    private fun setListener() {
        trash_btn.setOnClickListener { println("tapped trash_btn") }
        rect_btn.setOnClickListener { println("tapped rect_btn") }
        rotate_btn.setOnClickListener { println("tapped rotate_btn") }
        contrast_btn.setOnClickListener { println("tapped contrast_btn") }
        sort_btn.setOnClickListener { println("tapped sort_btn") }
        upload_btn.setOnClickListener { upload() }
    }

    private fun upload() {
        val intent = Intent().apply {
            // String型で何らかの値を渡す必要がある
            putExtra(SCANNED_RESULT, "dummy")
            putExtra(EXTRA_DATA, "ここに行って欲しい")
        }
        setResult(RESULT_OK, intent)
        System.gc()
        finish()
    }



    private inner class ImageListPagerAdapter(fa: FragmentActivity, imageCount: Int) : FragmentStateAdapter(fa) {
        val sp = getSharedPreferences(SPNAME, Context.MODE_PRIVATE)!!
        val images: String? = sp.getString(SPKEY, null)
        val imageCount = imageCount

        override fun getItemCount(): Int = imageCount

        override fun createFragment(position: Int): Fragment {
            return ImageListFragment.newInstance(JSONArray(images).optString(position))
        }

    }
}
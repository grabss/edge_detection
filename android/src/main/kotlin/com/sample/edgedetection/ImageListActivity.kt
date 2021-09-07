package com.sample.edgedetection

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.Base64
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
import com.sample.edgedetection.model.Image
import kotlinx.android.synthetic.main.activity_image_list.*
import org.json.JSONArray
import java.lang.Exception

const val INDEX = "INDEX"

class ImageListActivity : FragmentActivity(), ConfirmDialogFragment.BtnListener {

    private lateinit var viewPager: ViewPager2
    private lateinit var sp: SharedPreferences
    private lateinit var pagerAdapter: ImageListPagerAdapter
    private lateinit var images: ArrayList<Image>
    private val dialog = ConfirmDialogFragment()
    private var index = 0

    companion object {
        const val EXTRA_DATA = "EXTRA_DATA"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_image_list)

        sp = getSharedPreferences(SPNAME, Context.MODE_PRIVATE)

        val json = sp.getString(IMAGE_ARRAY, null)
        images = jsonToImageArray(json!!)

        pagerAdapter = ImageListPagerAdapter(images)

        // 編集画面からインデックスを取得
       index = intent.getIntExtra(INDEX, 0)

        viewPager = pager
        viewPager.adapter = pagerAdapter
        viewPager.setCurrentItem(index, false)

        setBtnListener()

        TabLayoutMediator(indicator, viewPager) { _, _ -> }.attach()
    }

    private fun setBtnListener() {
        trash_btn.setOnClickListener {
            dialog.show(supportFragmentManager, "TAG")
        }
        rect_btn.setOnClickListener { println("tapped rect_btn") }
        rotate_btn.setOnClickListener {
            navToRotateScrn()
        }
        contrast_btn.setOnClickListener {
            navToContrastScrn()
        }
        sort_btn.setOnClickListener {
            navToSortScrn()
        }
        upload_btn.setOnClickListener {
            upload()
        }
    }

    private fun toDisableBtns() {
        trash_btn.isEnabled = false
        rect_btn.isEnabled = false
        rotate_btn.isEnabled = false
        contrast_btn.isEnabled = false
        sort_btn.isEnabled = false
        upload_btn.isEnabled = false
    }

    // finish()で画像一覧画面をスタックから除外しないとエラー発生。
    // 画像をスタックに積んだままの遷移はNG。
    private fun navToRotateScrn() {
        val intent = Intent(this, RotateActivity::class.java)
        intent.putExtra(INDEX, viewPager.currentItem)
        startActivity(intent)
        finish()
    }

    private fun navToContrastScrn() {
        val intent = Intent(this, ContrastActivity::class.java)
        intent.putExtra(INDEX, viewPager.currentItem)
        startActivity(intent)
        finish()
    }

    private fun navToSortScrn() {
        val intent = Intent(this, SortActivity::class.java)
        intent.putExtra(INDEX, viewPager.currentItem)
        startActivity(intent)
        finish()
    }

    // アップロード実行。Flutterに2次元配列のbyte配列を渡す
    private fun upload() {
        if (images.isEmpty()) {
            return
        }
        upload_btn.isEnabled = false
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
        images.removeAt(index)
        pagerAdapter.updateData(images)
        viewPager.post {
            viewPager.setCurrentItem(index, true)
        }
        if (images.isEmpty()) {
            toDisableBtns()
        }
    }

    // ダイアログのキャンセルボタンタップ時に処理を加える場合はここに記述
    override fun onCancelClick() {
    }

    private inner class ImageListPagerAdapter(images: ArrayList<Image>) : RecyclerView.Adapter<PagerViewHolder>() {
        val sp = getSharedPreferences(SPNAME, Context.MODE_PRIVATE)!!
        var images = images
        private val gson = Gson()

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
            val editor = sp.edit()
            editor.putString(IMAGE_ARRAY, gson.toJson(newImages)).apply()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PagerViewHolder =
            PagerViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.fragment_image_list, parent, false))

        override fun onBindViewHolder(holder: PagerViewHolder, position: Int) {
            holder.bind(images[position])
        }
    }

    class PagerViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val imageView: ImageView = itemView.findViewById(R.id.imageView)

        fun bind(image: Image) {
            val imageBytes = Base64.decode(image?.b64, Base64.DEFAULT)
            val decodedImg = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
            imageView.setImageBitmap(decodedImg)
        }
    }
}
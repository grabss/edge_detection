package com.sample.edgedetection

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.*
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Base64
import android.util.DisplayMetrics
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.FragmentActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.google.gson.Gson
import com.sample.edgedetection.model.Image
import kotlinx.android.synthetic.main.activity_rotate.*
import kotlinx.android.synthetic.main.activity_rotate.cancelBtn
import kotlinx.android.synthetic.main.activity_rotate.decisionBtn
import kotlinx.android.synthetic.main.activity_sort.*
import java.io.ByteArrayOutputStream
import kotlin.concurrent.thread

class SortActivity : FragmentActivity(), ConfirmDialogFragment.BtnListener {
    private lateinit var sp: SharedPreferences
    private var index = 0
    private val bmList = mutableListOf<Bitmap>()
    private var currentAnimator: Animator? = null
    private var shortAnimationDuration: Int = 0
    private lateinit var imageAdapter: ImageAdapter
    private val dm = DisplayMetrics()
    private val dialog = ConfirmDialogFragment()
    private var tmpIndex = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sort)
        index = intent.getIntExtra(INDEX, 0)
        sp = getSharedPreferences(SPNAME, Context.MODE_PRIVATE)
        setGridView()
        setHelper()
        grid.layoutManager = GridLayoutManager(this, 3, RecyclerView.VERTICAL, false)
        setBtnListener()
        windowManager.defaultDisplay.getRealMetrics(dm)
    }

    private fun setGridView() {
        val json = sp.getString(IMAGE_ARRAY, null)
        if (json != null) {
            val images = jsonToImageArray(json)
            for(image in images) {
                val imageBytes = Base64.decode(image.b64, Base64.DEFAULT)
                val decodedImg = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
                bmList.add(decodedImg)
            }
        }
        imageAdapter = ImageAdapter(bmList)
        grid.adapter = imageAdapter
    }

    // ドラッグ操作の有効化
    private fun setHelper() {
        val helper = ItemTouchHelper(
            object : ItemTouchHelper.SimpleCallback(
                ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT or ItemTouchHelper.UP or ItemTouchHelper.DOWN
                , ItemTouchHelper.ACTION_STATE_SWIPE
            ) {
                override fun onMove(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder): Boolean {
                    println("onMove")
                    val fromPos = viewHolder.adapterPosition
                    val toPos = target.adapterPosition
                    imageAdapter.notifyItemMoved(fromPos, toPos)
                    var moto = bmList[fromPos]
                    bmList.removeAt(fromPos)
                    bmList.add(toPos, moto)

                    if (fromPos < toPos) {
                        println("fromPos < toPos")
                    } else {
                        println("toPos < fromPos")
                    }
                    return true
                }

                override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                    TODO("Not yet implemented")
                }

                // ドラッグ操作終了時に呼ばれる
                override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
                    super.clearView(recyclerView, viewHolder)
                    imageAdapter.notifyDataSetChanged()
                }
            }
        )
        helper.attachToRecyclerView(grid)
    }

    // 拡大表示
    private fun zoomImageFromThumb(thumbView: View, position: Int) {
        currentAnimator?.cancel()

        expandedImage.setImageBitmap(bmList[position])

        val startBoundsInt = Rect()
        val finalBoundsInt = Rect()
        val globalOffset = Point()

        thumbView.getGlobalVisibleRect(startBoundsInt)
        container.getGlobalVisibleRect(finalBoundsInt, globalOffset)
        startBoundsInt.offset(-globalOffset.x, -globalOffset.y)
        finalBoundsInt.offset(-globalOffset.x, -globalOffset.y)

        val startBounds = RectF(startBoundsInt)
        val finalBounds = RectF(finalBoundsInt)

        val startScale: Float
        if((finalBounds.width() / finalBounds.height() > startBounds.width() / startBounds.height())) {

            // Extend start bounds horizontally
            println("Extend start bounds horizontally")
            startScale = startBounds.height() / finalBounds.height()
            val startWidth: Float = startScale * finalBounds.height()
            val deltaWidth: Float = (startWidth - startBounds.width()) / 2
            startBounds.left -= deltaWidth.toInt()
            startBounds.right += deltaWidth.toInt()
        } else {

            // Extend start bounds vertically
            println("Extend start bounds vertically")
            startScale = startBounds.width() / finalBounds.width()
            val startHeight: Float = startScale * finalBounds.height()
            val deltaHeight: Float = (startHeight - startBounds.height()) / 2f
            startBounds.top -= deltaHeight.toInt()
            startBounds.bottom += deltaHeight.toInt()
        }

        thumbView.alpha = 0f
        expandedImage.visibility = View.VISIBLE

        expandedImage.pivotX = 0f
        expandedImage.pivotY = 0f

        currentAnimator = AnimatorSet().apply {
            play(
                ObjectAnimator.ofFloat(
                expandedImage,
                View.X,
                startBounds.left,
                finalBounds.left)
            ).apply {
                with(ObjectAnimator.ofFloat(expandedImage, View.Y, startBounds.top, finalBounds.top))
                with(ObjectAnimator.ofFloat(expandedImage, View.SCALE_X, startScale, 1f))
                with(ObjectAnimator.ofFloat(expandedImage, View.SCALE_Y, startScale, 1f))

            }
            duration = shortAnimationDuration.toLong()
            interpolator = DecelerateInterpolator()
            addListener(object: AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator?) {
//                    super.onAnimationEnd(animation)
                    currentAnimator = null
                }

                override fun onAnimationCancel(animation: Animator?) {
//                    super.onAnimationCancel(animation)
                    currentAnimator = null
                }
            })
            start()
        }

        expandedImage.setOnClickListener {
            currentAnimator?.cancel()

            // Animate the four positioning/sizing properties in parallel,
            // back to their original values.
            currentAnimator = AnimatorSet().apply {
                play(ObjectAnimator.ofFloat(expandedImage, View.X, startBounds.left)).apply {
                    with(ObjectAnimator.ofFloat(expandedImage, View.Y, startBounds.top))
                    with(ObjectAnimator.ofFloat(expandedImage, View.SCALE_X, startScale))
                    with(ObjectAnimator.ofFloat(expandedImage, View.SCALE_Y, startScale))
                }
                duration = shortAnimationDuration.toLong()
                interpolator = DecelerateInterpolator()
                addListener(object : AnimatorListenerAdapter() {

                    override fun onAnimationEnd(animation: Animator) {
                        thumbView.alpha = 1f
                        expandedImage.visibility = View.GONE
                        currentAnimator = null
                    }

                    override fun onAnimationCancel(animation: Animator) {
                        thumbView.alpha = 1f
                        expandedImage.visibility = View.GONE
                        currentAnimator = null
                    }
                })
                start()
            }
        }
    }

    private fun setBtnListener() {
        cancelBtn.setOnClickListener {
            disableBtns()
            navToImageListScrn()
        }

        decisionBtn.setOnClickListener {
            disableBtns()
            index = 0
            thread {
                // SharedPrefの値を更新
                updateData()
                navToImageListScrn()
            }
        }
    }

    private fun disableDecisionBtn() {
        decisionBtn.isEnabled = false
    }

    private fun disableBtns() {
        cancelBtn.isEnabled = false
        decisionBtn.isEnabled = false
    }

    private fun updateData() {
        val gson = Gson()
        var newImages = mutableListOf<Image>()
        val editor = sp.edit()
        for(bm in bmList) {
            val baos = ByteArrayOutputStream()
            bm.compress(Bitmap.CompressFormat.JPEG, 100, baos)
            val b = baos.toByteArray()
            val b64 = Base64.encodeToString(b, Base64.DEFAULT)
            val image = Image(b64)
            newImages.add(image)
        }
        if (newImages.isEmpty()) {
            editor.putString(IMAGE_ARRAY, null).apply()
        } else {
            editor.putString(IMAGE_ARRAY, gson.toJson(newImages)).apply()
        }
    }

    private fun navToImageListScrn() {
        val intent = Intent(this, ImageListActivity::class.java)
        intent.putExtra(INDEX, index)
        startActivityForResult(intent, 100)
        finish()
    }

    private fun showAlertDlg(position: Int) {
        tmpIndex = position
        dialog.show(supportFragmentManager, "TAG")
    }

    override fun onDecisionClick() {
        imageAdapter.removeItem(tmpIndex)
    }

    // ダイアログのキャンセルボタンタップ時に処理を加える場合はここに記述
    override fun onCancelClick() {
    }

    private inner class ImageAdapter(bmList: List<Bitmap>): RecyclerView.Adapter<ImageAdapter.ViewHolder>() {
        private var bmList: MutableList<Bitmap> = bmList as MutableList<Bitmap>

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val textView: TextView = view.findViewById(R.id.index)
            val imageView: ImageView = view.findViewById(R.id.gridImg)
            val trashBtn: ImageButton = view.findViewById(R.id.trashBtn)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.grid_item, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            // 画面の横幅取得
            val winW = dm.widthPixels
            // 画像の横幅を(画面幅 * 1/3 - 100dp)にする
            val width = (winW / 3) - 100

            val textView = holder.textView
            textView.width = width
            textView.text = (position + 1).toString()

            val imageView = holder.imageView
            imageView.layoutParams.width = width
            imageView.setImageBitmap(bmList[position])

            val trashBtn = holder.trashBtn
            trashBtn.setOnClickListener {
                showAlertDlg(position)
            }

            imageView.setOnClickListener {
                zoomImageFromThumb(imageView, position)
                shortAnimationDuration = resources.getInteger(android.R.integer.config_shortAnimTime)
                true
            }
        }

        override fun getItemCount() = bmList.size

        fun removeItem(position: Int){
            bmList.removeAt(position)
            notifyDataSetChanged()
            if (bmList.isEmpty()) {
                disableDecisionBtn()
            }
        }
    }
}
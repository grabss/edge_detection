package com.sample.edgedetection

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.ContentValues
import android.content.Intent
import android.graphics.*
import android.os.Bundle
import android.provider.BaseColumns
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
import com.sample.edgedetection.base.ID
import com.sample.edgedetection.helper.DbHelper
import com.sample.edgedetection.helper.ImageTable
import com.sample.edgedetection.model.Image
import kotlinx.android.synthetic.main.activity_rotate.cancelBtn
import kotlinx.android.synthetic.main.activity_rotate.decisionBtn
import kotlinx.android.synthetic.main.activity_sort.*
import kotlin.concurrent.thread

class SortActivity : FragmentActivity(), ConfirmDialogFragment.BtnListener {
    private var id = ""
    private val imageList = mutableListOf<Image>()
    private var currentAnimator: Animator? = null
    private var shortAnimationDuration: Int = 0
    private lateinit var imageAdapter: ImageAdapter
    private val dm = DisplayMetrics()
    private val dialog = ConfirmDialogFragment()
    private var index = 0
    private val dbHelper = DbHelper(this)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sort)
        id = intent.getStringExtra(ID).toString()
        setGridView()
        setHelper()
        grid.layoutManager = GridLayoutManager(this, 3, RecyclerView.VERTICAL, false)
        setBtnListener()
        windowManager.defaultDisplay.getRealMetrics(dm)
    }

    override fun onBackPressed() {
        toDisableBtns()
        navToImageListScrn()
    }

    private fun setGridView() {
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
        with(cursor) {
            while (moveToNext()) {
                val id = getLong(getColumnIndexOrThrow(BaseColumns._ID)).toString()
                val blob = getBlob(getColumnIndexOrThrow(ImageTable.COLUMN_NAME_THUMB_BITMAP))
                val orderIndex = getInt(getColumnIndexOrThrow(ImageTable.COLUMN_NAME_ORDER_INDEX))
                val thumbBm = BitmapFactory.decodeByteArray(blob, 0, blob.size)
                val image = Image(id = id, thumbBm = thumbBm, orderIndex = orderIndex)
                imageList.add(image)
            }
        }
        imageAdapter = ImageAdapter(imageList)
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
                    var moto = imageList[fromPos]
                    imageList.removeAt(fromPos)
                    imageList.add(toPos, moto)

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

        expandedImage.setImageBitmap(imageList[position].thumbBm)

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
            toDisableBtns()
            navToImageListScrn()
        }

        decisionBtn.setOnClickListener {
            toDisableBtns()
            index = 0
            thread {
                updateData()
                navToImageListScrn()
            }
        }
    }

    private fun disableDecisionBtn() {
        decisionBtn.isEnabled = false
    }

    private fun toDisableBtns() {
        cancelBtn.isEnabled = false
        decisionBtn.isEnabled = false
    }

    private fun updateData() {
        val db = dbHelper.writableDatabase
        var i = 1
        for(img in imageList) {
            val values = getContentValues(i)
            val selection = "${BaseColumns._ID} = ?"
            db.update(
                ImageTable.TABLE_NAME,
                values,
                selection,
                arrayOf(img.id)
            )
            i++
        }
    }

    private fun getContentValues(index: Int): ContentValues {
        return ContentValues().apply {
            put("${ImageTable.COLUMN_NAME_ORDER_INDEX}", index)
        }
    }

    private fun navToImageListScrn() {
        val intent = Intent(this, ImageListActivity::class.java)
        intent.putExtra(ID, id)
        startActivityForResult(intent, 100)
        finish()
    }

    private fun showAlertDlg(position: Int) {
        index = position
        dialog.show(supportFragmentManager, "TAG")
    }

    override fun onDecisionClick() {
        imageAdapter.removeItem(index)
    }

    // ダイアログのキャンセルボタンタップ時に処理を加える場合はここに記述
    override fun onCancelClick() {
    }

    private inner class ImageAdapter(imageList: List<Image>): RecyclerView.Adapter<ImageAdapter.ViewHolder>() {
        private var imageList: MutableList<Image> = imageList as MutableList<Image>

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
            imageView.setImageBitmap(imageList[position].thumbBm)

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

        override fun getItemCount() = imageList.size

        fun removeItem(position: Int){
            val db = dbHelper.writableDatabase
            val image = imageList[position]
            db.delete(ImageTable.TABLE_NAME, "${BaseColumns._ID} = ?", arrayOf(image.id))
            imageList.removeAt(position)
            notifyDataSetChanged()
            if (imageList.isEmpty()) {
                disableDecisionBtn()
            }
        }
    }
}
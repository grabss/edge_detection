package com.sample.edgedetection

import android.content.Context
import android.content.SharedPreferences
import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.Base64
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import com.sample.edgedetection.model.Image

private const val ARG_PARAM1 = "params"

class ImageListFragment : Fragment() {
    private var image: Image? = null
    private lateinit var imageView: ImageView
    private lateinit var sp: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sp = this.requireActivity().getSharedPreferences(SPNAME, Context.MODE_PRIVATE)
        arguments?.let {
            image = it.get(ARG_PARAM1) as Image?
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val layout = inflater.inflate(R.layout.fragment_image_list, container, false)
        imageView = layout.findViewById(R.id.imageView)
        setImages()
        return layout
    }

    private fun setImages() {
        val imageBytes = Base64.decode(image?.b64, Base64.DEFAULT)
        val decodedImg = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
        imageView.setImageBitmap(decodedImg)
    }

    companion object {
        @JvmStatic
        fun newInstance(image: Image) =
            ImageListFragment().apply {
                arguments = Bundle().apply {
                    putSerializable(ARG_PARAM1, image)
                }
            }
    }
}
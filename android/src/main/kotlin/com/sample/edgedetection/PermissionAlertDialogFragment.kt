package com.sample.edgedetection

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import kotlinx.android.synthetic.main.fragment_permission_alert_dialog.*

class PermissionAlertDialogFragment(val title: String) : DialogFragment() {
    private lateinit var listener: BtnListener
    val text = title

    interface BtnListener {
        fun onDecisionClick()
        fun onCancelClick()
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        try {
            listener = context as BtnListener
        } catch (e: ClassCastException) {
            throw ClassCastException(("$context must implement BtnListener"))
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val builder = AlertDialog.Builder(requireContext())
        val inflater = requireActivity().layoutInflater
        val view = inflater.inflate(R.layout.fragment_permission_alert_dialog, container, false)
        val title = view.findViewById<TextView>(R.id.title)
        val cancelBtn = view.findViewById<Button>(R.id.cancelBtn)
        val decisionBtn = view.findViewById<Button>(R.id.decisionBtn)

        title.text = text

        builder.setView(view)
        this.isCancelable = false
        val dialog = builder.create()
        dialog.show()

        cancelBtn.setOnClickListener {
            dialog.dismiss()
            listener.onCancelClick()
        }

        decisionBtn.setOnClickListener {
            dialog.dismiss()
            listener.onDecisionClick()
        }

        return dialog
    }
}
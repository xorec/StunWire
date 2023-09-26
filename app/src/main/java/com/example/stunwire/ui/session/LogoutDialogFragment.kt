package com.example.stunwire.ui.session

import android.app.Dialog
import android.os.Bundle
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.viewModels
import com.example.stunwire.R
import com.example.stunwire.viewmodel.SessionViewModel
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class LogoutDialogFragment : DialogFragment() {
    private val sessionViewModel: SessionViewModel by viewModels()

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog =
        MaterialAlertDialogBuilder(requireContext())
            .setMessage(getString(R.string.logout_dialog_fragment_do_you_want_to_logout))
            .setPositiveButton(getString(R.string.logout_dialog_fragment_yes)) { _, _ -> sessionViewModel.disconnectLaunched() }
            .setNegativeButton(getString(R.string.logout_dialog_fragment_no)) { _, _ -> dismiss() }
            .create()

    companion object {
        const val TAG = "LogoutDialog"
    }
}
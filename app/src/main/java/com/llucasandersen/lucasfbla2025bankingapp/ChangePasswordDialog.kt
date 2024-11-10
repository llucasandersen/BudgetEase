package com.llucasandersen.lucasfbla2025bankingapp

import android.app.AlertDialog
import android.app.Dialog
import android.os.Bundle
import androidx.fragment.app.DialogFragment
import com.google.android.material.textfield.TextInputEditText
import io.appwrite.Client
import io.appwrite.exceptions.AppwriteException
import io.appwrite.services.Account
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ChangePasswordDialog : DialogFragment() {

    private lateinit var currentPasswordInput: TextInputEditText
    private lateinit var newPasswordInput: TextInputEditText
    private lateinit var appwriteClient: Client
    private var listener: ChangePasswordListener? = null

    interface ChangePasswordListener {
        fun onPasswordChangeSuccess()
        fun onPasswordChangeFailed(error: String)
    }

    fun setListener(listener: ChangePasswordListener) {
        this.listener = listener
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        appwriteClient = Client(requireContext())
            .setEndpoint("https://append.lucasserver.cloud/v1")
            .setProject("6709882f194aa1acc096")
            .setSelfSigned(true)

        val builder = AlertDialog.Builder(requireActivity())
        val inflater = requireActivity().layoutInflater
        val view = inflater.inflate(R.layout.dialog_change_password, null)

        currentPasswordInput = view.findViewById(R.id.currentPasswordInput)
        newPasswordInput = view.findViewById(R.id.newPasswordInput)

        builder.setView(view)
            .setTitle("Change Password")
            .setPositiveButton("Change") { _, _ -> changePassword() }
            .setNegativeButton("Cancel") { dialog, _ -> dialog.dismiss() }

        return builder.create()
    }

    private fun changePassword() {
        val currentPassword = currentPasswordInput.text.toString()
        val newPassword = newPasswordInput.text.toString()

        if (currentPassword.isEmpty() || newPassword.isEmpty()) {
            listener?.onPasswordChangeFailed("All fields must be filled.")
            return
        }

        val accountService = Account(appwriteClient)
        CoroutineScope(Dispatchers.IO).launch {
            try {
                accountService.updatePassword(password = newPassword, oldPassword = currentPassword)
                withContext(Dispatchers.Main) {
                    listener?.onPasswordChangeSuccess()
                    dismiss()
                }
            } catch (e: AppwriteException) {
                withContext(Dispatchers.Main) {
                    listener?.onPasswordChangeFailed(parseErrorMessage(e.message))
                }
            }
        }
    }

    private fun parseErrorMessage(error: String?): String {
        return when {
            error?.contains("password must be at least 8 characters", true) == true -> "Password must be at least 8 characters."
            error?.contains("Invalid `oldPassword`", true) == true -> "Incorrect current password."
            else -> "An error occurred. Please try again."
        }
    }
}

package com.yaso202508appproxy.intunetestapp

import android.content.Context
import android.widget.ArrayAdapter
import androidx.appcompat.app.AlertDialog

class AuthScopesSelectDialog(private val context: Context) {
    private val items = AuthScopes.getAll()
    private val adapter = ArrayAdapter(
        context,
        android.R.layout.select_dialog_singlechoice,
        items
    )
    private var currentIndex = -1

    fun show(callback: (List<String>) -> Unit) {
        AlertDialog.Builder(context)
            .setTitle("Select Scopes")
            .setSingleChoiceItems(
                adapter,
                if (currentIndex in items.indices) currentIndex else -1
            ) { _, which ->
                currentIndex = which
            }.setPositiveButton("OK") { _, _ ->
                if (currentIndex in items.indices) {
                    callback(items[currentIndex].scopes)
                }
            }.show()
    }
}
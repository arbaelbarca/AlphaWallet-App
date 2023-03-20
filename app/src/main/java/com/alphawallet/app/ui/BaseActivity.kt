package com.alphawallet.app.ui

import androidx.appcompat.app.AppCompatActivity
import com.alphawallet.app.R
import android.widget.TextView
import androidx.annotation.DrawableRes
import android.widget.Toast
import com.alphawallet.app.viewmodel.BaseViewModel
import android.content.Intent
import com.alphawallet.app.ui.BaseActivity
import com.alphawallet.app.widget.SignTransactionDialog
import android.app.Activity
import android.view.MenuItem
import androidx.appcompat.widget.Toolbar
import com.alphawallet.app.entity.AuthenticationCallback
import com.alphawallet.app.entity.AuthenticationFailType
import com.alphawallet.app.entity.Operation

abstract class BaseActivity : AppCompatActivity() {
    // which won't occur between wallet sessions - do not repeat this pattern
    // for other code
    protected fun toolbar(): Toolbar? {
        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        if (toolbar != null) {
            setSupportActionBar(toolbar)
            toolbar.setTitle(R.string.empty)
        }
        enableDisplayHomeAsUp()
        return toolbar
    }

    protected fun setTitle(title: String?) {
        val actionBar = supportActionBar
        val toolbarTitle = findViewById<TextView>(R.id.toolbar_title)
        if (toolbarTitle != null) {
            actionBar?.setTitle(R.string.empty)
            toolbarTitle.text = title
        }
    }

    protected fun setSubtitle(subtitle: String?) {
        val actionBar = supportActionBar
        if (actionBar != null) {
            actionBar.subtitle = subtitle
        }
    }

    protected fun enableDisplayHomeAsUp() {
        val actionBar = supportActionBar
        actionBar?.setDisplayHomeAsUpEnabled(true)
    }

    protected fun enableDisplayHomeAsUp(@DrawableRes resourceId: Int) {
        val actionBar = supportActionBar
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true)
            actionBar.setHomeAsUpIndicator(resourceId)
        }
    }

    protected fun enableDisplayHomeAsHome(active: Boolean) {
        val actionBar = supportActionBar
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(active)
            actionBar.setHomeAsUpIndicator(R.drawable.ic_browser_home)
        }
    }

    protected fun dissableDisplayHomeAsUp() {
        val actionBar = supportActionBar
        actionBar?.setDisplayHomeAsUpEnabled(false)
    }

    protected fun hideToolbar() {
        val actionBar = supportActionBar
        actionBar?.hide()
    }

    protected fun showToolbar() {
        val actionBar = supportActionBar
        actionBar?.show()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            onBackPressed()
            finish()
        }
        return true
    }

    fun displayToast(message: String?) {
        if (message != null) {
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
            BaseViewModel.onPushToast(null)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        //Interpret the return code; if it's within the range of values possible to return from PIN confirmation then separate out
        //the task code from the return value. We have to do it this way because there's no way to send a bundle across the PIN dialog
        //and out through the PIN dialog's return back to here
        if (authCallback == null) {
            return
        }
        if (requestCode >= SignTransactionDialog.REQUEST_CODE_CONFIRM_DEVICE_CREDENTIALS && requestCode <= SignTransactionDialog.REQUEST_CODE_CONFIRM_DEVICE_CREDENTIALS + 10) {
            val taskCode = Operation.values()[requestCode - SignTransactionDialog.REQUEST_CODE_CONFIRM_DEVICE_CREDENTIALS]
            if (resultCode == RESULT_OK) {
                authCallback!!.authenticatePass(taskCode)
            } else {
                authCallback!!.authenticateFail("", AuthenticationFailType.PIN_FAILED, taskCode)
            }
            authCallback = null
        }
    }

    companion object {
        @JvmField
        var authCallback // Note: This static is only for signing callbacks
                : AuthenticationCallback? = null
    }
}

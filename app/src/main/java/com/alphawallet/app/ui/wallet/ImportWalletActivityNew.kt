package com.alphawallet.app.ui.wallet

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.viewbinding.library.activity.viewBinding
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import com.alphawallet.app.C
import com.alphawallet.app.R
import com.alphawallet.app.databinding.ActivityImportWalletNewBinding
import com.alphawallet.app.entity.CustomViewSettings
import com.alphawallet.app.entity.Wallet
import com.alphawallet.app.listener.OnClickSpanListener
import com.alphawallet.app.router.HomeRouter
import com.alphawallet.app.router.ImportWalletRouter
import com.alphawallet.app.util.setSpannableWithLine
import com.alphawallet.app.viewmodel.SplashViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class ImportWalletActivityNew : AppCompatActivity(), Runnable {

    val binding: ActivityImportWalletNewBinding by viewBinding()

    private var viewModel: SplashViewModel? = null
    private var handler: Handler? = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_import_wallet_new)

        initial()
    }

    private fun initial() {
        binding.toolbar.title = "Import From Seed"

        val titleCheckbox = getString(R.string.text_ket_import_wallet)
        setSpannableWithLine(
            this, binding.tvDescFaqImportWallet, titleCheckbox,
            "Term and Conditions",
            R.color.color_blue_dark_text,
            R.color.accent,
            object : OnClickSpanListener {
                override fun clickSpan() {

                }
            }
        )

        initViewModel()
        initOnClick()
    }

    private fun initOnClick() {
        binding.btnImport.setOnClickListener {
            ImportWalletRouter().openForResult(
                this,
                C.IMPORT_REQUEST_CODE,
                true
            )
        }
    }

    private fun initViewModel() {
        viewModel = ViewModelProvider(this)[SplashViewModel::class.java]
        viewModel!!.cleanAuxData(applicationContext)
        viewModel!!.wallets().observe(this) { wallets: Array<Wallet?> -> onWallets(wallets) }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == C.IMPORT_REQUEST_CODE) {
            viewModel!!.fetchWallets()
        }
    }

    private fun onWallets(wallets: Array<Wallet?>) {
        //event chain should look like this:
        //1. check if wallets are empty:
        //      - yes, get either create a new account or take user to wallet page if SHOW_NEW_ACCOUNT_PROMPT is set
        //              then come back to this check.
        //      - no. proceed to check if we are importing a link
        //2. repeat after step 1 is complete. Are we importing a ticket?
        //      - yes - proceed with import
        //      - no - proceed to home activity
        if (wallets.isNotEmpty()) {
            handler!!.postDelayed(this, CustomViewSettings.startupDelay())
        }
    }

    override fun run() {
        HomeRouter().open(this, true)
        finish()
    }

}

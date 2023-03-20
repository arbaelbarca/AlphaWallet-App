package com.alphawallet.app.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.viewbinding.library.activity.viewBinding
import androidx.lifecycle.ViewModelProvider
import com.alphawallet.app.C
import com.alphawallet.app.R
import com.alphawallet.app.analytics.Analytics
import com.alphawallet.app.databinding.ActivitySplashBinding
import com.alphawallet.app.entity.*
import com.alphawallet.app.entity.analytics.FirstWalletAction
import com.alphawallet.app.router.HomeRouter
import com.alphawallet.app.router.ImportWalletRouter
import com.alphawallet.app.service.KeyService.AuthenticationLevel
import com.alphawallet.app.ui.wallet.ImportWalletActivityNew
import com.alphawallet.app.ui.wallet.createwallet.CreateWalletActivity
import com.alphawallet.app.util.RootUtil
import com.alphawallet.app.viewmodel.SplashViewModel
import com.alphawallet.app.widget.AWalletAlertDialog
import com.alphawallet.app.widget.SignTransactionDialog
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
open class SplashActivity : BaseActivity(), CreateWalletCallbackInterface, Runnable {
    private var viewModel: SplashViewModel? = null
    private var handler: Handler? = Handler(Looper.getMainLooper())
    private var errorMessage: String? = null

    val binding: ActivitySplashBinding by viewBinding()

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        //detect previous launch
        initial()

    }

    private fun initial() {
        viewModel = ViewModelProvider(this)[SplashViewModel::class.java]
        viewModel!!.cleanAuxData(applicationContext)
        setContentView(R.layout.activity_splash)
//        viewModel!!.wallets().observe(this) { wallets: Array<Wallet?> -> onWallets(wallets) }
//        viewModel!!.createWallet().observe(this) { wallet: Wallet -> onWalletCreate(wallet) }
//        viewModel!!.fetchWallets()
        checkRoot()
        initOnClick()
    }

    private fun initOnClick() {
        binding.buttonImport.setOnClickListener { v: View? ->
//                ImportWalletRouter().openForResult(
//                    this,
//                    C.IMPORT_REQUEST_CODE,
//                    true
//                )
            startActivity(Intent(this, ImportWalletActivityNew::class.java))
        }

        binding.buttonCreate.setOnClickListener {
            startActivity(Intent(this, CreateWalletActivity::class.java))
        }
    }

    private val thisActivity: Activity
        get() = this

    //wallet created, now check if we need to import
    private fun onWalletCreate(wallet: Wallet) {
        val wallets = arrayOfNulls<Wallet>(1)
        wallets[0] = wallet
        onWallets(wallets)
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
        if (wallets.isEmpty()) {
            viewModel!!.setDefaultBrowser()
            findViewById<View>(R.id.layout_new_wallet).visibility = View.VISIBLE

        } else {
            handler!!.postDelayed(this, CustomViewSettings.startupDelay())
        }

        binding.buttonCreate.setOnClickListener { v: View? ->
            val props = AnalyticsProperties()
            props.put(FirstWalletAction.KEY, FirstWalletAction.CREATE_WALLET.value)
            viewModel!!.track(Analytics.Action.FIRST_WALLET_ACTION, props)
            viewModel!!.createNewWallet(this, this)
        }

        binding.buttonWatch.setOnClickListener { v: View? ->
            ImportWalletRouter().openWatchCreate(
                this,
                C.IMPORT_REQUEST_CODE
            )
        }

        binding.buttonImport.setOnClickListener { v: View? ->
//                ImportWalletRouter().openForResult(
//                    this,
//                    C.IMPORT_REQUEST_CODE,
//                    true
//                )
            startActivity(Intent(this, ImportWalletActivityNew::class.java))
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode >= SignTransactionDialog.REQUEST_CODE_CONFIRM_DEVICE_CREDENTIALS && requestCode <= SignTransactionDialog.REQUEST_CODE_CONFIRM_DEVICE_CREDENTIALS + 10) {
            val taskCode = Operation.values()[requestCode - SignTransactionDialog.REQUEST_CODE_CONFIRM_DEVICE_CREDENTIALS]
            if (resultCode == RESULT_OK) {
                viewModel!!.completeAuthentication(taskCode)
            } else {
                viewModel!!.failedAuthentication(taskCode)
            }
        } else if (requestCode == C.IMPORT_REQUEST_CODE) {
            viewModel!!.fetchWallets()
        }
    }

    override fun HDKeyCreated(address: String, ctx: Context, level: AuthenticationLevel) {
        viewModel!!.StoreHDKey(address, level)
    }

    public override fun onDestroy() {
        super.onDestroy()
        handler = null
    }

    override fun keyFailure(message: String) {
        errorMessage = message
        if (handler != null) handler!!.post(displayError)
    }

    var displayError: Runnable = Runnable {
        val aDialog = AWalletAlertDialog(thisActivity)
        aDialog.setTitle(R.string.key_error)
        aDialog.setIcon(AWalletAlertDialog.ERROR)
        aDialog.setMessage(errorMessage)
        aDialog.setButtonText(R.string.dialog_ok)
        aDialog.setButtonListener { v: View? -> aDialog.dismiss() }
        aDialog.show()
    }

    override fun cancelAuthentication() {}
    override fun fetchMnemonic(mnemonic: String) {}
    override fun run() {
        HomeRouter().open(this, true)
        finish()
    }

    private fun checkRoot() {
        if (RootUtil.isDeviceRooted()) {
            val dialog = AWalletAlertDialog(this)
            dialog.setTitle(R.string.root_title)
            dialog.setMessage(R.string.root_body)
            dialog.setButtonText(R.string.ok)
            dialog.setIcon(AWalletAlertDialog.ERROR)
            dialog.setButtonListener { v: View? -> dialog.dismiss() }
            dialog.show()
        }
    }
}

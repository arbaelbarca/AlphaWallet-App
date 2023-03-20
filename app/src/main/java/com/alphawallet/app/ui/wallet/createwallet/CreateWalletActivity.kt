package com.alphawallet.app.ui.wallet.createwallet

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Shader
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.viewbinding.library.activity.viewBinding
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.viewpager.widget.PagerAdapter
import com.alphawallet.app.C
import com.alphawallet.app.R
import com.alphawallet.app.analytics.Analytics
import com.alphawallet.app.databinding.ActivityCreateWalletBinding
import com.alphawallet.app.entity.*
import com.alphawallet.app.entity.analytics.FirstWalletAction
import com.alphawallet.app.listener.OnClickSpanListener
import com.alphawallet.app.router.HomeRouter
import com.alphawallet.app.service.KeyService
import com.alphawallet.app.util.setSpannable
import com.alphawallet.app.viewmodel.SplashViewModel
import com.alphawallet.app.widget.AWalletAlertDialog
import com.alphawallet.app.widget.SignTransactionDialog
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class CreateWalletActivity : AppCompatActivity(), Runnable, CreateWalletCallbackInterface {

    private var handler: Handler? = Handler(Looper.getMainLooper())
    private var errorMessage: String? = null

    private val layouts = listOf(
        R.layout.layout_create_wallet1,
        R.layout.layout_create_wallet2,
        R.layout.layout_create_wallet3,
    )

    private var splashViewModel: SplashViewModel? = null

    private val adapterViewPager = CarouselViewPagerAdapter()

    val binding: ActivityCreateWalletBinding by viewBinding()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_create_wallet)
        initial()
    }

    private fun initial() {
        initViewPager()
        initViewModel()
    }

    private fun initViewModel() {
        splashViewModel = ViewModelProvider(this)[SplashViewModel::class.java]
        splashViewModel!!.cleanAuxData(applicationContext)
        splashViewModel!!.createWallet().observe(this) { wallet: Wallet -> onWalletCreate(wallet) }
    }

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
        if (wallets.isNotEmpty()) {
            println("respon Yes dapet $wallets")
            handler!!.postDelayed(this, CustomViewSettings.startupDelay())
        }
    }

    override fun run() {
        HomeRouter().open(this, true)
        finish()
    }


    private fun initViewPager() {
        val viewPager = binding.viewPagerCreateWallet
        viewPager.adapter = adapterViewPager
    }

    inner class CarouselViewPagerAdapter : PagerAdapter() {
        private var layoutInflater: LayoutInflater? = null
        override fun instantiateItem(container: ViewGroup, position: Int): Any {
            layoutInflater = getSystemService(LAYOUT_INFLATER_SERVICE) as LayoutInflater
            val view = layoutInflater!!.inflate(layouts[position], container, false)
            container.addView(view)
            when (position) {
                0 -> {
                    initDataViewPager1(view)
                }
                1 -> {
                    initDataViewPager2(view)

                }
                else -> {
                    initDataViewPager3(view)

                }
            }

            return view
        }

        override fun getCount(): Int {
            return layouts.size
        }

        override fun isViewFromObject(view: View, obj: Any): Boolean {
            return view === obj
        }

        override fun destroyItem(container: ViewGroup, position: Int, `object`: Any) {
            val view = `object` as View
            container.removeView(view)
        }
    }

    private fun initDataViewPager2(view: View?) {

    }

    private fun initDataViewPager3(view: View?) {
        val tvSecureWallet3 = view?.findViewById<TextView>(R.id.tvSecureWallet3)
        val tvKet1 = view?.findViewById<TextView>(R.id.tvKet1Wallet3)
        val btnStart = view?.findViewById<TextView>(R.id.btnStartWallet3)
        val titleCheckbox = "Secure your wallet's "
        setSpannable(
            this, tvKet1!!, titleCheckbox,
            "Seed Phrase",
            R.color.color_blue_dark_text,
            R.color.accent,
            object : OnClickSpanListener {
                override fun clickSpan() {

                }
            }
        )

        btnStart?.setOnClickListener {
            val props = AnalyticsProperties()
            props.put(FirstWalletAction.KEY, FirstWalletAction.CREATE_WALLET.value)
            splashViewModel!!.track(Analytics.Action.FIRST_WALLET_ACTION, props)
            splashViewModel!!.createNewWallet(this, this)
        }

        val shaderColor = LinearGradient(
            0f, 0f, 0f, tvSecureWallet3?.lineHeight?.toFloat()!!, Color.parseColor("#70A2FF"),
            Color.parseColor("#54F0D1"), Shader.TileMode.REPEAT
        )

        tvSecureWallet3.paint.shader = shaderColor
    }

    private fun initDataViewPager1(view: View?) {
        val tvTitle = view?.findViewById<TextView>(R.id.tvCreatePassword)
        val tvCheckBox = view?.findViewById<TextView>(R.id.tvCheckBoxWallet1)
        val shaderColor = LinearGradient(
            0f, 0f, 0f, tvTitle?.lineHeight?.toFloat()!!, Color.parseColor("#70A2FF"),
            Color.parseColor("#54F0D1"), Shader.TileMode.REPEAT
        )
        tvTitle.paint?.shader = shaderColor

        val titleCheckbox = "I understand that DeGe cannot recover this password for me. "
        setSpannable(
            this, tvCheckBox!!, titleCheckbox,
            "Lean More",
            R.color.color_blue_dark_text,
            R.color.accent,
            object : OnClickSpanListener {
                override fun clickSpan() {

                }
            }
        )
    }

    override fun onBackPressed() {
        if (binding.viewPagerCreateWallet.currentItem == 0) {
            super.onBackPressed();
        } else {
            binding.viewPagerCreateWallet.currentItem = binding.viewPagerCreateWallet.currentItem - 1;
        }
    }

    override fun keyFailure(message: String) {
        errorMessage = message
        if (handler != null) handler!!.post(displayError)
    }

    var displayError: Runnable = Runnable {
        val aDialog = AWalletAlertDialog(this)
        aDialog.setTitle(R.string.key_error)
        aDialog.setIcon(AWalletAlertDialog.ERROR)
        aDialog.setMessage(errorMessage)
        aDialog.setButtonText(R.string.dialog_ok)
        aDialog.setButtonListener { v: View? -> aDialog.dismiss() }
        aDialog.show()
    }

    override fun onDestroy() {
        super.onDestroy()
        handler = null
    }

    override fun HDKeyCreated(address: String?, ctx: Context?, level: KeyService.AuthenticationLevel?) {
        splashViewModel!!.StoreHDKey(address, level)
    }

    override fun cancelAuthentication() {

    }

    override fun fetchMnemonic(mnemonic: String?) {

    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode >= SignTransactionDialog.REQUEST_CODE_CONFIRM_DEVICE_CREDENTIALS && requestCode <= SignTransactionDialog.REQUEST_CODE_CONFIRM_DEVICE_CREDENTIALS + 10) {
            val taskCode = Operation.values()[requestCode - SignTransactionDialog.REQUEST_CODE_CONFIRM_DEVICE_CREDENTIALS]
            if (resultCode == RESULT_OK) {
                splashViewModel!!.completeAuthentication(taskCode)
            } else {
                splashViewModel!!.failedAuthentication(taskCode)
            }
        } else if (requestCode == C.IMPORT_REQUEST_CODE) {
            splashViewModel!!.fetchWallets()
        }
    }
}

package com.alphawallet.app.ui.splashscreen

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Shader
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.viewbinding.library.activity.viewBinding
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.alphawallet.app.R
import com.alphawallet.app.databinding.ActivitySplashScreenBinding
import com.alphawallet.app.entity.CustomViewSettings
import com.alphawallet.app.entity.Wallet
import com.alphawallet.app.router.HomeRouter
import com.alphawallet.app.ui.onboarding.OnBoardingActivity
import com.alphawallet.app.viewmodel.SplashViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@SuppressLint("CustomSplashScreen")
@AndroidEntryPoint
class SplashScreenActivity : AppCompatActivity(), Runnable {
    private var splashViewModel: SplashViewModel? = null
    private var handler: Handler? = Handler(Looper.getMainLooper())

    val binding: ActivitySplashScreenBinding by viewBinding()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash_screen)

        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)

        initial()
    }

    private fun initial() {
        initViewModel()

        val shaderColor = LinearGradient(
            0f, 0f, 0f, binding.tvSpecta.lineHeight.toFloat(), Color.parseColor("#8AD4EC"),
            Color.parseColor("#FF56A9"), Shader.TileMode.CLAMP
        )
        binding.tvSpecta.paint.shader = shaderColor


    }

    private fun initViewModel() {
        splashViewModel = ViewModelProvider(this)[SplashViewModel::class.java]
        splashViewModel!!.cleanAuxData(applicationContext)
        splashViewModel!!.wallets().observe(this) { wallets: Array<Wallet?> -> onWallets(wallets) }
        splashViewModel!!.fetchWallets()
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
            lifecycleScope.launch {
                delay(3000)
                startActivity(Intent(this@SplashScreenActivity, OnBoardingActivity::class.java))
                finish()
            }
        } else {
            handler!!.postDelayed(this, CustomViewSettings.startupDelay())
        }
    }

    override fun run() {
        HomeRouter().open(this, true)
        finish()
    }
}

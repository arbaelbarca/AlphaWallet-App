package com.alphawallet.app.ui

import android.Manifest
import android.annotation.SuppressLint
import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.view.View
import android.viewbinding.library.activity.viewBinding
import android.widget.Toast
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.OnLifecycleEvent
import androidx.lifecycle.ViewModelProvider
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2.OnPageChangeCallback
import com.alphawallet.app.C
import com.alphawallet.app.R
import com.alphawallet.app.analytics.Analytics
import com.alphawallet.app.api.v1.entity.request.ApiV1Request
import com.alphawallet.app.databinding.ActivityHomeBinding
import com.alphawallet.app.entity.*
import com.alphawallet.app.entity.cryptokeys.SignatureFromKey
import com.alphawallet.app.repository.EthereumNetworkRepository
import com.alphawallet.app.router.ImportTokenRouter
import com.alphawallet.app.service.NotificationService
import com.alphawallet.app.service.PriceAlertsService
import com.alphawallet.app.ui.widget.entity.ActionSheetCallback
import com.alphawallet.app.ui.widget.entity.PagerCallback
import com.alphawallet.app.util.LocaleUtils
import com.alphawallet.app.util.UpdateUtils
import com.alphawallet.app.util.Utils
import com.alphawallet.app.viewmodel.BaseNavigationActivity
import com.alphawallet.app.viewmodel.HomeViewModel
import com.alphawallet.app.viewmodel.SelectThemeViewModel
import com.alphawallet.app.viewmodel.WalletConnectViewModel
import com.alphawallet.app.walletconnect.AWWalletConnectClient
import com.alphawallet.app.walletconnect.WCSession.Companion.from
import com.alphawallet.app.web3.entity.Web3Transaction
import com.alphawallet.app.widget.AWalletAlertDialog
import com.alphawallet.app.widget.AWalletConfirmationDialog
import com.alphawallet.ethereum.EthereumNetworkBase
import com.alphawallet.token.entity.SalesOrderMalformed
import com.alphawallet.token.entity.Signable
import com.alphawallet.token.tools.Numeric
import com.alphawallet.token.tools.ParseMagicLink
import com.github.florent37.tutoshowcase.TutoShowcase
import dagger.hilt.android.AndroidEntryPoint
import net.yslibrary.android.keyboardvisibilityevent.KeyboardVisibilityEvent.setEventListener
import net.yslibrary.android.keyboardvisibilityevent.KeyboardVisibilityEventListener
import timber.log.Timber
import java.net.URLDecoder
import javax.inject.Inject

@AndroidEntryPoint
class HomeActivity : BaseNavigationActivity(), View.OnClickListener, HomeCommsInterface, FragmentMessenger, Runnable,
    SignAuthenticationCallback, ActionSheetCallback, LifecycleObserver, PagerCallback {

    @JvmField
    @Inject
    var awWalletConnectClient: AWWalletConnectClient? = null
    private val pager2Adapter: FragmentStateAdapter
    private val handler = Handler(Looper.getMainLooper())

    val binding: ActivityHomeBinding by viewBinding()

    private val networkSettingsHandler = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result: ActivityResult? -> supportFragmentManager.setFragmentResult(RESET_TOKEN_SERVICE, Bundle()) }

    private var viewModel: HomeViewModel? = null
    private var viewModelWC: WalletConnectViewModel? = null
    private var dialog: Dialog? = null
    private var homeReceiver: HomeReceiver? = null
    private var walletTitle: String? = null
    private var backupWalletDialog: TutoShowcase? = null
    private var isForeground = false

    @Volatile
    private var tokenClicked = false
    private var openLink: String? = null

    private val selectThemeViewModel: SelectThemeViewModel? = null

    @OnLifecycleEvent(Lifecycle.Event.ON_START)
    private fun onMoveToForeground() {
        Timber.tag("LIFE").d("AlphaWallet into foreground")
        if (viewModel != null) {
            viewModel!!.checkTransactionEngine()
            viewModel!!.sendMsgPumpToWC(this)
        }
        isForeground = true
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_STOP)
    private fun onMoveToBackground() {
        Timber.tag("LIFE").d("AlphaWallet into background")
        if (viewModel != null && !tokenClicked) viewModel!!.stopTransactionUpdate()
        if (viewModel != null) viewModel!!.outOfFocus()
        isForeground = false
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (!isForeground) {
            onMoveToBackground()
        }
    }

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            if (viewModel!!.fullScreenSelected()) {
                hideSystemUI()
            } else {
                showSystemUI()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        LocaleUtils.setDeviceLocale(baseContext)
        super.onCreate(savedInstanceState)
        LocaleUtils.setActiveLocale(this)
        lifecycle.addObserver(this)
        isForeground = true
        setWCConnect()
        if (supportActionBar != null) supportActionBar!!.hide()
        viewModel = ViewModelProvider(this)[HomeViewModel::class.java]
        viewModelWC = ViewModelProvider(this)[WalletConnectViewModel::class.java]
        viewModel!!.identify()
        viewModel!!.setWalletStartup()
        viewModel!!.setCurrencyAndLocale(this)
        viewModel!!.tryToShowWhatsNewDialog(this)
        setContentView(R.layout.activity_home)

        initThemeMode()
        initViews()
        toolbar()

        binding.viewPager.isUserInputEnabled = false // i think this replicates lockPages(true)
        binding.viewPager.adapter = pager2Adapter
        binding.viewPager.offscreenPageLimit = WalletPage.values().size
        binding.viewPager.registerOnPageChangeCallback(object : OnPageChangeCallback() {
            override fun onPageScrolled(position: Int, positionOffset: Float, positionOffsetPixels: Int) {
                super.onPageScrolled(position, positionOffset, positionOffsetPixels)
            }

            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
            }

            override fun onPageScrollStateChanged(state: Int) {
                super.onPageScrollStateChanged(state)
            }
        })
        initBottomNavigation()
        dissableDisplayHomeAsUp()
        viewModel!!.error().observe(this) { errorEnvelope: ErrorEnvelope -> onError(errorEnvelope) }
        viewModel!!.walletName().observe(this) { name: String? -> onWalletName(name) }
        viewModel!!.backUpMessage().observe(this) { address: String -> onBackup(address) }
        viewModel!!.splashReset().observe(this) { aBoolean: Boolean -> onRequireInit(aBoolean) }
        viewModel!!.defaultWallet().observe(this) { wallet: Wallet -> onDefaultWallet(wallet) }
        viewModel!!.updateAvailable().observe(this) { availableVersion: String -> onUpdateAvailable(availableVersion) }
        if (CustomViewSettings.hideDappBrowser()) {
            removeDappBrowser()
        }
        setEventListener(
            this, KeyboardVisibilityEventListener { isOpen: Boolean ->
                if (isOpen) {
                    setNavBarVisibility(View.GONE)
                    getFragment(WalletPage.values()[binding.viewPager.currentItem]).softKeyboardVisible()
                } else {
                    setNavBarVisibility(View.VISIBLE)
                    getFragment(WalletPage.values()[binding.viewPager.currentItem]).softKeyboardGone()
                }
            })
        viewModel!!.tryToShowRateAppDialog(this)
        viewModel!!.tryToShowEmailPrompt(this, binding.layoutSuccessOverlay, handler, this)
        if (Utils.verifyInstallerId(this)) {
            UpdateUtils.checkForUpdates(this, this)
        } else {
            if (MediaLinks.isMediaTargeted(applicationContext)) {
                viewModel!!.checkLatestGithubRelease()
            }
        }
        setupFragmentListeners()

        // Get the intent that started this activity
        val intent = intent
        val data = intent.data
        if (intent.hasExtra(C.FROM_HOME_ROUTER) && intent.getStringExtra(C.FROM_HOME_ROUTER) == C.FROM_HOME_ROUTER) {
            viewModel!!.storeCurrentFragmentId(-1)
        }
        if (data != null) {
            val importData = data.toString()
            var importPath: String? = null
            if (importData.startsWith("content://")) {
                importPath = data.path
            }
            checkIntents(importData, importPath, intent)
        }
        val i = Intent(this, PriceAlertsService::class.java)
        startService(i)
    }

    private fun initThemeMode() {
        selectThemeViewModel?.setTheme(applicationContext, C.THEME_DARK)

        when (selectThemeViewModel?.theme) {
            C.THEME_LIGHT -> {
                println("respon Light")
                selectThemeViewModel.setTheme(applicationContext, C.THEME_LIGHT)
            }
            C.THEME_DARK -> {
                println("respon Night")
                selectThemeViewModel.setTheme(applicationContext, C.THEME_DARK)
            }
            else -> {
                selectThemeViewModel?.setTheme(applicationContext, C.THEME_AUTO)
            }
        }
    }

    private fun onUpdateAvailable(availableVersion: String) {
        externalUpdateReady(availableVersion)
    }

    private fun setWCConnect() {
        try {
            awWalletConnectClient!!.init(this)
        } catch (e: Exception) {
            Timber.tag("WalletConnect").e(e)
        }
    }

    private fun onDefaultWallet(wallet: Wallet) {
        if (viewModel!!.checkNewWallet(wallet.address)) {
            viewModel!!.setNewWallet(wallet.address, false)
            val selectNetworkIntent = Intent(this, NetworkToggleActivity::class.java)
            selectNetworkIntent.putExtra(C.EXTRA_SINGLE_ITEM, false)
            networkSettingsHandler.launch(selectNetworkIntent)
        }
    }

    private fun setupFragmentListeners() {
        //TODO: Move all fragment comms to this model - see all instances of ((HomeActivity)getActivity()).
        supportFragmentManager
            .setFragmentResultListener(RESET_TOKEN_SERVICE, this) { requestKey: String?, b: Bundle? ->
                viewModel!!.restartTokensService()
                //trigger wallet adapter reset
                resetTokens()
            }
        supportFragmentManager
            .setFragmentResultListener(C.RESET_WALLET, this) { requestKey: String?, b: Bundle? ->
                viewModel!!.restartTokensService()
                resetTokens()
                showPage(WalletPage.WALLET)
            }
        supportFragmentManager
            .setFragmentResultListener(C.CHANGE_CURRENCY, this) { k: String?, b: Bundle? ->
                resetTokens()
                showPage(WalletPage.WALLET)
            }
        supportFragmentManager
            .setFragmentResultListener(C.RESET_TOOLBAR, this) { requestKey: String?, b: Bundle? -> invalidateOptionsMenu() }
        supportFragmentManager
            .setFragmentResultListener(C.ADDED_TOKEN, this) { requestKey: String?, b: Bundle ->
                val contractList: List<ContractLocator> = b.getParcelableArrayList(C.ADDED_TOKEN)!!
                getFragment(WalletPage.ACTIVITY).addedToken(contractList)
            }
        supportFragmentManager
            .setFragmentResultListener(
                C.SHOW_BACKUP,
                this
            ) { requestKey: String?, b: Bundle -> showBackupWalletDialog(b.getBoolean(C.SHOW_BACKUP, false)) }
        supportFragmentManager
            .setFragmentResultListener(C.HANDLE_BACKUP, this) { requestKey: String?, b: Bundle ->
                if (b.getBoolean(C.HANDLE_BACKUP)) {
                    backupWalletSuccess(b.getString("Key"))
                } else {
                    backupWalletFail(b.getString("Key"), b.getBoolean("nolock"))
                }
            }
        supportFragmentManager
            .setFragmentResultListener(C.TOKEN_CLICK, this) { requestKey: String?, b: Bundle? ->
                tokenClicked = true
                handler.postDelayed({ tokenClicked = false }, 10000)
            }
        supportFragmentManager
            .setFragmentResultListener(C.CHANGED_LOCALE, this) { requestKey: String?, b: Bundle? ->
                viewModel!!.restartHomeActivity(
                    applicationContext
                )
            }
        supportFragmentManager
            .setFragmentResultListener(C.SETTINGS_INSTANTIATED, this) { k: String?, b: Bundle? -> loadingComplete() }
    }

    public override fun onNewIntent(startIntent: Intent) {
        super.onNewIntent(startIntent)
        val data = startIntent.data
        var importPath: String? = null
        var importData: String? = null
        if (data != null) {
            importData = data.toString()
            if (importData.startsWith("content://")) {
                importPath = data.path
            }
            checkIntents(importData, importPath, startIntent)
        }
    }

    //First time to use
    private fun onRequireInit(aBoolean: Boolean) {
        val intent = Intent(this, SplashActivity::class.java)
        startActivity(intent)
        finish()
    }

    private fun onBackup(address: String) {
        if (Utils.isAddressValid(address)) {
            Toast.makeText(this, getString(R.string.postponed_backup_warning), Toast.LENGTH_LONG).show()
        }
    }

    private fun initViews() {

        binding.layoutSuccessOverlay.setOnClickListener(View.OnClickListener { view: View? ->
            //dismiss big green tick
            binding.layoutSuccessOverlay.visibility = View.GONE
        })


    }

    private fun showBackupWalletDialog(walletImported: Boolean) {
        if (!viewModel!!.isFindWalletAddressDialogShown) {
            //check if wallet was imported - in which case no need to display
            if (!walletImported) {
                val background = ContextCompat.getColor(applicationContext, R.color.translucent_dark)
                val statusBarColor = window.statusBarColor
                backupWalletDialog = TutoShowcase.from(this)
                backupWalletDialog!!.setContentView(R.layout.showcase_backup_wallet)
                    .setBackgroundColor(background)
                    .onClickContentView(R.id.btn_close) { view: View? ->
                        window.statusBarColor = statusBarColor
                        backupWalletDialog!!.dismiss()
                    }
                    .onClickContentView(R.id.showcase_layout) { view: View? ->
                        window.statusBarColor = statusBarColor
                        backupWalletDialog!!.dismiss()
                    }
                    .on(R.id.settings_tab)
                    .addCircle()
                    .onClick { v: View? ->
                        window.statusBarColor = statusBarColor
                        backupWalletDialog!!.dismiss()
                        showPage(WalletPage.SETTINGS)
                    }
                backupWalletDialog!!.show()
                window.statusBarColor = background
            }
            viewModel!!.isFindWalletAddressDialogShown = true
        }
    }

    private fun onWalletName(name: String?) {
        walletTitle = if (name != null && !name.isEmpty()) {
            name
        } else {
            getString(R.string.toolbar_header_wallet)
        }
        getFragment(WalletPage.WALLET).setToolbarTitle(walletTitle)
    }

    private fun onError(errorEnvelope: ErrorEnvelope) {}

    @SuppressLint("RestrictedApi")
    override fun onResume() {
        super.onResume()
        setWCConnect()
        viewModel!!.prepare(this)
        viewModel!!.getWalletName(this)
        viewModel!!.setErrorCallback(this)
        if (homeReceiver == null) {
            homeReceiver = HomeReceiver(this, this)
            homeReceiver!!.register()
        }
        initViews()
        handler.post {

            //check clipboard
            val magicLink = ImportTokenActivity.getMagiclinkFromClipboard(this)
            if (magicLink != null) {
                viewModel!!.showImportLink(this, magicLink)
            }
        }
    }

    override fun onPause() {
        super.onPause()
        if (dialog != null && dialog!!.isShowing) {
            dialog!!.dismiss()
        }
    }

    public override fun onSaveInstanceState(savedInstanceState: Bundle) {
        super.onSaveInstanceState(savedInstanceState)
        savedInstanceState.putInt(STORED_PAGE, binding.viewPager.currentItem)
        viewModel!!.storeCurrentFragmentId(selectedItem.ordinal)
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        val oldPage = savedInstanceState.getInt(STORED_PAGE)
        if (oldPage >= 0 && oldPage < WalletPage.values().size) {
            showPage(WalletPage.values()[oldPage])
        }
    }

    override fun onClick(view: View) {}
    override fun onBottomNavigationItemSelected(index: WalletPage): Boolean {
        return when (index) {
            WalletPage.DAPP_BROWSER -> {
                showPage(WalletPage.DAPP_BROWSER)
                true
            }
            WalletPage.WALLET -> {
                showPage(WalletPage.WALLET)
                true
            }
            WalletPage.SETTINGS -> {
                showPage(WalletPage.SETTINGS)
                true
            }
            WalletPage.ACTIVITY -> {
                showPage(WalletPage.ACTIVITY)
                true
            }
        }
        return false
    }

    fun onBrowserWithURL(url: String?) {
        showPage(WalletPage.DAPP_BROWSER)
        getFragment(WalletPage.DAPP_BROWSER).onItemClick(url)
    }

    public override fun onDestroy() {
        viewModel!!.storeCurrentFragmentId(selectedItem.ordinal)
        super.onDestroy()
        viewModel!!.onClean()
        if (homeReceiver != null) {
            homeReceiver!!.unregister()
            homeReceiver = null
        }
    }

    private fun showPage(page: WalletPage) {
        var page: WalletPage? = page
        val oldPage = WalletPage.values()[binding.viewPager.currentItem]
        var enableDisplayAsHome = false
        when (page) {
            WalletPage.DAPP_BROWSER -> {
                hideToolbar()
                setTitle(getString(R.string.toolbar_header_browser))
                selectNavigationItem(WalletPage.DAPP_BROWSER)
                enableDisplayAsHome = true
            }
            WalletPage.WALLET -> {
                showToolbar()
                if (walletTitle == null || walletTitle!!.isEmpty()) {
                    setTitle(getString(R.string.toolbar_header_wallet))
                } else {
                    setTitle(walletTitle)
                }
                selectNavigationItem(WalletPage.WALLET)
            }
            WalletPage.SETTINGS -> {
                showToolbar()
                setTitle(getString(R.string.toolbar_header_settings))
                selectNavigationItem(WalletPage.SETTINGS)
            }
            WalletPage.ACTIVITY -> {
                showToolbar()
                setTitle(getString(R.string.activity_label))
                selectNavigationItem(WalletPage.ACTIVITY)
            }
            else -> {
                page = WalletPage.WALLET
                showToolbar()
                if (walletTitle == null || walletTitle!!.isEmpty()) {
                    setTitle(getString(R.string.toolbar_header_wallet))
                } else {
                    setTitle(walletTitle)
                }
                selectNavigationItem(WalletPage.WALLET)
            }
        }
        enableDisplayHomeAsHome(enableDisplayAsHome)
        switchAdapterToPage(page)
        invalidateOptionsMenu()
        checkWarnings()
        signalPageVisibilityChange(oldPage, page)
    }

    //Switch from main looper
    private fun switchAdapterToPage(page: WalletPage) {
        handler.post { binding.viewPager!!.setCurrentItem(page.ordinal, false) }
    }

    private fun signalPageVisibilityChange(oldPage: WalletPage, newPage: WalletPage) {
        val inFocus = getFragment(newPage)
        inFocus.comeIntoFocus()
        if (oldPage != newPage) {
            val leavingFocus = getFragment(oldPage)
            leavingFocus.leaveFocus()
        }
    }

    private fun checkWarnings() {
        if (updatePrompt) {
            hideDialog()
            updatePrompt = false
            var warns = viewModel!!.updateWarnings + 1
            if (warns < 3) {
                val cDialog = AWalletConfirmationDialog(this)
                cDialog.setTitle(R.string.alphawallet_update)
                cDialog.setCancelable(true)
                cDialog.setSmallText("Using an old version of Alphawallet. Please update from the Play Store or Alphawallet website.")
                cDialog.setPrimaryButtonText(R.string.ok)
                cDialog.setPrimaryButtonListener { v: View? -> cDialog.dismiss() }
                dialog = cDialog
                dialog?.show()
            } else if (warns > 10) {
                warns = 0
            }
            viewModel!!.setUpdateWarningCount(warns)
        }
    }

    override fun playStoreUpdateReady(updateVersion: Int) {
        //signal to WalletFragment an update is ready
        //display entry in the WalletView
        getFragment(WalletPage.SETTINGS).signalPlayStoreUpdate(updateVersion)
    }

    override fun externalUpdateReady(updateVersion: String) {
        getFragment(WalletPage.SETTINGS).signalExternalUpdate(updateVersion)
    }

    override fun tokenScriptError(message: String) {
        handler.removeCallbacksAndMessages(null) //remove any previous error call, only use final error
        // This is in a runnable because the error will come from non main thread process
        handler.postDelayed({
            hideDialog()
            val aDialog = AWalletAlertDialog(this)
            aDialog.setTitle(getString(R.string.tokenscript_file_error))
            aDialog.setMessage(message)
            aDialog.setIcon(AWalletAlertDialog.ERROR)
            aDialog.setButtonText(R.string.button_ok)
            aDialog.setButtonListener { v: View? -> aDialog.dismiss() }
            dialog = aDialog
            dialog?.show()
        }, 500)
    }

    fun backupWalletFail(keyBackup: String?, hasNoLock: Boolean) {
        //postpone backup until later
        getFragment(WalletPage.SETTINGS).backupSeedSuccess(hasNoLock)
        if (keyBackup != null) {
            getFragment(WalletPage.WALLET).remindMeLater(Wallet(keyBackup))
            viewModel!!.checkIsBackedUp(keyBackup)
        }
    }

    fun backupWalletSuccess(keyBackup: String?) {
        getFragment(WalletPage.SETTINGS).backupSeedSuccess(false)
        getFragment(WalletPage.WALLET).storeWalletBackupTime(keyBackup)
        removeSettingsBadgeKey(C.KEY_NEEDS_BACKUP)
        binding.successImage.setImageResource(R.drawable.big_green_tick)
        binding.layoutSuccessOverlay.visibility = View.VISIBLE
        handler.postDelayed(this, 1000)
    }

    override fun run() {
        if (binding.layoutSuccessOverlay.alpha > 0) {
            binding.layoutSuccessOverlay.animate().alpha(0.0f).duration = 500
            handler.postDelayed(this, 750)
        } else {
            binding.layoutSuccessOverlay.visibility = View.GONE
            binding.layoutSuccessOverlay.alpha = 1.0f
        }
    }

    override fun gotAuthorisation(gotAuth: Boolean) {}
    override fun cancelAuthentication() {}
    override fun createdKey(keyAddress: String) {
        //Key was upgraded
        //viewModel.upgradeWallet(keyAddress);
    }

    override fun loadingComplete() {
        val lastId = viewModel!!.lastFragmentId
        if (!TextUtils.isEmpty(openLink)) //delayed open link from intent - safe now that all fragments have been initialised
        {
            showPage(WalletPage.DAPP_BROWSER)
            val dappFrag = getFragment(WalletPage.DAPP_BROWSER) as DappBrowserFragment
            if (!dappFrag.isDetached) dappFrag.loadDirect(openLink)
            openLink = null
            viewModel!!.storeCurrentFragmentId(-1)
        } else if (intent.getBooleanExtra(C.Key.FROM_SETTINGS, false)) {
            showPage(WalletPage.SETTINGS)
        } else if (lastId >= 0 && lastId < WalletPage.values().size) {
            showPage(WalletPage.values()[lastId])
            viewModel!!.storeCurrentFragmentId(-1)
        } else {
            showPage(WalletPage.WALLET)
            getFragment(WalletPage.WALLET).comeIntoFocus()
        }
    }

    private fun getFragment(page: WalletPage): BaseFragment {
        // if fragment hasn't been created yet, return a blank BaseFragment to avoid crash
        return if (page.ordinal + 1 > supportFragmentManager.fragments.size) {
            recreate() //restart activity required
            BaseFragment()
        } else {
            supportFragmentManager.fragments[page.ordinal] as BaseFragment
        }
    }

    override fun requestNotificationPermission() {
        checkNotificationPermission(RC_ASSET_NOTIFICATION_PERM)
    }

    override fun backupSuccess(keyAddress: String) {
        if (Utils.isAddressValid(keyAddress)) backupWalletSuccess(keyAddress)
    }

    override fun resetTokens() {
        getFragment(WalletPage.ACTIVITY).resetTokens()
        getFragment(WalletPage.WALLET).resetTokens()
    }

    override fun resetTransactions() {
        getFragment(WalletPage.ACTIVITY).resetTransactions()
    }

    override fun openWalletConnect(sessionId: String) {
        if (isForeground) {
            val intent = Intent(application, WalletConnectActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
            intent.putExtra("session", sessionId)
            startActivity(intent)
        }
    }

    private fun hideDialog() {
        if (dialog != null && dialog!!.isShowing) {
            dialog!!.dismiss()
        }
    }

    private fun checkNotificationPermission(permissionTag: Int): Boolean {
        return if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_NOTIFICATION_POLICY)
            == PackageManager.PERMISSION_GRANTED
        ) {
            true
        } else {
            val permissions = arrayOf(Manifest.permission.ACCESS_NOTIFICATION_POLICY)
            if (!ActivityCompat.shouldShowRequestPermissionRationale(
                    this,
                    Manifest.permission.ACCESS_NOTIFICATION_POLICY
                )
            ) {
                Timber.tag("HomeActivity").w("Notification permission is not granted. Requesting permission")
                ActivityCompat.requestPermissions(this, permissions, permissionTag)
                false
            } else {
                true
            }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            DappBrowserFragment.REQUEST_CAMERA_ACCESS -> getFragment(WalletPage.DAPP_BROWSER).gotCameraAccess(
                permissions,
                grantResults
            )
            DappBrowserFragment.REQUEST_FILE_ACCESS -> getFragment(WalletPage.DAPP_BROWSER).gotFileAccess(permissions, grantResults)
            DappBrowserFragment.REQUEST_FINE_LOCATION -> getFragment(WalletPage.DAPP_BROWSER).gotGeoAccess(permissions, grantResults)
            RC_ASSET_EXTERNAL_WRITE_PERM -> {}
        }
    }

    public override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data) // intercept return intent from PIN/Swipe authentications
        when (requestCode) {
            DAPP_BARCODE_READER_REQUEST_CODE -> getFragment(WalletPage.DAPP_BROWSER).handleQRCode(resultCode, data, this)
            C.REQUEST_BACKUP_WALLET -> {
                var keyBackup: String? = null
                var noLockScreen = false
                if (data != null) keyBackup = data.getStringExtra("Key")
                if (data != null) noLockScreen = data.getBooleanExtra("nolock", false)
                if (resultCode == RESULT_OK) backupWalletSuccess(keyBackup) else backupWalletFail(keyBackup, noLockScreen)
            }
            C.REQUEST_UNIVERSAL_SCAN -> if (data != null && resultCode == RESULT_OK) {
                if (data.hasExtra(C.EXTRA_QR_CODE)) {
                    val qrCode = data.getStringExtra(C.EXTRA_QR_CODE)
                    viewModel!!.handleQRCode(this, qrCode)
                } else if (data.hasExtra(C.EXTRA_ACTION_NAME)) {
                    val action = data.getStringExtra(C.EXTRA_ACTION_NAME)
                    if (action.equals(C.ACTION_MY_ADDRESS_SCREEN, ignoreCase = true)) {
                        viewModel!!.showMyAddress(this)
                    }
                }
            }
            C.TOKEN_SEND_ACTIVITY -> if (data != null && resultCode == RESULT_OK && data.hasExtra(C.DAPP_URL_LOAD)) {
                getFragment(WalletPage.DAPP_BROWSER).switchNetworkAndLoadUrl(
                    data.getLongExtra(C.EXTRA_CHAIN_ID, EthereumNetworkBase.MAINNET_ID),
                    data.getStringExtra(C.DAPP_URL_LOAD)
                )
                showPage(WalletPage.DAPP_BROWSER)
            } else if (data != null && resultCode == RESULT_OK && data.hasExtra(C.EXTRA_TXHASH)) {
                showPage(WalletPage.ACTIVITY)
            }
            C.TERMINATE_ACTIVITY -> if (data != null && resultCode == RESULT_OK) {
                getFragment(WalletPage.ACTIVITY).scrollToTop()
                showPage(WalletPage.ACTIVITY)
            }
            C.ADDED_TOKEN_RETURN -> if (data != null && data.hasExtra(C.EXTRA_TOKENID_LIST)) {
                val tokenData: List<ContractLocator> = data.getParcelableArrayListExtra(C.EXTRA_TOKENID_LIST)!!
                getFragment(WalletPage.ACTIVITY).addedToken(tokenData)
            } else if (data != null && data.getBooleanExtra(C.RESET_WALLET, false)) {
                viewModel!!.restartTokensService()
                //trigger wallet adapter reset
                resetTokens()
            }
            else -> super.onActivityResult(requestCode, resultCode, data)
        }
    }

    fun postponeWalletBackupWarning(walletAddress: String?) {
        removeSettingsBadgeKey(C.KEY_NEEDS_BACKUP)
    }

    override fun onBackPressed() {
        //Check if current page is WALLET or not
        if (binding.viewPager.currentItem == WalletPage.DAPP_BROWSER.ordinal) {
            getFragment(WalletPage.DAPP_BROWSER).backPressed()
        } else if (binding.viewPager.currentItem != WalletPage.WALLET.ordinal && isNavBarVisible) {
            showPage(WalletPage.WALLET)
        } else {
            super.onBackPressed()
        }
    }

    private fun hideSystemUI() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val inset = WindowInsetsControllerCompat(window, window.decorView)
        inset.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        inset.hide(WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.navigationBars())
    }

    private fun showSystemUI() {
        WindowCompat.setDecorFitsSystemWindows(window, true)
        val inset = WindowInsetsControllerCompat(window, window.decorView)
        inset.show(WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.navigationBars())
    }

    private fun checkIntents(importData: String, importPath: String?, startIntent: Intent) {
        var importData: String? = importData
        try {
            if (importData != null) importData = URLDecoder.decode(importData, "UTF-8")
            val dappFrag = getFragment(WalletPage.DAPP_BROWSER) as DappBrowserFragment
            if (importData != null && importData.startsWith(NotificationService.AWSTARTUP)) {
                importData = importData.substring(NotificationService.AWSTARTUP.length)
                //move window to token if found
                getFragment(WalletPage.WALLET).setImportFilename(importData)
            } else if (startIntent.getStringExtra("url") != null) {
                val url = startIntent.getStringExtra("url")
                showPage(WalletPage.DAPP_BROWSER)
                if (!dappFrag.isDetached) dappFrag.loadDirect(url)
            } else if (importData != null && importData.length > 22 && importData.contains(AW_MAGICLINK)) {
                // Deeplink-based Wallet API
                val request = ApiV1Request(importData)
                if (request.isValid) {
                    val intent = Intent(this, ApiV1Activity::class.java)
                    intent.putExtra(C.Key.API_V1_REQUEST_URL, importData)
                    viewModel!!.track(Analytics.Action.DEEP_LINK_API_V1)
                    startActivity(intent)
                    return
                }
                val directLinkIndex = importData.indexOf(AW_MAGICLINK_DIRECT)
                if (directLinkIndex > 0) {
                    //get link
                    val link = importData.substring(directLinkIndex + AW_MAGICLINK_DIRECT.length)
                    if (supportFragmentManager.fragments.size >= WalletPage.DAPP_BROWSER.ordinal) {
                        viewModel!!.track(Analytics.Action.DEEP_LINK)
                        showPage(WalletPage.DAPP_BROWSER)
                        if (!dappFrag.isDetached) dappFrag.loadDirect(link)
                    } else {
                        openLink = link //open link once fragments are initialised
                    }
                } else {
                    val parser = ParseMagicLink(CryptoFunctions(), EthereumNetworkRepository.extraChains())
                    if (parser.parseUniversalLink(importData).chainId > 0) {
                        ImportTokenRouter().open(this, importData)
                        finish()
                    }
                }
            } else if (importData != null && importData.startsWith("wc:")) {
                val session = from(importData)
                val importPassData = WalletConnectActivity.WC_INTENT + importData
                val intent = Intent(this, WalletConnectActivity::class.java)
                intent.putExtra("qrCode", importPassData)
                intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                startActivity(intent)
            } else if (importPath != null) {
                val useAppExternalDir = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q || !viewModel!!.checkDebugDirectory()
                viewModel!!.importScriptFile(this, useAppExternalDir, startIntent)
            }
        } catch (s: SalesOrderMalformed) {
            //No report, expected
        } catch (e: Exception) {
            Timber.tag("Intent").w(e)
        }
    }

    override fun signingComplete(signature: SignatureFromKey, message: Signable) {
        val signHex = Numeric.toHexString(signature.signature)
        Timber.d("Initial Msg: %s", message.message)
        awWalletConnectClient!!.signComplete(signature, message)
    }

    override fun signingFailed(error: Throwable, message: Signable) {
        awWalletConnectClient!!.signFail(error.message, message)
    }

    override fun getAuthorisation(callback: SignAuthenticationCallback) {
        viewModelWC!!.getAuthenticationForSignature(viewModel!!.defaultWallet().value, this, callback)
    }

    override fun sendTransaction(tx: Web3Transaction) {}
    override fun dismissed(txHash: String, callbackId: Long, actionCompleted: Boolean) {
        if (!actionCompleted) {
            awWalletConnectClient!!.dismissed(callbackId)
        }
    }

    override fun notifyConfirm(mode: String) {}

    //TODO: Implement when passing transactions through here
    var getGasSettings = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result: ActivityResult? -> }

    override fun gasSelectLauncher(): ActivityResultLauncher<Intent> {
        return getGasSettings
    }

    private class ScreenSlidePagerAdapter(fragmentActivity: FragmentActivity) : FragmentStateAdapter(fragmentActivity) {
        override fun createFragment(position: Int): Fragment {
            return when (WalletPage.values()[position]) {
                WalletPage.WALLET -> WalletFragment()
                WalletPage.ACTIVITY -> ActivityFragment()
                WalletPage.DAPP_BROWSER -> if (CustomViewSettings.hideDappBrowser()) {
                    BaseFragment()
                } else {
                    DappBrowserFragment()
                }
                WalletPage.SETTINGS -> NewSettingsFragment()
                else -> WalletFragment()
            }
        }

        override fun getItemCount(): Int {
            return WalletPage.values().size
        }
    }

    companion object {
        const val RC_ASSET_EXTERNAL_WRITE_PERM = 223
        const val RC_ASSET_NOTIFICATION_PERM = 224
        const val DAPP_BARCODE_READER_REQUEST_CODE = 1
        const val STORED_PAGE = "currentPage"
        const val RESET_TOKEN_SERVICE = "HOME_reset_ts"
        const val AW_MAGICLINK = "aw.app/"
        const val AW_MAGICLINK_DIRECT = "openurl?url="
        private var updatePrompt = false

        @JvmStatic
        fun setUpdatePrompt() {
            //TODO: periodically check this value (eg during page flipping)
            //Set alert to user to update their app
            updatePrompt = true
        }
    }

    init {
        // fragment creation is shifted to adapter
        pager2Adapter = ScreenSlidePagerAdapter(this)
    }


}

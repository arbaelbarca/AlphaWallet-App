package com.alphawallet.app.ui

import android.app.Activity
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Shader
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.util.Pair
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.RelativeLayout
import android.widget.Toast
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.*
import androidx.recyclerview.widget.GridLayoutManager.SpanSizeLookup
import com.alphawallet.app.C
import com.alphawallet.app.R
import com.alphawallet.app.analytics.Analytics
import com.alphawallet.app.data.model.request.AccountWalletModel
import com.alphawallet.app.databinding.FragmentWalletBinding
import com.alphawallet.app.entity.*
import com.alphawallet.app.entity.tokens.Token
import com.alphawallet.app.entity.tokens.TokenCardMeta
import com.alphawallet.app.entity.walletconnect.WalletConnectSessionItem
import com.alphawallet.app.interact.GenericWalletInteract.BackupLevel
import com.alphawallet.app.repository.TokensRealmSource
import com.alphawallet.app.repository.entity.RealmToken
import com.alphawallet.app.service.TickerService
import com.alphawallet.app.service.TokensService
import com.alphawallet.app.ui.account.fragment.dialogfragment.AccountWalletBottomFragment
import com.alphawallet.app.ui.widget.TokensAdapterCallback
import com.alphawallet.app.ui.widget.adapter.ListAccountWalletAdapter
import com.alphawallet.app.ui.widget.adapter.TokensAdapter
import com.alphawallet.app.ui.widget.entity.AvatarWriteCallback
import com.alphawallet.app.ui.widget.entity.WarningData
import com.alphawallet.app.ui.widget.holder.TokenGridHolder
import com.alphawallet.app.ui.widget.holder.TokenHolder
import com.alphawallet.app.ui.widget.holder.WarningHolder
import com.alphawallet.app.util.LocaleUtils
import com.alphawallet.app.util.MyBlurBuilder
import com.alphawallet.app.viewmodel.WalletViewModel
import com.alphawallet.app.walletconnect.AWWalletConnectClient
import com.alphawallet.app.widget.*
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayout.OnTabSelectedListener
import dagger.hilt.android.AndroidEntryPoint
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import io.realm.Realm
import io.realm.RealmChangeListener
import io.realm.RealmResults
import kotlinx.android.synthetic.main.layout_account_wallet.*
import timber.log.Timber
import java.math.BigInteger
import javax.inject.Inject

/**
 * Created by justindeguzman on 2/28/18.
 */
@AndroidEntryPoint
class WalletFragment : BaseFragment(), TokensAdapterCallback, View.OnClickListener, Runnable, AvatarWriteCallback,
    ServiceSyncCallback {
    private val handler = Handler(Looper.getMainLooper())
    private var viewModel: WalletViewModel? = null
    private var adapter: TokensAdapter? = null
    private var addressAvatar: UserAvatar? = null
    private var selectedToken: View? = null
    private var importFileName: String? = null
    private var isVisibles = false
    private var currentTabPos = TokenFilter.ALL
    private var realm: Realm? = null
    private var realmUpdates: RealmResults<RealmToken>? = null
    private var largeTitleView: LargeTitleView? = null
    private var realmUpdateTime: Long = 0
    private var handleBackupClick: ActivityResultLauncher<Intent>? = null
    private var tokenManagementLauncher: ActivityResultLauncher<Intent>? = null

    lateinit var fragmentBinding: FragmentWalletBinding
    var bottomSheetBehavior: BottomSheetBehavior<RelativeLayout>? = null
    lateinit var listAccountWalletAdapter: ListAccountWalletAdapter
    var listAccountModel: MutableList<AccountWalletModel> = mutableListOf()

    @JvmField
    @Inject
    var awWalletConnectClient: AWWalletConnectClient? = null
    private var networkSettingsHandler = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result: ActivityResult? ->
        //send instruction to restart tokenService
        parentFragmentManager.setFragmentResult(HomeActivity.RESET_TOKEN_SERVICE, Bundle())
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        fragmentBinding = FragmentWalletBinding.inflate(layoutInflater, container, false)

        return fragmentBinding.root
    }


    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initial()
    }

    private fun initial() {

        LocaleUtils.setActiveLocale(context) // Can't be placed before above line
        if (CustomViewSettings.canAddTokens()) {
            toolbar(requireView(), R.menu.menu_wallet) { menuItem: MenuItem -> onMenuItemClick(menuItem) }
        } else {
            toolbar(requireView())
        }

        initResultLaunchers()
        initViews(requireView())
        initViewModel()
        initList()
        initTabLayout(requireView())
        initNotificationView(requireView())
        setImportToken()
//        initBottomSheetBehavior()
//        initAdapterAccountWallet()
//        initListAccountWallet()

        viewModel!!.prepare()
        addressAvatar!!.setWaiting()
        childFragmentManager
            .setFragmentResultListener(SEARCH_FRAGMENT, this) { requestKey: String?, bundle: Bundle? ->
                val fragment = childFragmentManager.findFragmentByTag(SEARCH_FRAGMENT)
                if (fragment != null && fragment.isVisible && !fragment.isDetached) {
                    fragment.onDetach()
                    childFragmentManager.beginTransaction()
                        .remove(fragment)
                        .commitAllowingStateLoss()
                }
            }
    }

    private fun initListAccountWallet() {
        listAccountModel.addAll(
            listOf(
                AccountWalletModel(
                    1,
                    "Account 1",
                    R.drawable.baseline_account_circle_24,
                    "9.09 Eth"
                ),
                AccountWalletModel(
                    1,
                    "Account 1",
                    R.drawable.baseline_account_circle_24,
                    "9.09 Eth"
                ),
                AccountWalletModel(
                    1,
                    "Account 1",
                    R.drawable.baseline_account_circle_24,
                    "9.09 Eth"
                ),
                AccountWalletModel(
                    1,
                    "Account 1",
                    R.drawable.baseline_account_circle_24,
                    "9.09 Eth"
                ),
            )
        )

        listAccountWalletAdapter.addListAccount(listAccountModel)
    }

    private fun initAdapterAccountWallet() {
        listAccountWalletAdapter = ListAccountWalletAdapter()
        rvLsitAccountWallet.apply {
            adapter = listAccountWalletAdapter
            layoutManager = LinearLayoutManager(requireContext())
            hasFixedSize()
        }

    }

    private fun initBottomSheetBehavior() {
        val relativeLayout = requireActivity().findViewById<RelativeLayout>(R.id.rlBottomViewAccountWallet)

        bottomSheetBehavior = BottomSheetBehavior.from(relativeLayout)
        behaviorBottomSheet()

        //call for state bottomsheet
//        bottomSheetBehavior?.state = BottomSheetBehavior.STATE_EXPANDED

    }

    fun behaviorBottomSheet() {
        bottomSheetBehavior?.addBottomSheetCallback(object : BottomSheetBehavior.BottomSheetCallback() {
            override fun onStateChanged(bottomSheet: View, newState: Int) {
                when (newState) {
                    BottomSheetBehavior.STATE_HIDDEN -> {
//                        bottomSheetBehavior!!.state = BottomSheetBehavior.STATE_COLLAPSED

                    }
                    BottomSheetBehavior.STATE_EXPANDED -> {
//                        showToast("expande",requireContext()) // ini full
                        val layoutParams =
                            (rlBottomViewAccountWallet?.layoutParams as? ViewGroup.MarginLayoutParams)
                        layoutParams?.setMargins(0, 70, 0, 0)
                        rlBottomViewAccountWallet?.layoutParams = layoutParams

                    }
                    BottomSheetBehavior.STATE_COLLAPSED -> {
//                        showToast("collapes",requireContext()) // ini collapes di bawah
                        val layoutParams =
                            (rlBottomViewAccountWallet?.layoutParams as? ViewGroup.MarginLayoutParams)
                        layoutParams?.setMargins(0, 0, 0, 0)
                        rlBottomViewAccountWallet?.layoutParams = layoutParams

                    }
                    BottomSheetBehavior.STATE_DRAGGING, BottomSheetBehavior.STATE_SETTLING -> {
//                        showToast("drag",requireContext()) // ini ketika di drag
                    }
                    BottomSheetBehavior.STATE_HALF_EXPANDED -> {

                    }
                }
            }

            override fun onSlide(bottomSheet: View, slideOffset: Float) {
                bottomSheet.animate()
            }
        })
    }

    private fun initResultLaunchers() {
        tokenManagementLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result: ActivityResult ->
            if (result.data == null) return@registerForActivityResult
            val tokenData: ArrayList<ContractLocator> = result.data!!.getParcelableArrayListExtra(C.ADDED_TOKEN)!!
            val b = Bundle()
            b.putParcelableArrayList(C.ADDED_TOKEN, tokenData)
            parentFragmentManager.setFragmentResult(C.ADDED_TOKEN, b)
        }
        networkSettingsHandler = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result: ActivityResult? ->
            //send instruction to restart tokenService
            parentFragmentManager.setFragmentResult(HomeActivity.RESET_TOKEN_SERVICE, Bundle())
        }
        handleBackupClick = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result: ActivityResult ->
            var keyBackup: String? = null
            var noLockScreen = false
            val data = result.data
            if (data != null) keyBackup = data.getStringExtra("Key")
            if (data != null) noLockScreen = data.getBooleanExtra("nolock", false)
            if (result.resultCode == Activity.RESULT_OK) {
                (activity as HomeActivity?)!!.backupWalletSuccess(keyBackup)
            } else {
                (activity as HomeActivity?)!!.backupWalletFail(keyBackup, noLockScreen)
            }
        }
    }

    private fun initList() {
        adapter = TokensAdapter(
            this, viewModel!!.assetDefinitionService, viewModel!!.tokensService,
            tokenManagementLauncher
        )
        adapter!!.setHasStableIds(true)
        setLinearLayoutManager(TokenFilter.ALL.ordinal)
        fragmentBinding.list.adapter = adapter
        if (fragmentBinding.list.itemAnimator != null) (fragmentBinding.list.itemAnimator as SimpleItemAnimator?)!!.supportsChangeAnimations =
            false
        val itemTouchHelper = ItemTouchHelper(SwipeCallback(adapter!!))
        itemTouchHelper.attachToRecyclerView(fragmentBinding.list)
        fragmentBinding.refreshLayout.setOnRefreshListener { refreshList() }
        fragmentBinding.list.addRecyclerListener { holder: RecyclerView.ViewHolder? -> adapter!!.onRViewRecycled(holder) }
        fragmentBinding.list.layoutManager = LinearLayoutManager(activity)
    }

    private fun initViewModel() {
        viewModel = ViewModelProvider(this)
            .get(WalletViewModel::class.java)
        viewModel!!.progress().observe(viewLifecycleOwner) { shouldShow: Boolean? ->
            fragmentBinding.systemView.showProgress(
                shouldShow!!
            )
        }
        viewModel!!.tokens().observe(viewLifecycleOwner) { tokens: Array<TokenCardMeta>? -> onTokens(tokens) }
        viewModel!!.backupEvent().observe(viewLifecycleOwner) { backupLevel: BackupLevel -> backupEvent(backupLevel) }
        viewModel!!.defaultWallet().observe(viewLifecycleOwner) { wallet: Wallet -> onDefaultWallet(wallet) }
        viewModel!!.onFiatValues().observe(viewLifecycleOwner) { fiatValues: Pair<Double, Double> -> updateValue(fiatValues) }
        viewModel!!.tokensService.startWalletSync(this)
        viewModel!!.activeWalletConnectSessions()
            .observe(viewLifecycleOwner) { walletConnectSessionItems: List<WalletConnectSessionItem?>? ->
                adapter!!.showActiveWalletConnectSessions(walletConnectSessionItems)
            }
    }

    private fun initViews(view: View) {
        addressAvatar = view.findViewById(R.id.user_address_blockie)
        addressAvatar?.visibility = View.VISIBLE
        fragmentBinding.systemView.showProgress(true)
        fragmentBinding.systemView.attachRecyclerView(fragmentBinding.list)
        fragmentBinding.systemView.attachSwipeRefreshLayout(fragmentBinding.refreshLayout)
        largeTitleView = view.findViewById(R.id.large_title_view)
        (view.findViewById<View>(R.id.progress_view) as ProgressView).hide()
    }

    private fun onDefaultWallet(wallet: Wallet) {
        if (CustomViewSettings.showManageTokens()) {
            adapter!!.setWalletAddress(wallet.address)
        }

        addressAvatar!!.bind(wallet, this)
        addressAvatar!!.visibility = View.VISIBLE
        addressAvatar!!.setOnClickListener { v: View? ->
            // open wallets activity
//            viewModel!!.showManageWallets(context, false)
//            bottomSheetBehavior?.state = BottomSheetBehavior.STATE_EXPANDED
            val accountWalletBottomFragment = AccountWalletBottomFragment()
            accountWalletBottomFragment.show(childFragmentManager, "")


//            fragmentBinding.imgTransparantWallet.visibility = View.VISIBLE
        }

        //Do we display new user backup popup?
        val result = Bundle()
        result.putBoolean(C.SHOW_BACKUP, wallet.lastBackupTime > 0)
        parentFragmentManager.setFragmentResult(C.SHOW_BACKUP, result) //reset tokens service and wallet page with updated filters
        addressAvatar!!.setWaiting()
    }

    private fun setRealmListener(updateTime: Long) {
        if (realm == null || realm!!.isClosed) realm = viewModel!!.realmInstance
        if (realmUpdates != null) {
            realmUpdates!!.removeAllChangeListeners()
            realm!!.removeAllChangeListeners()
        }
        realmUpdates = realm!!.where(RealmToken::class.java).equalTo("isEnabled", true)
            .like("address", TokensRealmSource.ADDRESS_FORMAT)
            .greaterThan("addedTime", updateTime + 1)
            .findAllAsync()
        realmUpdates?.addChangeListener { realmTokens: RealmResults<RealmToken> ->
            var lastUpdateTime = updateTime
            val metas: MutableList<TokenCardMeta> = ArrayList()
            //make list
            for (t in realmTokens) {
                if (t.updateTime > lastUpdateTime) lastUpdateTime = t.updateTime
                if (!viewModel!!.tokensService.networkFilters.contains(t.chainId)) continue
                if (viewModel!!.isChainToken(t.chainId, t.tokenAddress)) continue
                val balance = TokensRealmSource.convertStringBalance(t.balance, t.contractType)
                val meta = TokenCardMeta(
                    t.chainId, t.tokenAddress, balance,
                    t.updateTime, viewModel!!.assetDefinitionService, t.name, t.symbol, t.contractType,
                    viewModel!!.getTokenGroup(t.chainId, t.tokenAddress)
                )
                meta.lastTxUpdate = t.lastTxTime
                meta.isEnabled = t.isEnabled
                metas.add(meta)
            }
            if (metas.size > 0) {
                realmUpdateTime = lastUpdateTime
                updateMetas(metas)
                handler.postDelayed({ setRealmListener(realmUpdateTime) }, 500)
            }
        }
    }

    private fun updateMetas(metas: List<TokenCardMeta>) {
        handler.post {
            if (metas.isNotEmpty()) {
                adapter!!.updateTokenMetas(metas.toTypedArray())
                fragmentBinding.systemView.hide()
                viewModel!!.checkDeleteMetas(metas)
            }
        }
    }

    //Refresh value of wallet once sync is complete
    override fun syncComplete(svs: TokensService, syncCount: Int) {
        if (syncCount > 0) handler.post { addressAvatar!!.finishWaiting() }

        svs.fiatValuePair
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe { fiatValues: Pair<Double, Double> -> updateValue(fiatValues) }
            .isDisposed

        if (syncCount > 0) {
            //now refresh the tokens to pick up any new ticker updates
            viewModel!!.tokensService.tickerUpdateList
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe { updatedContracts: List<String?>? -> adapter!!.notifyTickerUpdate(updatedContracts) }
                .isDisposed
        }
    }

    //Could the view have been destroyed?
    private fun updateValue(fiatValues: Pair<Double, Double>) {
        try {
            // to avoid NaN
            println("respon ValuesFiat $fiatValues")

            val changePercent = if (fiatValues.first.toInt() != 0) (fiatValues.first - fiatValues.second) / fiatValues.second * 100.0 else 0.0
            largeTitleView!!.subtitle.text = getString(
                R.string.wallet_total_change, TickerService.getCurrencyString(fiatValues.first - fiatValues.second),
                TickerService.getPercentageConversion(changePercent)
            )
            largeTitleView!!.title.text = TickerService.getCurrencyString(fiatValues.first)
            val color = ContextCompat.getColor(requireContext(), if (changePercent < 0) R.color.white else R.color.positive)
            val shaderColor = LinearGradient(
                0f, 0f, 0f, largeTitleView!!.title.lineHeight.toFloat(), Color.parseColor("#70A2FF"),
                Color.parseColor("#54F0D1"), Shader.TileMode.REPEAT
            )

            largeTitleView!!.title.paint.shader = shaderColor
            largeTitleView!!.subtitle.setTextColor(color)

            if (viewModel!!.wallet != null && viewModel!!.wallet.type != WalletType.WATCH && isVisibles) {
                viewModel!!.checkBackup(fiatValues.first)
            }
        } catch (e: Exception) {
            // empty: expected if view has terminated before we can shut down the service return
            e.printStackTrace()
        }
    }

    private fun refreshList() {
        handler.post {
            adapter!!.clear()
            viewModel!!.prepare()
            viewModel!!.notifyRefresh()
            awWalletConnectClient!!.updateNotification()
        }
    }

    override fun comeIntoFocus() {
        isVisibles = true
        if (viewModel!!.wallet != null && !TextUtils.isEmpty(viewModel!!.wallet.address)) {
            setRealmListener(realmUpdateTime)
        }
    }

    override fun leaveFocus() {
        if (realmUpdates != null) {
            realmUpdates!!.removeAllChangeListeners()
            realmUpdates = null
        }
        if (realm != null && !realm!!.isClosed) realm!!.close()
        softKeyboardGone()
    }

    override fun onPause() {
        super.onPause()
    }

    private fun initTabLayout(view: View) {
        if (CustomViewSettings.hideTabBar()) {
            fragmentBinding.tabLayout.visibility = View.GONE
            return
        }
//        fragmentBinding.tabLayout.addTab(fragmentBinding.tabLayout.newTab().setText(R.string.all))
        fragmentBinding.tabLayout.addTab(fragmentBinding.tabLayout.newTab().setText(R.string.token_text))
        fragmentBinding.tabLayout.addTab(fragmentBinding.tabLayout.newTab().setText(R.string.collectibles))
//        fragmentBinding.tabLayout.addTab(fragmentBinding.tabLayout.newTab().setText(R.string.defi_header))
//        fragmentBinding.tabLayout.addTab(fragmentBinding.tabLayout.newTab().setText(R.string.governance_header))
//        fragmentBinding.tabLayout.addTab(fragmentBinding.tabLayout.newTab().setText(R.string.attestations));
        fragmentBinding.tabLayout.addOnTabSelectedListener(object : OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                val newFilter = setLinearLayoutManager(tab.position)
                adapter!!.setFilterType(newFilter)
                when (newFilter) {
                    TokenFilter.ALL, TokenFilter.ASSETS, TokenFilter.DEFI, TokenFilter.GOVERNANCE -> {
                        fragmentBinding.list.layoutManager = LinearLayoutManager(activity)
                        viewModel!!.prepare()
                    }
                    TokenFilter.COLLECTIBLES -> {
                        setGridLayoutManager(TokenFilter.COLLECTIBLES)
                        viewModel!!.prepare()
                    }
                    TokenFilter.ATTESTATIONS -> {}
                    else -> {

                    }
                }
            }

            override fun onTabUnselected(tab: TabLayout.Tab) {}
            override fun onTabReselected(tab: TabLayout.Tab) {}
        })
    }

    private fun setGridLayoutManager(tab: TokenFilter) {
        val gridLayoutManager = GridLayoutManager(context, 2)
        gridLayoutManager.spanSizeLookup = object : SpanSizeLookup() {
            override fun getSpanSize(position: Int): Int {
                return if (adapter!!.getItemViewType(position) == TokenGridHolder.VIEW_TYPE) {
                    1
                } else 2
            }
        }
        fragmentBinding.list.layoutManager = gridLayoutManager
        currentTabPos = tab
    }

    private fun setLinearLayoutManager(selectedTab: Int): TokenFilter {
        currentTabPos = TokenFilter.values()[selectedTab]
        return currentTabPos
    }

    override fun onTokenClick(view: View?, token: Token?, tokenIds: List<BigInteger?>?, selected: Boolean) {
        if (selectedToken == null) {
            parentFragmentManager.setFragmentResult(C.TOKEN_CLICK, Bundle())
            selectedToken = view
            var clickOrigin = viewModel!!.getTokenFromService(token!!)
            if (clickOrigin == null) clickOrigin = token
            viewModel!!.showTokenDetail(activity, clickOrigin)
            handler.postDelayed(this, 700)
        }
    }

    override fun onLongTokenClick(view: View?, token: Token?, tokenIds: List<BigInteger?>?) {
        TODO("Not yet implemented")
    }

    override fun reloadTokens() {
        viewModel!!.reloadTokens()
    }

    override fun onBuyToken() {
        val buyEthDialog = BottomSheetDialog(requireActivity())
        val buyEthOptionsView = BuyEthOptionsView(activity)
        buyEthOptionsView.setOnBuyWithRampListener { v: View? ->
            val intent = viewModel!!.getBuyIntent(currentWallet.address)
            (activity as HomeActivity?)!!.onActivityResult(C.TOKEN_SEND_ACTIVITY, Activity.RESULT_OK, intent)
            viewModel!!.track(Analytics.Action.BUY_WITH_RAMP)
            buyEthDialog.dismiss()
        }
        buyEthOptionsView.setOnBuyWithCoinbasePayListener { v: View? -> viewModel!!.showBuyEthOptions(activity) }
        buyEthDialog.setContentView(buyEthOptionsView)
        buyEthDialog.show()
    }

    override fun onResume() {
        super.onResume()
        currentTabPos = TokenFilter.ALL
        selectedToken = null
        if (viewModel == null) {
            requireActivity().recreate()
        } else {
            viewModel!!.track(Analytics.Navigation.WALLET)
            if (largeTitleView != null) {
                largeTitleView!!.visibility = View.VISIBLE //show or hide Fiat summary
            }
        }
    }

    private fun onTokens(tokens: Array<TokenCardMeta>?) {
        if (tokens != null) {
            adapter!!.setTokens(tokens)
            checkScrollPosition()
            viewModel!!.calculateFiatValues()
        }
        fragmentBinding.systemView.showProgress(false)
        realmUpdateTime = 0
        for (tcm in tokens!!) {
            if (tcm.lastUpdate > realmUpdateTime) realmUpdateTime = tcm.lastUpdate
        }
        if (isVisibles) {
            setRealmListener(realmUpdateTime)
        }
        if (currentTabPos == TokenFilter.ALL) {
            awWalletConnectClient!!.updateNotification()
        } else {
            adapter!!.showActiveWalletConnectSessions(emptyList())
        }
    }

    /**
     * Checks to see if the current session was started from clicking on a TokenScript notification
     * If it was, identify the contract and pass information to adapter which will identify the corresponding contract token card
     */
    private fun setImportToken() {
        if (importFileName != null) {
            val importToken = viewModel!!.assetDefinitionService.getHoldingContract(importFileName)
            if (importToken != null) Toast.makeText(context, importToken.address, Toast.LENGTH_LONG).show()
            if (importToken != null && adapter != null) adapter!!.setScrollToken(importToken)
            importFileName = null
        }
    }

    /**
     * If the adapter has identified the clicked-on script update from the above call and that card is present, scroll to the card.
     */
    private fun checkScrollPosition() {
        val scrollPos = adapter!!.scrollPosition
        if (scrollPos > 0) {
            (fragmentBinding.list.layoutManager as LinearLayoutManager?)!!.scrollToPositionWithOffset(scrollPos, 0)
        }
    }

    private fun backupEvent(backupLevel: BackupLevel) {
        if (adapter!!.hasBackupWarning()) return
        val wData: WarningData
        when (backupLevel) {
            BackupLevel.BACKUP_NOT_REQUIRED -> {}
            BackupLevel.WALLET_HAS_LOW_VALUE -> {
                wData = WarningData(this)
                wData.title = getString(R.string.time_to_backup_wallet)
                wData.detail = getString(R.string.recommend_monthly_backup)
                wData.buttonText = getString(R.string.back_up_now)
                wData.colour = R.color.text_secondary
                wData.wallet = viewModel!!.wallet
                adapter!!.addWarning(wData)
            }
            BackupLevel.WALLET_HAS_HIGH_VALUE -> {
                wData = WarningData(this)
                wData.title = getString(R.string.wallet_not_backed_up)
                wData.detail = getString(R.string.not_backed_up_detail)
                wData.buttonText = getString(R.string.back_up_now)
                wData.colour = R.color.error
                wData.wallet = viewModel!!.wallet
                adapter!!.addWarning(wData)
            }
        }
    }

    private fun onError(errorEnvelope: ErrorEnvelope) {
        if (errorEnvelope.code == C.ErrorCode.EMPTY_COLLECTION) {
            fragmentBinding.systemView.showEmpty(getString(R.string.no_tokens))
        } else {
            fragmentBinding.systemView.showError(getString(R.string.error_fail_load_tokens), this)
        }
    }

    override fun onClick(view: View) {
        if (view.id == R.id.try_again) {
            viewModel!!.prepare()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
        if (realmUpdates != null) {
            try {
                realmUpdates!!.removeAllChangeListeners()
            } catch (e: Exception) {
                Timber.e(e)
            }
        }
        if (realm != null && !realm!!.isClosed) realm!!.close()
        if (adapter != null) adapter!!.onDestroy(fragmentBinding.list)
    }

    override fun resetTokens() {
        if (viewModel != null && adapter != null) {
            //reload tokens
            viewModel!!.reloadTokens()
            handler.post {

                //first abort the current operation
                adapter!!.clear()
                //show syncing
                addressAvatar!!.setWaiting()
            }
        }
    }

    override fun run() {
//        if (selectedToken != null && selectedToken.findViewById(R.id.token_layout) != null)
//        {
//            selectedToken.findViewById(R.id.token_layout).setBackgroundResource(R.drawable.background_marketplace_event);
//        }
        selectedToken = null
    }

    override fun backUpClick(wallet: Wallet) {
        val intent = Intent(context, BackupKeyActivity::class.java)
        intent.putExtra(C.Key.WALLET, wallet)
        when (viewModel!!.walletType) {
            WalletType.HDKEY -> intent.putExtra("TYPE", BackupOperationType.BACKUP_HD_KEY)
            WalletType.KEYSTORE -> intent.putExtra("TYPE", BackupOperationType.BACKUP_KEYSTORE_KEY)
            else -> {

            }
        }
        intent.flags = Intent.FLAG_ACTIVITY_MULTIPLE_TASK
        handleBackupClick!!.launch(intent)
    }

    override fun remindMeLater(wallet: Wallet) {
        handler.post {
            if (viewModel != null) viewModel!!.setKeyWarningDismissTime(wallet.address)
            if (adapter != null) adapter!!.removeItem(WarningHolder.VIEW_TYPE)
        }
    }

    override fun storeWalletBackupTime(backedUpKey: String) {
        handler.post {
            if (viewModel != null) viewModel!!.setKeyBackupTime(backedUpKey)
            if (adapter != null) adapter!!.removeItem(WarningHolder.VIEW_TYPE)
        }
    }

    override fun setImportFilename(fName: String) {
        importFileName = fName
    }

    override fun avatarFound(wallet: Wallet) {
        //write to database
        viewModel!!.saveAvatar(wallet)
    }

    val currentWallet: Wallet
        get() = viewModel!!.wallet

    override fun onMenuItemClick(menuItem: MenuItem): Boolean {
        if (menuItem.itemId == R.id.action_my_wallet) {
            viewModel!!.showMyAddress(requireActivity())
        }
        if (menuItem.itemId == R.id.action_scan) {
            viewModel!!.showQRCodeScanning(activity)
        }
        return super.onMenuItemClick(menuItem)
    }

    private fun initNotificationView(view: View) {
        val notificationView: NotificationView = view.findViewById(R.id.notification)
        val hasShownWarning = viewModel!!.isMarshMallowWarningShown
        if (!hasShownWarning && Build.VERSION.SDK_INT <= Build.VERSION_CODES.M) {
            notificationView.setTitle(requireContext().getString(R.string.title_version_support_warning))
            notificationView.setMessage(requireContext().getString(R.string.message_version_support_warning))
            notificationView.setPrimaryButtonText(requireContext().getString(R.string.hide_notification))
            notificationView.setPrimaryButtonListener {
                notificationView.visibility = View.GONE
                viewModel!!.setMarshMallowWarning(true)
            }
        } else {
            notificationView.visibility = View.GONE
        }
    }

    override fun onSearchClicked() {
        val intent = Intent(activity, SearchActivity::class.java)
        startActivity(intent)
    }

    override fun onSwitchClicked() {
        val intent = Intent(activity, NetworkToggleActivity::class.java)
        intent.putExtra(C.EXTRA_SINGLE_ITEM, false)
        networkSettingsHandler.launch(intent)
    }

    inner class SwipeCallback internal constructor(private val mAdapter: TokensAdapter) :
        ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT) {
        private var icon: Drawable? = null
        private var background: ColorDrawable? = null
        override fun onMove(
            recyclerView: RecyclerView,
            viewHolder: RecyclerView.ViewHolder,
            viewHolder1: RecyclerView.ViewHolder
        ): Boolean {
            return false
        }

        override fun onSwiped(viewHolder: RecyclerView.ViewHolder, position: Int) {
            if (viewHolder is WarningHolder) {
                remindMeLater(viewModel!!.wallet)
            } else if (viewHolder is TokenHolder) {
                val token = viewHolder.token
                viewModel!!.setTokenEnabled(token, false)
                val removedToken = mAdapter.removeToken(token.tokenInfo.chainId, token.address)
                if (context != null) {
                    val snackbar = Snackbar
                        .make(
                            viewHolder.itemView,
                            token.tokenInfo.name + " " + context!!.getString(R.string.token_hidden),
                            Snackbar.LENGTH_LONG
                        )
                        .setAction(getString(R.string.action_snackbar_undo)) { view: View? ->
                            viewModel!!.setTokenEnabled(token, true)
                            mAdapter.addToken(removedToken)
                        }
                    snackbar.show()
                }
            }
        }

        override fun getSwipeDirs(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder): Int {
            if (viewHolder.itemViewType == TokenHolder.VIEW_TYPE) {
                val t = (viewHolder as TokenHolder).token
                if (t != null && t.isEthereum) return 0
            } else {
                return 0
            }
            return super.getSwipeDirs(recyclerView, viewHolder)
        }

        override fun onChildDraw(
            c: Canvas,
            recyclerView: RecyclerView,
            viewHolder: RecyclerView.ViewHolder,
            dX: Float,
            dY: Float,
            actionState: Int,
            isCurrentlyActive: Boolean
        ) {
            super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive)
            val itemView = viewHolder.itemView
            val offset = 20
            val iconMargin = (itemView.height - icon!!.intrinsicHeight) / 2
            val iconTop = itemView.top + (itemView.height - icon!!.intrinsicHeight) / 2
            val iconBottom = iconTop + icon!!.intrinsicHeight
            if (dX > 0) {
                val iconLeft = itemView.left + iconMargin + icon!!.intrinsicWidth
                val iconRight = itemView.left + iconMargin
                icon!!.setBounds(iconRight, iconTop, iconLeft, iconBottom)
                background!!.setBounds(
                    itemView.left, itemView.top,
                    itemView.left + dX.toInt() + offset,
                    itemView.bottom
                )
            } else if (dX < 0) {
                val iconLeft = itemView.right - iconMargin - icon!!.intrinsicWidth
                val iconRight = itemView.right - iconMargin
                icon!!.setBounds(iconLeft, iconTop, iconRight, iconBottom)
                background!!.setBounds(
                    itemView.right + dX.toInt() - offset,
                    itemView.top, itemView.right, itemView.bottom
                )
            } else {
                background!!.setBounds(0, 0, 0, 0)
            }
            background!!.draw(c)
            icon!!.draw(c)
        }

        init {
            if (activity != null) {
                icon = ContextCompat.getDrawable(activity!!, R.drawable.ic_hide_token)
                if (icon != null) {
                    icon?.setTint(ContextCompat.getColor(activity!!, R.color.error_inverse))
                }
                background = ColorDrawable(ContextCompat.getColor(activity!!, R.color.error))
            }
        }
    }

    companion object {
        const val SEARCH_FRAGMENT = "w_search"
        private const val TAG = "WFRAG"
    }
}

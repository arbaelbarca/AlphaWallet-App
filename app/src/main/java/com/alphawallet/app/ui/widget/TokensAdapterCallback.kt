package com.alphawallet.app.ui.widget

import android.view.View
import com.alphawallet.app.entity.tokens.Token
import java.math.BigInteger

interface TokensAdapterCallback {
    fun onTokenClick(view: View?, token: Token?, tokenIds: List<BigInteger?>?, selected: Boolean)
    fun onLongTokenClick(view: View?, token: Token?, tokenIds: List<BigInteger?>?)
    fun reloadTokens() {}
    fun onBuyToken() {}
    fun onSearchClicked() {}
    fun onSwitchClicked() {}
}

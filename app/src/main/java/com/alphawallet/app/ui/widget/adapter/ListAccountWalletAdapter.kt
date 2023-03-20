package com.alphawallet.app.ui.widget.adapter

import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.alphawallet.app.data.model.request.AccountWalletModel
import com.alphawallet.app.databinding.LayoutItemAccountWalletBinding
import com.alphawallet.app.util.ViewBindingVH

class ListAccountWalletAdapter : RecyclerView.Adapter<ViewBindingVH>() {

    var listAccountModel: MutableList<AccountWalletModel> = mutableListOf()

    fun addListAccount(listAccountModels: MutableList<AccountWalletModel>) {
        listAccountModel.clear()
        listAccountModel.addAll(listAccountModels)
        notifyDataSetChanged()

    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewBindingVH {
        return ViewBindingVH.create(parent, LayoutItemAccountWalletBinding::inflate)
    }

    override fun getItemCount(): Int {
        return listAccountModel.size
    }

    override fun onBindViewHolder(holder: ViewBindingVH, position: Int) {
        val dataItem = listAccountModel[position]
        (holder.binding as LayoutItemAccountWalletBinding).apply {
            holder.itemView.apply {
                tvItemTitleAccountWallet.text = dataItem.title
                tvItemCoinAccountWallet.text = dataItem.coinWallet
                imgItemWallet.setImageResource(dataItem.image)
            }
        }
    }
}

package com.alphawallet.app.ui.account.fragment.dialogfragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.alphawallet.app.R
import com.alphawallet.app.data.model.request.AccountWalletModel
import com.alphawallet.app.databinding.LayoutAccountWalletBinding
import com.alphawallet.app.ui.widget.adapter.ListAccountWalletAdapter
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import kotlinx.android.synthetic.main.layout_account_wallet.*

// TODO: Rename parameter arguments, choose names that match
// the fragment initialization parameters, e.g. ARG_ITEM_NUMBER
private const val ARG_PARAM1 = "param1"
private const val ARG_PARAM2 = "param2"

/**
 * A simple [Fragment] subclass.
 * Use the [AccountWalletBottomFragment.newInstance] factory method to
 * create an instance of this fragment.
 */
class AccountWalletBottomFragment : BottomSheetDialogFragment() {
    // TODO: Rename and change types of parameters
    private var param1: String? = null
    private var param2: String? = null

    lateinit var fragmentBinding: LayoutAccountWalletBinding
    lateinit var listAccountWalletAdapter: ListAccountWalletAdapter
    var listAccountModel: MutableList<AccountWalletModel> = mutableListOf()


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            param1 = it.getString(ARG_PARAM1)
            param2 = it.getString(ARG_PARAM2)
        }

        setStyle(STYLE_NO_FRAME, R.style.CustomBottomSheetDialogTheme)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // Inflate the layout for this fragment
        fragmentBinding = LayoutAccountWalletBinding.inflate(inflater, container, false)
        return fragmentBinding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initial()
    }

    private fun initial() {
        initAdapterAccountWallet()
        initListAccountWallet()
        initOnClick()
    }

    private fun initOnClick() {
        fragmentBinding.btnCreateNewAccountWallet.setOnClickListener {
            AccountWalletCreateBottomFragment()
                .show(childFragmentManager, "create")
        }

        fragmentBinding.btnImportAccountWallet.setOnClickListener {
            AccountWalletImportBottomFragment()
                .show(childFragmentManager, "import")
        }
    }

    companion object {
        @JvmStatic
        fun newInstance(param1: String, param2: String) {

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
}

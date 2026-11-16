package top.niunaijun.blackboxa.view.gms

import android.accounts.AccountManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import cbfg.rvadapter.RVAdapter
import com.afollestad.materialdialogs.MaterialDialog
import com.google.android.material.switchmaterial.SwitchMaterial
import top.niunaijun.blackbox.BlackBoxCore
import top.niunaijun.blackboxa.R
import top.niunaijun.blackboxa.bean.GmsBean
import top.niunaijun.blackboxa.databinding.ActivityGmsBinding
import top.niunaijun.blackboxa.util.InjectionUtil
import top.niunaijun.blackboxa.util.inflate
import top.niunaijun.blackboxa.util.toast
import top.niunaijun.blackboxa.view.base.LoadingActivity

class GmsManagerActivity : LoadingActivity() {

    private lateinit var viewModel: GmsViewModel
    private lateinit var mAdapter: RVAdapter<GmsBean>
    private val viewBinding: ActivityGmsBinding by inflate()

    // Which user slot is waiting for an account to be added
    private var pendingAccountUserId: Int = -1

    // System account picker result
    private val addAccountResult = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK && pendingAccountUserId >= 0) {
            val email = result.data
                ?.getStringExtra(AccountManager.KEY_ACCOUNT_NAME)
                ?: return@registerForActivityResult

            val addResult = MutableLiveData<Boolean>()
            addResult.observe(this) { success ->
                if (success) {
                    toast(getString(R.string.google_account_added, email))
                    viewModel.getInstalledUser() // refresh list
                } else {
                    toast(R.string.google_account_add_failed)
                }
                pendingAccountUserId = -1
            }
            viewModel.addGoogleAccount(email, pendingAccountUserId, addResult)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(viewBinding.root)
        initToolbar(viewBinding.toolbarLayout.toolbar, R.string.gms_manager, true)

        if (!BlackBoxCore.get().isSupportGms) {
            toast(R.string.no_gms)
            finish()
            return
        }

        initViewModel()
        initRecyclerView()
    }

    private fun initViewModel() {
        viewModel = ViewModelProvider(this, InjectionUtil.getGmsFactory())[GmsViewModel::class.java]
        showLoading()

        viewModel.mInstalledLiveData.observe(this) {
            hideLoading()
            if (it.isEmpty()) {
                viewBinding.stateView.showEmpty()
            } else {
                viewBinding.stateView.showContent()
                mAdapter.setItems(it)
            }
        }

        viewModel.mUpdateInstalledLiveData.observe(this) { result ->
            if (result == null) return@observe
            hideLoading()

            val items = mAdapter.getItems()
            for (index in items.indices) {
                val bean = items[index]
                if (bean.userID == result.userID) {
                    if (result.success) bean.isInstalledGms = !bean.isInstalledGms
                    mAdapter.replaceAt(index, bean)
                    break
                }
            }

            if (result.success) {
                toast(result.msg)
            } else {
                MaterialDialog(this).show {
                    title(R.string.gms_manager)
                    message(text = result.msg ?: getString(R.string.uninstall_fail))
                    positiveButton(R.string.done)
                }
            }
        }

        viewModel.getInstalledUser()
    }

    private fun initRecyclerView() {
        mAdapter = RVAdapter<GmsBean>(this, GmsAdapter { bean -> onAddAccountClick(bean) })
            .bind(viewBinding.recyclerView)
            .setItemClickListener { view, item, _ ->
                val checkbox = view.findViewById<SwitchMaterial>(R.id.checkbox)
                if (item.isInstalledGms) uninstallGms(item.userID, checkbox)
                else installGms(item.userID, checkbox)
            }
        viewBinding.recyclerView.layoutManager = LinearLayoutManager(this)
    }

    private fun onAddAccountClick(bean: GmsBean) {
        if (bean.googleAccount != null) {
            // Already has an account — offer to remove or change
            MaterialDialog(this).show {
                title(R.string.gms_manager)
                message(text = bean.googleAccount)
                positiveButton(R.string.change_google_account) { launchAccountPicker(bean.userID) }
                negativeButton(R.string.remove_google_account) {
                    viewModel.removeGoogleAccount(bean.googleAccount!!, bean.userID)
                    toast(R.string.google_account_removed)
                    viewModel.getInstalledUser()
                }
            }
        } else {
            launchAccountPicker(bean.userID)
        }
    }

    private fun launchAccountPicker(userId: Int) {
        pendingAccountUserId = userId
        // Opens the real system Google account picker — user can pick existing or add new
        val intent = AccountManager.newChooseAccountIntent(
            null, null, arrayOf("com.google"),
            null, null, null, null
        )
        addAccountResult.launch(intent)
    }

    private fun installGms(userID: Int, checkbox: SwitchMaterial) {
        MaterialDialog(this).show {
            title(R.string.enable_gms)
            message(R.string.enable_gms_hint)
            positiveButton(R.string.done) {
                showLoading()
                viewModel.installGms(userID)
            }
            negativeButton(R.string.cancel) { checkbox.isChecked = false }
        }
    }

    private fun uninstallGms(userID: Int, checkbox: SwitchMaterial) {
        MaterialDialog(this).show {
            title(R.string.disable_gms)
            message(R.string.disable_gms_hint)
            positiveButton(R.string.done) {
                showLoading()
                viewModel.uninstallGms(userID)
            }
            negativeButton(R.string.cancel) { checkbox.isChecked = true }
        }
    }

    companion object {
        fun start(context: Context) {
            context.startActivity(Intent(context, GmsManagerActivity::class.java))
        }
    }
}

package top.niunaijun.blackboxa.view.gms

import android.accounts.Account
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import cbfg.rvadapter.RVAdapter
import com.afollestad.materialdialogs.MaterialDialog
import com.google.android.material.switchmaterial.SwitchMaterial
import top.niunaijun.blackbox.BlackBoxCore
import top.niunaijun.blackbox.fake.frameworks.BAccountManager
import top.niunaijun.blackboxa.R
import top.niunaijun.blackboxa.bean.GmsBean
import top.niunaijun.blackboxa.databinding.ActivityGmsBinding
import top.niunaijun.blackboxa.util.InjectionUtil
import top.niunaijun.blackboxa.util.inflate
import top.niunaijun.blackboxa.util.toast
import top.niunaijun.blackboxa.view.auth.GoogleAuthActivity
import top.niunaijun.blackboxa.view.base.LoadingActivity

class GmsManagerActivity : LoadingActivity() {

    private lateinit var viewModel: GmsViewModel
    private lateinit var mAdapter: RVAdapter<GmsBean>
    private val viewBinding: ActivityGmsBinding by inflate()
    private var pendingAccountUserId: Int = -1

    companion object {
        private const val REQ_GOOGLE_AUTH = 7001
        fun start(context: Context) {
            context.startActivity(Intent(context, GmsManagerActivity::class.java))
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

    // Receive result from GoogleAuthActivity
    @Suppress("DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_GOOGLE_AUTH && resultCode == Activity.RESULT_OK && data != null) {
            val email = data.getStringExtra(GoogleAuthActivity.RESULT_EMAIL) ?: return
            val token = data.getStringExtra(GoogleAuthActivity.RESULT_TOKEN) ?: ""
            val userId = pendingAccountUserId
            pendingAccountUserId = -1

            if (email.isNotBlank()) {
                // Store account + token in BAccountManager for this user slot
                val account = Account(email, "com.google")
                BAccountManager.get().addAccountExplicitlyForUser(account, null, null, userId)
                if (token.isNotBlank()) {
                    BAccountManager.get().setAuthToken(
                        account,
                        "oauth2:https://www.googleapis.com/auth/userinfo.email https://www.googleapis.com/auth/userinfo.profile openid",
                        token
                    )
                }
                toast(getString(R.string.google_account_added, email))
                viewModel.getInstalledUser()
            }
        }
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

            if (result.success) toast(result.msg)
            else MaterialDialog(this).show {
                title(R.string.gms_manager)
                message(text = result.msg ?: getString(R.string.uninstall_fail))
                positiveButton(R.string.done)
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
            MaterialDialog(this).show {
                title(R.string.gms_manager)
                message(text = bean.googleAccount)
                positiveButton(R.string.change_google_account) { launchGoogleSignIn(bean.userID) }
                negativeButton(R.string.remove_google_account) {
                    viewModel.removeGoogleAccount(bean.googleAccount!!, bean.userID)
                    toast(R.string.google_account_removed)
                    viewModel.getInstalledUser()
                }
            }
        } else {
            launchGoogleSignIn(bean.userID)
        }
    }

    @Suppress("DEPRECATION")
    private fun launchGoogleSignIn(userId: Int) {
        pendingAccountUserId = userId
        GoogleAuthActivity.start(this, userId, REQ_GOOGLE_AUTH)
    }

    private fun installGms(userID: Int, checkbox: SwitchMaterial) {
        MaterialDialog(this).show {
            title(R.string.enable_gms)
            message(R.string.enable_gms_hint)
            positiveButton(R.string.done) { showLoading(); viewModel.installGms(userID) }
            negativeButton(R.string.cancel) { checkbox.isChecked = false }
        }
    }

    private fun uninstallGms(userID: Int, checkbox: SwitchMaterial) {
        MaterialDialog(this).show {
            title(R.string.disable_gms)
            message(R.string.disable_gms_hint)
            positiveButton(R.string.done) { showLoading(); viewModel.uninstallGms(userID) }
            negativeButton(R.string.cancel) { checkbox.isChecked = true }
        }
    }
}

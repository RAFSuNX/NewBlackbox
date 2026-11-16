package top.niunaijun.blackboxa.data

import androidx.lifecycle.MutableLiveData
import top.niunaijun.blackbox.BlackBoxCore
import top.niunaijun.blackboxa.R
import top.niunaijun.blackboxa.app.AppManager
import top.niunaijun.blackboxa.bean.GmsBean
import top.niunaijun.blackboxa.bean.GmsInstallBean
import top.niunaijun.blackboxa.util.getString

class GmsRepository {

    fun getGmsInstalledList(mInstalledLiveData: MutableLiveData<List<GmsBean>>) {
        val userList = BlackBoxCore.get().users.map { user ->
            val userId = user.id
            val userName = AppManager.mRemarkSharedPreferences
                .getString("Remark$userId", "User $userId") ?: "User $userId"
            val isInstalled = BlackBoxCore.get().isInstallGms(userId)
            val accounts = BlackBoxCore.get().getGoogleAccounts(userId)
            val email = accounts?.firstOrNull()?.name
            GmsBean(userId, userName, isInstalled, email)
        }
        mInstalledLiveData.postValue(userList)
    }

    fun installGms(userID: Int, mUpdateInstalledLiveData: MutableLiveData<GmsInstallBean>) {
        val installResult = BlackBoxCore.get().installGms(userID)
        val result = if (installResult.success) getString(R.string.install_success)
                     else getString(R.string.install_fail, installResult.msg)
        mUpdateInstalledLiveData.postValue(GmsInstallBean(userID, installResult.success, result))
    }

    fun uninstallGms(userID: Int, mUpdateInstalledLiveData: MutableLiveData<GmsInstallBean>) {
        val isSuccess = BlackBoxCore.get().isInstallGms(userID) && BlackBoxCore.get().uninstallGms(userID)
        val result = if (isSuccess) getString(R.string.uninstall_success)
                     else getString(R.string.uninstall_fail)
        mUpdateInstalledLiveData.postValue(GmsInstallBean(userID, isSuccess, result))
    }

    fun addGoogleAccount(email: String, userID: Int): Boolean =
        BlackBoxCore.get().addGoogleAccount(email, userID)

    fun removeGoogleAccount(email: String, userID: Int): Boolean =
        BlackBoxCore.get().removeGoogleAccount(email, userID)
}

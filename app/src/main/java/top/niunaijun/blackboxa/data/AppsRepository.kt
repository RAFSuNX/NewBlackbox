package top.niunaijun.blackboxa.data

import android.content.pm.ApplicationInfo
import android.net.Uri
import android.util.Log
import android.webkit.URLUtil
import androidx.lifecycle.MutableLiveData
import java.io.File
import top.niunaijun.blackbox.BlackBoxCore
import top.niunaijun.blackbox.utils.AbiUtils
import top.niunaijun.blackboxa.R
import top.niunaijun.blackboxa.app.AppManager
import top.niunaijun.blackboxa.bean.AppInfo
import top.niunaijun.blackboxa.bean.InstalledAppBean
import top.niunaijun.blackboxa.util.getString

class AppsRepository {
    val TAG: String = "AppsRepository"
    private var mInstalledList = mutableListOf<AppInfo>()

    private fun safeLoadAppLabel(applicationInfo: ApplicationInfo): String {
        return try {
            BlackBoxCore.getPackageManager().getApplicationLabel(applicationInfo).toString()
        } catch (e: Exception) {
            applicationInfo.packageName
        }
    }

    private fun safeLoadAppIcon(applicationInfo: ApplicationInfo): android.graphics.drawable.Drawable? {
        return try {
            BlackBoxCore.getPackageManager().getApplicationIcon(applicationInfo)
        } catch (e: Exception) {
            null
        }
    }

    fun previewInstallList() {
        try {
            synchronized(mInstalledList) {
                val installedList = BlackBoxCore.getPackageManager()
                    .getInstalledApplications(0)
                    .filter { app ->
                        (app.flags and ApplicationInfo.FLAG_SYSTEM) == 0 &&
                        AbiUtils.isSupport(File(app.sourceDir)) &&
                        !BlackBoxCore.get().isBlackBoxApp(app.packageName)
                    }
                    .map { app ->
                        AppInfo(safeLoadAppLabel(app), safeLoadAppIcon(app),
                            app.packageName, app.sourceDir, false)
                    }
                mInstalledList.clear()
                mInstalledList.addAll(installedList)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in previewInstallList: ${e.message}")
        }
    }

    fun getInstalledAppList(
        userID: Int,
        loadingLiveData: MutableLiveData<Boolean>,
        appsLiveData: MutableLiveData<List<InstalledAppBean>>
    ) {
        try {
            loadingLiveData.postValue(true)
            synchronized(mInstalledList) {
                val core = BlackBoxCore.get()
                val list = mInstalledList.map {
                    InstalledAppBean(it.name, it.icon, it.packageName, it.sourceDir,
                        core.isInstalled(it.packageName, userID))
                }
                appsLiveData.postValue(list)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in getInstalledAppList: ${e.message}")
            appsLiveData.postValue(emptyList())
        } finally {
            loadingLiveData.postValue(false)
        }
    }

    fun getVmInstallList(userId: Int, appsLiveData: MutableLiveData<List<AppInfo>>) {
        try {
            val core = BlackBoxCore.get()
            val sortList = AppManager.mRemarkSharedPreferences
                .getString("AppList$userId", "")
                ?.split(",")

            var applicationList: List<ApplicationInfo>? = null
            repeat(3) { attempt ->
                if (applicationList != null) return@repeat
                try {
                    applicationList = core.getInstalledApplications(0, userId)
                } catch (e: Exception) {
                    Log.e(TAG, "getVmInstallList attempt ${attempt + 1} failed: ${e.message}")
                    if (attempt < 2) Thread.sleep(200)
                }
            }

            if (applicationList == null) {
                appsLiveData.postValue(emptyList())
                return
            }

            val sorted = if (!sortList.isNullOrEmpty())
                applicationList!!.sortedWith(AppsSortComparator(sortList))
            else
                applicationList!!

            val appInfoList = sorted.mapNotNull { app ->
                try {
                    if (app.packageName.isNullOrBlank()) return@mapNotNull null
                    AppInfo(safeLoadAppLabel(app), safeLoadAppIcon(app),
                        app.packageName, app.sourceDir ?: "", false)
                } catch (e: Exception) {
                    Log.e(TAG, "Error processing ${app.packageName}: ${e.message}")
                    null
                }
            }

            appsLiveData.postValue(appInfoList)
        } catch (e: Exception) {
            Log.e(TAG, "Error in getVmInstallList: ${e.message}")
            appsLiveData.postValue(emptyList())
        }
    }

    fun installApk(source: String, userId: Int, resultLiveData: MutableLiveData<String>) {
        try {
            val core = BlackBoxCore.get()
            val installResult = if (URLUtil.isValidUrl(source))
                core.installPackageAsUser(Uri.parse(source), userId)
            else
                core.installPackageAsUser(source, userId)

            if (installResult.success) {
                updateAppSortList(userId, installResult.packageName, true)
                resultLiveData.postValue(getString(R.string.install_success))
            } else {
                resultLiveData.postValue(getString(R.string.install_fail, installResult.msg))
            }
            scanUser()
        } catch (e: Exception) {
            Log.e(TAG, "Error installing APK: ${e.message}")
            resultLiveData.postValue(getString(R.string.install_fail_no_msg))
        }
    }

    fun unInstall(packageName: String, userID: Int, resultLiveData: MutableLiveData<String>) {
        try {
            BlackBoxCore.get().uninstallPackageAsUser(packageName, userID)
            updateAppSortList(userID, packageName, false)
            scanUser()
            resultLiveData.postValue(getString(R.string.uninstall_success))
        } catch (e: Exception) {
            Log.e(TAG, "Error uninstalling: ${e.message}")
            resultLiveData.postValue(getString(R.string.uninstall_fail))
        }
    }

    fun launchApk(packageName: String, userId: Int, launchLiveData: MutableLiveData<Boolean>) {
        try {
            launchLiveData.postValue(BlackBoxCore.get().launchApk(packageName, userId))
        } catch (e: Exception) {
            Log.e(TAG, "Error launching APK: ${e.message}")
            launchLiveData.postValue(false)
        }
    }

    fun clearApkData(packageName: String, userID: Int, resultLiveData: MutableLiveData<String>) {
        try {
            BlackBoxCore.get().clearPackage(packageName, userID)
            resultLiveData.postValue(getString(R.string.clear_success))
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing data: ${e.message}")
            resultLiveData.postValue(getString(R.string.uninstall_fail))
        }
    }

    private fun scanUser() {
        try {
            val core = BlackBoxCore.get()
            val users = core.users
            if (users.isEmpty()) return
            val lastId = users.last().id
            if (core.getInstalledApplications(0, lastId).isEmpty()) {
                core.deleteUser(lastId)
                AppManager.mRemarkSharedPreferences.edit().apply {
                    remove("Remark$lastId")
                    remove("AppList$lastId")
                    apply()
                }
                scanUser()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in scanUser: ${e.message}")
        }
    }

    private fun updateAppSortList(userID: Int, pkg: String, isAdd: Boolean) {
        try {
            val saved = AppManager.mRemarkSharedPreferences.getString("AppList$userID", "") ?: ""
            val sortList = linkedSetOf<String>().also { it.addAll(saved.split(",")) }
            if (isAdd) sortList.add(pkg) else sortList.remove(pkg)
            AppManager.mRemarkSharedPreferences.edit()
                .putString("AppList$userID", sortList.joinToString(","))
                .apply()
        } catch (e: Exception) {
            Log.e(TAG, "Error updating sort list: ${e.message}")
        }
    }

    fun updateApkOrder(userID: Int, dataList: List<AppInfo>) {
        try {
            AppManager.mRemarkSharedPreferences.edit()
                .putString("AppList$userID", dataList.joinToString(",") { it.packageName })
                .apply()
        } catch (e: Exception) {
            Log.e(TAG, "Error updating order: ${e.message}")
        }
    }
}

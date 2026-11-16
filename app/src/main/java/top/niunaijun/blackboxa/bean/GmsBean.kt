package top.niunaijun.blackboxa.bean


data class GmsBean(val userID: Int, val userName: String, var isInstalledGms: Boolean, var googleAccount: String? = null)


data class GmsInstallBean(val userID: Int,val success:Boolean,val msg:String)
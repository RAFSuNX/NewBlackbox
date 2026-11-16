package top.niunaijun.blackboxa.view.gms

import android.view.View
import android.view.ViewGroup
import cbfg.rvadapter.RVHolder
import cbfg.rvadapter.RVHolderFactory
import top.niunaijun.blackboxa.R
import top.niunaijun.blackboxa.bean.GmsBean
import top.niunaijun.blackboxa.databinding.ItemGmsBinding

class GmsAdapter(private val onAccountClick: (GmsBean) -> Unit) : RVHolderFactory() {

    override fun createViewHolder(parent: ViewGroup?, viewType: Int, item: Any): RVHolder<out Any> {
        return GmsVH(inflate(R.layout.item_gms, parent), onAccountClick)
    }

    class GmsVH(
        itemView: View,
        private val onAccountClick: (GmsBean) -> Unit
    ) : RVHolder<GmsBean>(itemView) {

        private val binding = ItemGmsBinding.bind(itemView)

        override fun setContent(item: GmsBean, isSelected: Boolean, payload: Any?) {
            binding.tvTitle.text = item.userName

            // Clear listener before setChecked to avoid spurious click on bind
            binding.checkbox.setOnCheckedChangeListener(null)
            binding.checkbox.isChecked = item.isInstalledGms
            binding.checkbox.setOnCheckedChangeListener { buttonView, _ ->
                if (buttonView.isPressed) binding.root.performClick()
            }

            // Show account row only when GMS is enabled
            if (item.isInstalledGms) {
                binding.accountRow.visibility = View.VISIBLE
                if (item.googleAccount != null) {
                    binding.tvGoogleAccount.text = item.googleAccount
                    binding.btnAddAccount.setText(R.string.change_google_account)
                } else {
                    binding.tvGoogleAccount.setText(R.string.google_account_hint)
                    binding.btnAddAccount.setText(R.string.add_google_account)
                }
                binding.btnAddAccount.setOnClickListener { onAccountClick(item) }
            } else {
                binding.accountRow.visibility = View.GONE
            }
        }
    }
}

package top.niunaijun.blackboxa.view.apps

import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import cbfg.rvadapter.RVHolder
import cbfg.rvadapter.RVHolderFactory
import top.niunaijun.blackboxa.R
import top.niunaijun.blackboxa.bean.AppInfo
import top.niunaijun.blackboxa.databinding.ItemAppBinding

class AppsAdapter : RVHolderFactory() {

    companion object {
        private const val MAX_ICON_SIZE = 96
    }

    override fun createViewHolder(parent: ViewGroup?, viewType: Int, item: Any): RVHolder<out Any> {
        return AppsVH(inflate(R.layout.item_app, parent))
    }

    class AppsVH(itemView: View) : RVHolder<AppInfo>(itemView) {
        val binding = ItemAppBinding.bind(itemView)

        init {
            binding.icon.scaleType = ImageView.ScaleType.FIT_CENTER
        }

        override fun setContent(item: AppInfo, isSelected: Boolean, payload: Any?) {
            binding.icon.setImageDrawable(item.icon?.scaled() ?: placeholderColor())
            binding.name.text = item.name
                ?: itemView.context.getString(R.string.unknown_app)
            binding.cornerLabel.visibility = if (item.isXpModule) View.VISIBLE else View.INVISIBLE
        }

        private fun Drawable.scaled(): Drawable {
            if (this !is BitmapDrawable) return this
            if (bitmap.width <= MAX_ICON_SIZE && bitmap.height <= MAX_ICON_SIZE) return this
            val scaled = Bitmap.createScaledBitmap(bitmap, MAX_ICON_SIZE, MAX_ICON_SIZE, true)
            return BitmapDrawable(itemView.resources, scaled)
        }

        private fun placeholderColor() =
            androidx.core.content.ContextCompat.getDrawable(
                itemView.context, android.R.drawable.sym_def_app_icon
            )
    }
}

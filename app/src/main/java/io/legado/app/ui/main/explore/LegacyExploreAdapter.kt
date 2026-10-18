package io.legado.app.ui.main.explore

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.view.children
import androidx.recyclerview.widget.DiffUtil
import com.google.android.flexbox.FlexboxLayout
import io.legado.app.R
import io.legado.app.base.adapter.ItemViewHolder
import io.legado.app.base.adapter.RecyclerAdapter
import io.legado.app.data.entities.BookSourcePart
import io.legado.app.data.entities.rule.ExploreKind
import io.legado.app.data.entities.rule.apply
import io.legado.app.databinding.ItemFilletTextBinding
import io.legado.app.databinding.ItemFindBookBinding
import io.legado.app.lib.dialogs.SelectItem
import io.legado.app.lib.theme.accentColor
import io.legado.app.ui.widget.PopupAction
import io.legado.app.utils.dpToPx
import io.legado.app.utils.gone
import io.legado.app.utils.removeLastElement
import io.legado.app.utils.visible
import splitties.views.onLongClick

class LegacyExploreAdapter(context: Context, private val callBack: CallBack) :
    RecyclerAdapter<BookSourcePart, ItemFindBookBinding>(context) {

    private val recycledKindViews = arrayListOf<View>()
    private var expandedSourceUrl: String? = null
    private val kindCache = hashMapOf<String, List<ExploreKind>>()
    private val loadingSources = hashSetOf<String>()

    override fun getViewBinding(parent: ViewGroup) =
        ItemFindBookBinding.inflate(inflater, parent, false)

    override fun convert(
        holder: ItemViewHolder,
        binding: ItemFindBookBinding,
        item: BookSourcePart,
        payloads: MutableList<Any>
    ) {
        binding.run {
            val isLast = holder.layoutPosition == itemCount - 1
            root.setPadding(16.dpToPx(), 12.dpToPx(), 16.dpToPx(), if (isLast) 12.dpToPx() else 0)
            tvName.text = item.bookSourceName
            val expanded = item.bookSourceUrl == expandedSourceUrl
            ivStatus.setImageResource(if (expanded) R.drawable.ic_arrow_down else R.drawable.ic_arrow_right)
            if (expanded) {
                if (item.bookSourceUrl in loadingSources) {
                    rotateLoading.loadingColor = context.accentColor
                    rotateLoading.visible()
                } else {
                    rotateLoading.gone()
                }
                kindCache[item.bookSourceUrl]?.let { kinds ->
                    upKindList(flexbox, item.bookSourceUrl, kinds)
                } ?: flexbox.gone()
            } else {
                rotateLoading.gone()
                recycleFlexbox(flexbox)
                flexbox.gone()
            }
        }
    }

    private fun upKindList(flexbox: FlexboxLayout, sourceUrl: String, kinds: List<ExploreKind>) {
        recycleFlexbox(flexbox)
        if (kinds.isEmpty()) {
            flexbox.gone()
            return
        }
        flexbox.visible()
        kinds.forEach { kind ->
            val label = getKindView(flexbox)
            flexbox.addView(label)
            label.text = kind.title
            kind.style().apply(label)
            label.setOnClickListener {
                val url = kind.url
                if (url.isNullOrBlank()) return@setOnClickListener
                if (kind.title.startsWith("ERROR:")) callBack.showError("ERROR", url)
                else callBack.openExplore(sourceUrl, kind.title, url)
            }
        }
    }

    @Synchronized
    private fun getKindView(parent: FlexboxLayout): TextView =
        if (recycledKindViews.isEmpty()) ItemFilletTextBinding.inflate(inflater, parent, false).root
        else recycledKindViews.removeLastElement() as TextView

    @Synchronized
    private fun recycleFlexbox(flexbox: FlexboxLayout) {
        recycledKindViews.addAll(flexbox.children.toList())
        flexbox.removeAllViews()
    }

    fun setExpanded(sourceUrl: String?) {
        val previous = expandedSourceUrl
        expandedSourceUrl = sourceUrl
        previous?.let { indexOfSource(it)?.let(::notifyItemChanged) }
        sourceUrl?.let { indexOfSource(it)?.let(::notifyItemChanged) }
    }

    fun setLoading(sourceUrl: String, loading: Boolean) {
        if (loading) loadingSources.add(sourceUrl) else loadingSources.remove(sourceUrl)
        indexOfSource(sourceUrl)?.let(::notifyItemChanged)
    }

    fun showKinds(sourceUrl: String, kinds: List<ExploreKind>) {
        kindCache[sourceUrl] = kinds
        indexOfSource(sourceUrl)?.let(::notifyItemChanged)
    }

    fun clearKinds(sourceUrl: String) {
        kindCache.remove(sourceUrl)
        indexOfSource(sourceUrl)?.let(::notifyItemChanged)
    }

    private fun indexOfSource(sourceUrl: String) = getItems().indexOfFirst {
        it.bookSourceUrl == sourceUrl
    }.takeIf { it >= 0 }

    override fun registerListener(holder: ItemViewHolder, binding: ItemFindBookBinding) {
        binding.llTitle.setOnClickListener {
            val source = getItem(holder.layoutPosition) ?: return@setOnClickListener
            val previous = expandedSourceUrl
            val next = if (previous == source.bookSourceUrl) null else source.bookSourceUrl
            if (previous != null && previous != next) {
                callBack.collapse(previous)
            }
            setExpanded(next)
            if (next != null) callBack.loadKinds(source, holder.layoutPosition)
        }
        binding.llTitle.onLongClick {
            showMenu(binding.llTitle, holder.layoutPosition)
        }
    }

    private fun showMenu(anchor: View, position: Int): Boolean {
        val source = getItem(position) ?: return true
        val items = buildList {
            add(SelectItem(context.getString(R.string.edit), "edit"))
            add(SelectItem(context.getString(R.string.to_top), "top"))
            if (source.hasLoginUrl) add(SelectItem(context.getString(R.string.login), "login"))
            add(SelectItem(context.getString(R.string.search), "search"))
            add(SelectItem(context.getString(R.string.refresh), "refresh"))
            add(SelectItem(context.getString(R.string.delete), "delete"))
        }
        PopupAction(context).apply {
            setVertical(true)
            setDangerValues(setOf("delete"))
            setItems(items)
            onActionClick = { action ->
                when (action) {
                    "edit" -> callBack.editSource(source.bookSourceUrl)
                    "top" -> callBack.toTop(source)
                    "login" -> callBack.login(source)
                    "search" -> callBack.searchBook(source)
                    "refresh" -> callBack.refresh(source, position)
                    "delete" -> callBack.deleteSource(source)
                }
                dismiss()
            }
            showAsDropDown(anchor, 0, 4.dpToPx())
        }
        return true
    }

    interface CallBack {
        fun loadKinds(source: BookSourcePart, position: Int)
        fun collapse(sourceUrl: String)
        fun openExplore(sourceUrl: String, title: String, exploreUrl: String?)
        fun editSource(sourceUrl: String)
        fun toTop(source: BookSourcePart)
        fun deleteSource(source: BookSourcePart)
        fun searchBook(source: BookSourcePart)
        fun login(source: BookSourcePart)
        fun refresh(source: BookSourcePart, position: Int)
        fun showError(title: String, message: String)
    }

    companion object {
        val DIFF = object : DiffUtil.ItemCallback<BookSourcePart>() {
            override fun areItemsTheSame(oldItem: BookSourcePart, newItem: BookSourcePart) =
                oldItem.bookSourceUrl == newItem.bookSourceUrl

            override fun areContentsTheSame(oldItem: BookSourcePart, newItem: BookSourcePart) =
                oldItem.bookSourceName == newItem.bookSourceName &&
                        oldItem.bookSourceGroup == newItem.bookSourceGroup
        }
    }
}

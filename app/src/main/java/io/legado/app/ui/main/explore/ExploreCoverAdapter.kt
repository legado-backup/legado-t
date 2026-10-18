package io.legado.app.ui.main.explore

import android.content.Context
import android.view.ViewGroup
import androidx.core.view.isVisible
import io.legado.app.base.adapter.ItemViewHolder
import io.legado.app.base.adapter.RecyclerAdapter
import io.legado.app.data.entities.SearchBook
import io.legado.app.databinding.ItemExploreCoverBinding
import io.legado.app.help.config.AppConfig
import io.legado.app.lib.theme.coverCardBackgroundColor
import splitties.views.onLongClick

/**
 * 横滑样式容器的封面条目
 */
class ExploreCoverAdapter(context: Context, val callBack: CallBack) :
    RecyclerAdapter<SearchBook, ItemExploreCoverBinding>(context) {

    /** 长按转发到所属容器卡片的菜单 */
    var onItemLongClick: (() -> Unit)? = null

    private var boundBooks: List<SearchBook>? = null

    /** 引用相同则跳过,避免 loading 翻转时无谓的全量重绑 */
    fun setBooks(books: List<SearchBook>) {
        if (boundBooks === books) return
        boundBooks = books
        setItems(books)
    }

    override fun getViewBinding(parent: ViewGroup): ItemExploreCoverBinding {
        return ItemExploreCoverBinding.inflate(inflater, parent, false)
    }

    override fun convert(
        holder: ItemViewHolder,
        binding: ItemExploreCoverBinding,
        item: SearchBook,
        payloads: MutableList<Any>
    ) {
        binding.run {
            coverCard.setCardBackgroundColor(context.coverCardBackgroundColor)
            if (payloads.isEmpty()) {
                tvName.text = item.name
                ivInBookshelf.isVisible = callBack.isInBookshelf(item)
                ivCover.load(
                    item.coverUrl,
                    item.name,
                    item.author,
                    AppConfig.loadCoverOnlyWifi,
                    item.origin
                )
            } else {
                ivInBookshelf.isVisible = callBack.isInBookshelf(item)
            }
        }
    }

    override fun registerListener(holder: ItemViewHolder, binding: ItemExploreCoverBinding) {
        binding.root.setOnClickListener {
            getItem(holder.layoutPosition)?.let { callBack.showBookInfo(it) }
        }
        binding.root.onLongClick {
            onItemLongClick?.invoke()
        }
    }

    interface CallBack {
        fun isInBookshelf(book: SearchBook): Boolean
        fun showBookInfo(book: SearchBook)
    }
}

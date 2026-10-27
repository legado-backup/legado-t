package io.legado.app.ui.rss.article

import android.app.Application
import android.os.Bundle
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import io.legado.app.base.BaseViewModel
import io.legado.app.constant.AppLog
import io.legado.app.data.appDb
import io.legado.app.data.entities.RssArticle
import io.legado.app.data.entities.RssSource
import io.legado.app.help.coroutine.Coroutine
import io.legado.app.model.rss.Rss
import io.legado.app.utils.stackTraceStr
import kotlinx.coroutines.Dispatchers.IO


class RssArticlesViewModel(application: Application) : BaseViewModel(application) {
    val loadFinallyLiveData = MutableLiveData<Boolean>()
    val loadErrorLiveData = MutableLiveData<String>()
    val searchArticlesLiveData = MutableLiveData<List<RssArticle>>()
    var isLoading = true
    var order = System.currentTimeMillis()
    private var nextPageUrl: String? = null
    private var searchNextPageUrl: String? = null
    private val searchArticles = mutableListOf<RssArticle>()
    private var searchRequest: Coroutine<*>? = null
    @Volatile
    private var searchRequestGeneration = 0
    private var searchInitialLoadFailed = false
    var searchKey: String = ""
    var isSearchMode: Boolean = false
        private set
    var sortName: String = ""
    var sortUrl: String = ""
    var page = 1

    fun init(bundle: Bundle?) {
        bundle?.let {
            sortName = it.getString("sortName") ?: ""
            sortUrl = it.getString("sortUrl") ?: ""
            isSearchMode = it.getBoolean("searchMode", false)
            searchKey = it.getString("searchKey")?.trim().orEmpty()
        }
    }

    fun loadArticles(rssSource: RssSource) {
        if (isSearchMode) {
            loadSearchArticles(rssSource)
            return
        }
        isLoading = true
        page = 1
        loadErrorLiveData.postValue("")
        order = System.currentTimeMillis()
        Rss.getArticles(viewModelScope, sortName, sortUrl, rssSource, page).onSuccess(IO) {
            nextPageUrl = it.second
            val articles = it.first
            articles.forEach { rssArticle ->
                rssArticle.order = order--
            }
            appDb.rssArticleDao.insert(*articles.toTypedArray())
            if (!rssSource.ruleNextPage.isNullOrEmpty()) {
                appDb.rssArticleDao.clearOld(rssSource.sourceUrl, sortName, order)
            }
            val hasMore = articles.isNotEmpty() && !nextPageUrl.isNullOrBlank()
            loadFinallyLiveData.postValue(hasMore)
            isLoading = false
        }.onError {
            loadFinallyLiveData.postValue(false)
            AppLog.put("rss获取内容失败", it)
            loadErrorLiveData.postValue(it.stackTraceStr)
            isLoading = false
        }
    }

    private fun loadSearchArticles(rssSource: RssSource) {
        val searchUrl = rssSource.searchUrl?.takeIf { it.isNotBlank() }
        val key = searchKey.trim()
        if (searchUrl == null || key.isEmpty()) {
            searchArticles.clear()
            searchArticlesLiveData.postValue(emptyList())
            loadFinallyLiveData.postValue(false)
            isLoading = false
            return
        }
        searchRequest?.cancel()
        val generation = ++searchRequestGeneration
        isLoading = true
        page = 1
        searchNextPageUrl = null
        searchArticles.clear()
        searchArticlesLiveData.postValue(emptyList())
        searchInitialLoadFailed = false
        loadErrorLiveData.postValue("")
        searchRequest = Rss.searchArticles(
            viewModelScope,
            sortName,
            searchUrl,
            key,
            rssSource,
            page
        ).onSuccess {
            if (generation != searchRequestGeneration) return@onSuccess
            searchNextPageUrl = it.second
            val added = appendSearchArticles(it.first)
            page = 1
            if (added == 0) searchNextPageUrl = null
            loadFinallyLiveData.postValue(added > 0 && !searchNextPageUrl.isNullOrBlank())
            isLoading = false
        }.onError {
            if (generation != searchRequestGeneration) return@onError
            searchInitialLoadFailed = true
            loadFinallyLiveData.postValue(false)
            AppLog.put("rss搜索失败", it)
            loadErrorLiveData.postValue(it.stackTraceStr)
            isLoading = false
        }
    }

    fun loadMore(rssSource: RssSource) {
        if (isSearchMode) {
            if (searchInitialLoadFailed) {
                loadSearchArticles(rssSource)
            } else {
                loadMoreSearchArticles(rssSource)
            }
            return
        }
        val pageUrl = nextPageUrl
        if (pageUrl.isNullOrEmpty() || isLoading) return
        val requestedPage = page + 1
        isLoading = true
        Rss.getArticles(viewModelScope, sortName, pageUrl, rssSource, requestedPage).onSuccess(IO) {
            page = requestedPage
            nextPageUrl = it.second
            loadMoreSuccess(it.first)
            isLoading = false
        }.onError {
            loadFinallyLiveData.postValue(false)
            AppLog.put("rss获取内容失败", it)
            loadErrorLiveData.postValue(it.stackTraceStr)
            isLoading = false
        }
    }

    private fun loadMoreSearchArticles(rssSource: RssSource) {
        val pageUrl = searchNextPageUrl
        if (pageUrl.isNullOrEmpty() || isLoading) return
        val requestedPage = page + 1
        val generation = searchRequestGeneration
        isLoading = true
        Rss.searchArticles(
            viewModelScope,
            sortName,
            pageUrl,
            searchKey,
            rssSource,
            requestedPage
        ).onSuccess {
            if (generation != searchRequestGeneration) return@onSuccess
            page = requestedPage
            searchNextPageUrl = it.second
            val added = appendSearchArticles(it.first)
            if (added == 0) searchNextPageUrl = null
            loadFinallyLiveData.postValue(added > 0 && !searchNextPageUrl.isNullOrBlank())
            isLoading = false
        }.onError {
            if (generation != searchRequestGeneration) return@onError
            loadFinallyLiveData.postValue(false)
            AppLog.put("rss搜索失败", it)
            loadErrorLiveData.postValue(it.stackTraceStr)
            isLoading = false
        }
    }

    private fun appendSearchArticles(articles: MutableList<RssArticle>): Int {
        val existing = searchArticles.asSequence()
            .map { "${it.origin}\u0000${it.link}" }
            .toHashSet()
        var added = 0
        articles.forEach { article ->
            if (existing.add("${article.origin}\u0000${article.link}")) {
                searchArticles.add(article)
                added++
            }
        }
        searchArticlesLiveData.postValue(searchArticles.toList())
        return added
    }

    private fun loadMoreSuccess(articles: MutableList<RssArticle>) {
        if (articles.isEmpty()) {
            loadFinallyLiveData.postValue(false)
            return
        }
        val firstArticle = articles.first()
        val dbFirstArticle = appDb.rssArticleDao.get(firstArticle.origin, firstArticle.link)
        val lastArticle = articles.last()
        val dbLastArticle = appDb.rssArticleDao.get(lastArticle.origin, lastArticle.link)
        if (dbFirstArticle != null && dbLastArticle != null) {
            loadFinallyLiveData.postValue(false)
        } else {
            articles.forEach {
                it.order = order--
            }
            appDb.rssArticleDao.append(*articles.toTypedArray())
        }
    }

}
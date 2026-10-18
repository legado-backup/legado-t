package io.legado.app.ui.main.explore

import android.app.Application
import io.legado.app.base.BaseViewModel
import io.legado.app.data.appDb
import io.legado.app.data.entities.BookSourcePart
import io.legado.app.help.source.SourceHelp

class LegacyExploreViewModel(application: Application) : BaseViewModel(application) {

    fun topSource(source: BookSourcePart) {
        execute {
            source.customOrder = appDb.bookSourceDao.minOrder - 1
            appDb.bookSourceDao.upOrder(source)
        }
    }

    fun deleteSource(source: BookSourcePart) {
        execute {
            SourceHelp.deleteBookSource(source.bookSourceUrl)
        }
    }
}

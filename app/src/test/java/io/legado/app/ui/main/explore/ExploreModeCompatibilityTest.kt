package io.legado.app.ui.main.explore

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ExploreModeCompatibilityTest {

    @Test
    fun `legacy mode is a real discover page selected by the preference`() {
        val key = readProjectFile("src/main/java/io/legado/app/constant/PreferKey.kt")
        val config = readProjectFile("src/main/java/io/legado/app/help/config/AppConfig.kt")
        val settings = readProjectFile("src/main/res/xml/pref_config_other.xml")
        val main = readProjectFile("src/main/java/io/legado/app/ui/main/MainActivity.kt")
        val legacy = readProjectFile("src/main/java/io/legado/app/ui/main/explore/LegacyExploreFragment.kt")
        val layout = readProjectFile("src/main/res/layout/fragment_explore_legacy.xml")
        val row = readProjectFile("src/main/res/layout/item_find_book.xml")

        assertTrue(key.contains("const val legacyExplore = \"legacyExplore\""))
        assertTrue(config.contains("getPrefBoolean(PreferKey.legacyExplore, false)"))
        assertTrue(settings.contains("android:key=\"legacyExplore\""))
        assertTrue(settings.contains("android:defaultValue=\"false\""))
        assertTrue(main.contains("if (AppConfig.legacyExplore) idLegacyExplore else idExplore"))
        assertTrue(main.contains("idLegacyExplore -> LegacyExploreFragment(position)"))
        assertTrue(legacy.contains("appDb.bookSourceDao.flowExplore()"))
        assertTrue(legacy.contains("source.exploreKinds()"))
        assertTrue(layout.contains("@layout/view_search"))
        assertTrue(row.contains("@+id/flexbox"))
    }

    private fun readProjectFile(pathInApp: String): String {
        val candidates = listOf(File(pathInApp), File("app/$pathInApp"))
        return candidates.firstOrNull { it.isFile }?.readText().orEmpty()
    }
}

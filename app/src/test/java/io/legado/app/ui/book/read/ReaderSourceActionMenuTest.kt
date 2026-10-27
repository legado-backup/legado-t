package io.legado.app.ui.book.read

import io.legado.app.ProjectSources
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderSourceActionMenuTest {

    @Test
    fun `reader source action uses pill chip styling`() {
        // view_read_menu.xml 的 radius 已随 N3d 归位为 token(@dimen/radius_l,近拟合 16dp);
        // view_manga_menu.xml 不在 N3d 改动范围内,保留字面量断言。
        val radiusAssertion = mapOf(
            "view_read_menu.xml" to "app:radius=\"@dimen/radius_l\"",
            "view_manga_menu.xml" to "app:radius=\"16dp\"",
        )
        radiusAssertion.forEach { (layoutName, radiusExpectation) ->
            val xml = ProjectSources.app("src/main/res/layout/$layoutName")
            assertContains(layoutName, xml, "android:id=\"@+id/tv_source_action\"")
            assertContains(layoutName, xml, "android:minHeight=\"32dp\"")
            assertContains(layoutName, xml, "android:paddingStart=\"@dimen/space_m\"")
            assertContains(layoutName, xml, "android:paddingEnd=\"@dimen/space_s\"")
            assertContains(layoutName, xml, "app:drawableEndCompat=\"@drawable/ic_arrow_drop_down\"")
            assertContains(layoutName, xml, "app:drawableTint=\"@color/primaryText\"")
            assertContains(layoutName, xml, radiusExpectation)
            assertFalse("$layoutName should not keep the old 2dp square radius", xml.contains("app:radius=\"2dp\""))
        }
    }

    @Test
    fun `reader source action menu uses custom popup instead of system popup menu`() {
        val readMenuKt = ProjectSources.app("src/main/java/io/legado/app/ui/book/read/ReadMenu.kt")

        assertFalse(readMenuKt.contains("PopupMenu(context, binding.tvSourceAction)"))
        assertContains("ReadMenu.kt", readMenuKt, "private val sourceActionMenu by lazy")
        assertContains("ReadMenu.kt", readMenuKt, "PopupAction(context)")
        assertContains("ReadMenu.kt", readMenuKt, "setVertical(true)")
        assertContains("ReadMenu.kt", readMenuKt, "setDangerValues(setOf(\"disableSource\"))")
        assertContains("ReadMenu.kt", readMenuKt, "showAsDropDown(binding.tvSourceAction")
    }

    @Test
    fun `reader source action menu uses shared login configuration check`() {
        val readMenuKt = ProjectSources.app("src/main/java/io/legado/app/ui/book/read/ReadMenu.kt")

        assertContains("ReadMenu.kt", readMenuKt, "import io.legado.app.ui.login.hasSourceLoginConfiguration")
        assertContains(
            "ReadMenu.kt",
            readMenuKt,
            "ReadBook.bookSource?.let(::hasSourceLoginConfiguration) == true"
        )
    }

    @Test
    fun `popup action supports vertical source menus and danger items`() {
        val popupActionKt = ProjectSources.app("src/main/java/io/legado/app/ui/widget/PopupAction.kt")

        assertContains("PopupAction.kt", popupActionKt, "fun setVertical(vertical: Boolean)")
        assertContains("PopupAction.kt", popupActionKt, "fun setDangerValues(values: Set<String>)")
        assertContains("PopupAction.kt", popupActionKt, "LinearLayoutManager(context)")
        assertContains("PopupAction.kt", popupActionKt, "FlexboxLayoutManager(context)")
        assertContains("PopupAction.kt", popupActionKt, "context.getCompatColor(R.color.error)")
    }

    private fun assertContains(name: String, text: String, expected: String) {
        assertTrue("$name should contain $expected", text.contains(expected))
    }

}

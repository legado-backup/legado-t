package io.legado.app.ui.menu

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class MenuPopupStyleTest {

    @Test
    fun `popup menu surface uses md3 card shape and comfortable row height`() {
        val popupBackgroundXml = readProjectFile("src/main/res/drawable/bg_popup_menu.xml")
        val stylesXml = readProjectFile("src/main/res/values/styles.xml")

        assertContains(popupBackgroundXml, "<solid android:color=\"@color/background_popup_menu\"")
        assertContains(popupBackgroundXml, "<corners android:radius=\"@dimen/radius_m\"")
        // M3 菜单靠阴影分层,不用描边(系统 overflow 走 popupElevation,自定义弹窗走 applyMd3PopupStyle 的 elevation)
        assertFalse(
            "popup menu surface should rely on elevation instead of stroke",
            popupBackgroundXml.contains("<stroke")
        )
        assertContains(stylesXml, "<item name=\"android:popupElevation\">3dp</item>")
        assertContains(stylesXml, "<item name=\"android:listPreferredItemHeightSmall\">48dp</item>")
    }

    @Test
    fun `custom popup windows use shared md3 menu surface`() {
        listOf(
            "popup_action.xml",
            "popup_keyboard_tool.xml",
            "popup_seek_bar.xml"
        ).forEach { layoutName ->
            val xml = readProjectFile("src/main/res/layout/$layoutName")
            assertContains(xml, "android:background=\"@drawable/bg_popup_menu\"")
        }
    }

    @Test
    fun `popup menu items have md3 touch targets and rounded ripple`() {
        val menuItemBackgroundXml = readProjectFile("src/main/res/drawable/selector_menu_item_bg.xml")
        val dropdownItemXml = readProjectFile("src/main/res/layout/item_1line_text_and_del.xml")

        assertContains(menuItemBackgroundXml, "android:state_pressed=\"true\"")
        assertContains(menuItemBackgroundXml, "android:state_focused=\"true\"")
        assertContains(menuItemBackgroundXml, "<corners android:radius=\"@dimen/radius_s\"")
        assertContains(menuItemBackgroundXml, "<solid android:color=\"@color/background_menu\"")

        assertContains(dropdownItemXml, "android:background=\"@drawable/selector_menu_item_bg\"")
        assertContains(dropdownItemXml, "android:minHeight=\"48dp\"")
        assertContains(dropdownItemXml, "android:paddingStart=\"@dimen/space_l\"")
        assertContains(dropdownItemXml, "android:paddingEnd=\"@dimen/space_s\"")
        assertContains(dropdownItemXml, "android:layout_width=\"40dp\"")
        assertContains(dropdownItemXml, "android:layout_height=\"40dp\"")
    }

    @Test
    fun `popup window implementations apply transparent window background and elevation`() {
        val extensionKt = readProjectFile("src/main/java/io/legado/app/utils/PopupWindowExtensions.kt")

        assertContains(extensionKt, "fun PopupWindow.applyMd3PopupStyle()")
        assertContains(extensionKt, "setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))")
        assertContains(extensionKt, "elevation = 8f.dpToPx()")
        assertContains(extensionKt, "contentView?.let { it.background = it.context.popupBackground }")

        listOf(
            "src/main/java/io/legado/app/ui/widget/PopupAction.kt",
            "src/main/java/io/legado/app/ui/book/audio/TimerSliderPopup.kt",
            "src/main/java/io/legado/app/ui/widget/keyboard/KeyboardToolPop.kt"
        ).forEach { path ->
            assertContains(readProjectFile(path), "applyMd3PopupStyle()")
        }
    }

    @Test
    fun `autocomplete dropdown clears square popup offsets and uses md3 window background`() {
        val autoCompleteTextViewKt = readProjectFile(
            "src/main/java/io/legado/app/ui/widget/text/AutoCompleteTextView.kt"
        )

        assertContains(autoCompleteTextViewKt, "setDropDownBackgroundDrawable(context.popupBackground)")
        assertContains(autoCompleteTextViewKt, "dropDownVerticalOffset = 4.dpToPx()")
        assertContains(autoCompleteTextViewKt, "dropDownHorizontalOffset = 0")
    }

    @Test
    fun `popup action avoids redundant empty adapter invalidations`() {
        val popupActionKt = readProjectFile("src/main/java/io/legado/app/ui/widget/PopupAction.kt")

        assertFalse("PopupAction should rely on XML for its default horizontal layout manager", popupActionKt.contains("setVertical(false)"))
        assertContains(popupActionKt, "if (isVertical == vertical && binding.recyclerView.layoutManager != null)")
        assertContains(popupActionKt, "if (adapter.itemCount > 0)")
        assertContains(popupActionKt, "if (dangerValues == values)")
    }

    @Test
    fun `shared popup style applies runtime theme background`() {
        val extensionKt = readProjectFile("src/main/java/io/legado/app/utils/PopupWindowExtensions.kt")

        assertContains(extensionKt, "contentView?.let { it.background = it.context.popupBackground }")
    }

    private fun assertContains(text: String, expected: String) {
        assertTrue("Expected to contain $expected", text.contains(expected))
    }

    private fun readProjectFile(pathInApp: String): String {
        val candidates = listOf(
            File(pathInApp),
            File("app/$pathInApp")
        )
        return candidates.firstOrNull { it.isFile }?.readText().orEmpty()
    }
}

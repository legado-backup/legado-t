package io.legado.app.ui.nostr

import io.legado.app.ProjectSources
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NostrSignerDialogLayoutTest {

    @Test
    fun bunkerInputUsesFloatingHintAndReadableSingleLineLayout() {
        val xml = ProjectSources.app("src/main/res/layout/dialog_nostr_signer.xml")
        val controller = ProjectSources.app("src/main/java/io/legado/app/ui/nostr/NostrSignerDialogController.kt")
        val inputStart = xml.indexOf("android:id=\"@+id/et_bunker\"")
        assertTrue("bunker field not found", inputStart >= 0)
        val inputEnd = xml.indexOf("/>" , inputStart)
        assertTrue("bunker field declaration not closed", inputEnd >= 0)
        val input = xml.substring(inputStart, inputEnd)
        val wrapperStart = xml.lastIndexOf("<io.legado.app.ui.widget.text.TextInputLayout", inputStart)
        val wrapperEnd = xml.indexOf("</io.legado.app.ui.widget.text.TextInputLayout>", inputStart)
        assertTrue("bunker TextInputLayout not found", wrapperStart >= 0 && wrapperEnd > inputStart)
        val wrapper = xml.substring(wrapperStart, wrapperEnd)

        assertTrue("hint should float inside the outlined field", wrapper.contains("android:hint=\"@string/nostr_signer_bunker_hint\""))
        assertFalse("hint should not overlap the input text at the leading edge", input.contains("android:hint="))
        assertTrue("input text needs horizontal inset", input.contains("android:paddingStart=\"16dp\""))
        assertTrue("bunker input should meet the minimum touch height", input.contains("android:minHeight=\"48dp\""))
        assertTrue("bunker input text needs vertical inset", input.contains("android:paddingTop=\"6dp\"") && input.contains("android:paddingBottom=\"6dp\""))
        assertTrue("bunker URI must remain single-line", input.contains("android:singleLine=\"true\""))
        assertTrue("input text size should match standard body text", input.contains("android:textSize=\"16sp\""))
        assertTrue("hint should use the runtime secondary text color", controller.contains("binding.tilBunker.hintTextColor = ColorStateList.valueOf(secondaryColor)"))
        assertTrue("login action should use the runtime primary text color", controller.contains("binding.btnBunkerLogin.setTextColor(context.primaryTextColor)"))
    }

}

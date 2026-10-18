package io.legado.app

import com.google.gson.Gson
import io.legado.app.help.update.GithubRelease
import io.legado.app.help.update.UpdateManifestSelector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 当前更新 API 的离线回归，避免依赖已删除的 gitReleaseToAppReleaseInfo 或远程滚动发布。 */
class UpdateTest {
    @Test
    fun betaRollingTagUsesReleaseTitleAndSelectsDeviceAbi() {
        val manifest = release("beta", true).toUpdateManifest()
        assertEquals("beta", manifest.channel)
        assertEquals("3.26.101812", manifest.versionName)
        val artifact = UpdateManifestSelector.selectArtifact(manifest.artifacts, listOf("x86_64"))!!
        assertTrue(artifact.fileName.contains("x86_64"))
        assertTrue(artifact.url.isNotBlank())
        assertEquals(2, manifest.artifacts.size)
    }

    @Test
    fun officialReleaseUsesVersionTagAndFiltersNonApks() {
        val manifest = release("3.26.101812", false).toUpdateManifest()
        assertEquals("release", manifest.channel)
        assertEquals("3.26.101812", manifest.versionName)
        assertTrue(manifest.artifacts.all { it.fileName.endsWith(".apk") })
        assertEquals(2, manifest.artifacts.size)
    }

    private fun release(tag: String, prerelease: Boolean): GithubRelease = Gson().fromJson(
        """{
            "tag_name":"$tag", "name":"legado_app_3.26.101812", "prerelease":$prerelease,
            "assets":[
                {"name":"legado_arm64-v8a.apk","browser_download_url":"https://example.test/arm64.apk","state":"uploaded"},
                {"name":"legado_x86_64.apk","browser_download_url":"https://example.test/x86_64.apk","state":"uploaded"},
                {"name":"source.zip","browser_download_url":"https://example.test/source.zip","state":"uploaded"}
            ]
        }""".trimIndent(),
        GithubRelease::class.java,
    )
}

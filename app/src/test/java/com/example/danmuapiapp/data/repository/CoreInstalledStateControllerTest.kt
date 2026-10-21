package com.example.danmuapiapp.data.repository

import com.example.danmuapiapp.domain.model.*
import com.example.danmuapiapp.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CoreInstalledStateControllerTest {
    private class FakeSettings : SettingsRepository {
        val customWrites = mutableListOf<Pair<String, String>>()
        val branchWrites = mutableListOf<Pair<ApiVariant, String>>()
        override val githubProxy: StateFlow<String> = MutableStateFlow("")
        override val announcementBaseUrl: StateFlow<String> = MutableStateFlow("")
        override val autoStart: StateFlow<Boolean> = MutableStateFlow(false)
        override val keepAlive: StateFlow<Boolean> = MutableStateFlow(false)
        override val keepAliveHeartbeatEnabled: StateFlow<Boolean> = MutableStateFlow(false)
        override val keepAliveHeartbeatMode: StateFlow<KeepAliveHeartbeatMode> = MutableStateFlow(KeepAliveHeartbeatMode.Accessibility)
        override val keepAliveHeartbeatIntervalMinutes: StateFlow<Int> = MutableStateFlow(0)
        override val coreUpdateCheckIntervalMinutes: StateFlow<Int> = MutableStateFlow(0)
        override val normalModeStabilityMode: StateFlow<NormalModeStabilityMode> = MutableStateFlow(NormalModeStabilityMode.Auto)
        override val normalNotificationBehavior: StateFlow<NormalNotificationBehavior> = MutableStateFlow(NormalNotificationBehavior.ForegroundRestore)
        override val serviceNotificationInfoEnabled: StateFlow<Boolean> = MutableStateFlow(false)
        override val rootNotificationEnabled: StateFlow<Boolean> = MutableStateFlow(false)
        override val accessEntryDefaultTab: StateFlow<AccessEntryTab> = MutableStateFlow(AccessEntryTab.Ipv4)
        override val accessEntryLayout: StateFlow<AccessEntryLayout> = MutableStateFlow(AccessEntryLayout.Tabs)
        override val nightMode: StateFlow<NightModePreference> = MutableStateFlow(NightModePreference.FollowSystem)
        override val glassMaterial: StateFlow<GlassMaterialPreference> = MutableStateFlow(GlassMaterialPreference.Off)
        override val glassBottomBar: StateFlow<Boolean> = MutableStateFlow(false)
        override val glassTuning: StateFlow<GlassTuningPreference> = MutableStateFlow(GlassTuningPreference())
        override val appBackground: StateFlow<AppBackgroundPreference> = MutableStateFlow(AppBackgroundPreference())
        override val appDpiOverride: StateFlow<Int> = MutableStateFlow(0)
        override val compatDpiOverride: StateFlow<Int> = MutableStateFlow(0)
        override val hideFromRecents: StateFlow<Boolean> = MutableStateFlow(false)
        override val coreDisplayNames: StateFlow<CoreVariantDisplayNames> = MutableStateFlow(CoreVariantDisplayNames())
        override val coreBranchSelections: StateFlow<CoreBranchSelections> = MutableStateFlow(CoreBranchSelections())
        override val customCoreSource: StateFlow<ResolvedCustomCoreSource> = MutableStateFlow(ResolvedCustomCoreSource())
        override val customRepo: StateFlow<String> = MutableStateFlow("")
        override val customRepoBranch: StateFlow<String> = MutableStateFlow("")
        override val customRepoDisplayName: StateFlow<String> = MutableStateFlow("")
        override val tokenVisible: StateFlow<Boolean> = MutableStateFlow(false)
        override val fileLogEnabled: StateFlow<Boolean> = MutableStateFlow(false)
        override val logEnabled: StateFlow<Boolean> = MutableStateFlow(false)
        override val logPreviewEnabled: StateFlow<Boolean> = MutableStateFlow(false)
        override val logMaxCount: StateFlow<Int> = MutableStateFlow(0)
        override fun saveCustomCoreSource(repoInput: String, branchInput: String) =
            com.example.danmuapiapp.domain.model.resolveCustomCoreSource(repoInput, branchInput).also {
                customWrites += repoInput to branchInput
            }
        override fun setCoreBranch(variant: ApiVariant, branch: String) {
            branchWrites += variant to branch
        }
        override fun setGithubProxy(proxy: String) = Unit
        override fun setGithubToken(token: String) = Unit
        override fun setAutoStart(enabled: Boolean) = Unit
        override fun setKeepAlive(enabled: Boolean) = Unit
        override fun setKeepAliveHeartbeatEnabled(enabled: Boolean) = Unit
        override fun setKeepAliveHeartbeatMode(mode: KeepAliveHeartbeatMode) = Unit
        override fun setKeepAliveHeartbeatIntervalMinutes(minutes: Int) = Unit
        override fun setCoreUpdateCheckIntervalMinutes(minutes: Int) = Unit
        override fun setNormalModeStabilityMode(mode: NormalModeStabilityMode) = Unit
        override fun setNormalNotificationBehavior(behavior: NormalNotificationBehavior) = Unit
        override fun setServiceNotificationInfoEnabled(enabled: Boolean) = Unit
        override fun setRootNotificationEnabled(enabled: Boolean) = Unit
        override fun setAccessEntryDefaultTab(tab: AccessEntryTab) = Unit
        override fun setAccessEntryLayout(layout: AccessEntryLayout) = Unit
        override fun setNightMode(mode: NightModePreference) = Unit
        override fun setGlassMaterial(material: GlassMaterialPreference) = Unit
        override fun setGlassBottomBar(enabled: Boolean) = Unit
        override fun setGlassTuning(tuning: GlassTuningPreference) = Unit
        override fun setAppBackground(background: AppBackgroundPreference) = Unit
        override fun setAppDpiOverride(dpi: Int) = Unit
        override fun setCompatDpiOverride(dpi: Int) = Unit
        override fun setHideFromRecents(enabled: Boolean) = Unit
        override fun setVariantDisplayName(variant: ApiVariant, name: String) = Unit
        override fun saveCustomCoreConfig(displayName: String, repoInput: String, branchInput: String): ResolvedCustomCoreConfig = ResolvedCustomCoreConfig()
        override fun setCustomRepo(repo: String) = Unit
        override fun setCustomRepoBranch(branch: String) = Unit
        override fun setCustomRepoDisplayName(name: String) = Unit
        override fun setTokenVisible(visible: Boolean) = Unit
        override fun setFileLogEnabled(enabled: Boolean) = Unit
        override fun setLogEnabled(enabled: Boolean) = Unit
        override fun setLogPreviewEnabled(enabled: Boolean) = Unit
        override fun setLogMaxCount(count: Int) = Unit
        override fun getIgnoredUpdateVersion(variant: ApiVariant): String? = null
        override fun setIgnoredUpdateVersion(variant: ApiVariant, version: String?) = Unit
        override fun reloadFromStorage() = Unit
    }

    private fun controller(
        settings: FakeSettings,
        repo: String = "owner/core",
        branch: String? = "main",
        info: List<CoreInfo> = listOf(CoreInfo(ApiVariant.Stable, "1.0.0", true)),
        updates: MutableList<List<CoreInfo>> = mutableListOf(),
        removals: MutableList<ApiVariant> = mutableListOf(),
        refreshes: MutableList<Int> = mutableListOf()
    ) = CoreInstalledStateController(
        settingsRepository = settings,
        resolveRepo = { repo },
        resolveBranch = { branch },
        removeLatestVersion = { removals += it },
        updateCoreInfo = { updates += it },
        readCoreInfo = { info },
        refreshCoreInfoAndAwait = { refreshes += 1 }
    )

    @Test
    fun `custom source changes are persisted with the normalized values`() {
        val settings = FakeSettings()
        controller(settings).persistResolvedSourceIfNeeded(
            ApiVariant.Custom,
            CoreSourceMetadata(repo = "Owner/New.git", branch = "refs/heads/release")
        )
        assertEquals(listOf("Owner/New" to "release"), settings.customWrites)
        assertTrue(settings.branchWrites.isEmpty())
    }

    @Test
    fun `standard branch changes are persisted but repository changes are ignored`() {
        val settings = FakeSettings()
        val controller = controller(settings)
        controller.persistResolvedSourceIfNeeded(
            ApiVariant.Stable,
            CoreSourceMetadata(repo = "owner/core", branch = "dev")
        )
        controller.persistResolvedSourceIfNeeded(
            ApiVariant.Stable,
            CoreSourceMetadata(repo = "other/core", branch = "ignored")
        )
        assertEquals(listOf(ApiVariant.Stable to "dev"), settings.branchWrites)
        assertTrue(settings.customWrites.isEmpty())
    }

    @Test
    fun `unchanged source and null metadata do not write settings`() {
        val settings = FakeSettings()
        val controller = controller(settings)
        controller.persistResolvedSourceIfNeeded(
            ApiVariant.Custom,
            CoreSourceMetadata(repo = "owner/core", branch = "main")
        )
        controller.persistResolvedSourceIfNeeded(ApiVariant.Custom, null)
        assertTrue(settings.customWrites.isEmpty())
        assertTrue(settings.branchWrites.isEmpty())
    }

    @Test
    fun `installed state clears only the selected variant and awaits refresh`() {
        val updates = mutableListOf<List<CoreInfo>>()
        val removals = mutableListOf<ApiVariant>()
        val refreshes = mutableListOf<Int>()
        val info = listOf(
            CoreInfo(
                variant = ApiVariant.Stable,
                version = "1.0.0",
                isInstalled = true,
                availableVersion = "1.1.0",
                hasVersionUpdate = true,
                remoteVersion = "1.1.0",
                remoteBranch = "main",
                updateRelation = CoreUpdateRelation.RemoteAhead,
                updatedPullRequestHeads = mapOf(1 to "abc"),
                updateCheckError = "old",
                updateCheckedAtEpochMillis = 99L
            ),
            CoreInfo(
                variant = ApiVariant.Dev,
                version = "2.0.0",
                isInstalled = true,
                availableVersion = "2.1.0",
                hasVersionUpdate = true
            )
        )

        kotlinx.coroutines.runBlocking {
            controller(settings = FakeSettings(), info = info, updates = updates, removals = removals, refreshes = refreshes)
                .refreshInstalledState(ApiVariant.Stable)
        }

        assertEquals(listOf(ApiVariant.Stable), removals)
        assertEquals(1, refreshes.size)
        val cleared = updates.single().first { it.variant == ApiVariant.Stable }
        assertNull(cleared.availableVersion)
        assertNull(cleared.remoteVersion)
        assertNull(cleared.remoteBranch)
        assertNull(cleared.updateCheckError)
        assertEquals(CoreUpdateRelation.Unknown, cleared.updateRelation)
        assertTrue(cleared.updatedPullRequestHeads.isEmpty())
        assertTrue(!cleared.hasVersionUpdate)
        assertTrue(updates.single().first { it.variant == ApiVariant.Dev }.hasVersionUpdate)
    }
}

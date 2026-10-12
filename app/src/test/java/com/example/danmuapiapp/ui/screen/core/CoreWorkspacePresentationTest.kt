package com.example.danmuapiapp.ui.screen.core

import com.example.danmuapiapp.domain.model.ApiVariant
import com.example.danmuapiapp.domain.model.CoreInfo
import com.example.danmuapiapp.domain.model.GithubAccountStatus
import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

class CoreWorkspacePresentationTest {
    private val utc = ZoneId.of("UTC")
    private val installed = CoreInfo(ApiVariant.Stable, "1.21.5", isInstalled = true)

    @Test fun `quota bar shows usage rather than remaining quota`() {
        assertEquals(0.001f, coreQuotaUsedRatio(GithubAccountStatus(coreLimit = 5000, coreRemaining = 4995)), 0.00001f)
        assertEquals(1f, coreQuotaUsedRatio(GithubAccountStatus(coreLimit = 60, coreRemaining = 0)), 0f)
        assertEquals(0f, coreQuotaUsedRatio(GithubAccountStatus(coreLimit = 60, coreRemaining = 60)), 0f)
    }

    @Test fun `missing or inconsistent quota values never make invalid progress`() {
        assertEquals(0f, coreQuotaUsedRatio(GithubAccountStatus()), 0f)
        assertEquals(0f, coreQuotaUsedRatio(GithubAccountStatus(coreLimit = 0, coreRemaining = 0)), 0f)
        assertEquals(0f, coreQuotaUsedRatio(GithubAccountStatus(coreLimit = 60, coreRemaining = 70)), 0f)
        assertEquals(1f, coreQuotaUsedRatio(GithubAccountStatus(coreLimit = 60, coreRemaining = -1)), 0f)
    }

    @Test fun `reset time rounds up and distinguishes authentication states`() {
        assertEquals("已认证 · 2 分钟后重置", coreQuotaResetHint(GithubAccountStatus(tokenValid = true, coreResetEpochSeconds = 1061), 1000))
        assertEquals("匿名 · 1 分钟后重置", coreQuotaResetHint(GithubAccountStatus(coreResetEpochSeconds = 1001), 1000))
        assertEquals("认证失败 · 即将重置", coreQuotaResetHint(GithubAccountStatus(tokenValid = false, coreResetEpochSeconds = 900), 1000))
        assertEquals("待验证", coreQuotaResetHint(GithubAccountStatus(tokenConfigured = true), 1000))
    }

    @Test fun `installed subtitle prefers actual installation time in device timezone`() {
        val info = installed.copy(
            sourceInstalledAtEpochMillis = Instant.parse("2026-10-11T12:16:00Z").toEpochMilli(),
            sourceCommitPublishedAt = "2026-10-01T01:00:00Z"
        )
        assertEquals("2026-10-11 20:16 · GitHub", coreInstalledSubtitle(info, ZoneId.of("Asia/Shanghai")))
    }

    @Test fun `old installs label commit date without pretending it is installation time`() {
        assertEquals("提交于 2026-10-01 01:00", coreInstalledSubtitle(installed.copy(sourceCommitPublishedAt = "2026-10-01T01:00:00Z"), utc))
        assertEquals("提交于 2026-10-01 01:00", coreInstalledSubtitle(installed.copy(sourceCommitPublishedAt = Instant.parse("2026-10-01T01:00:00Z").toEpochMilli().toString()), utc))
    }

    @Test fun `invalid old timestamps and absent source have readable fallback`() {
        assertEquals("本地核心", coreInstalledSubtitle(installed.copy(sourceCommitPublishedAt = "invalid"), utc))
        assertEquals("GitHub · 本地核心", coreInstalledSubtitle(installed.copy(sourceCommitSha = "abc1234"), utc))
        assertEquals("点下方按钮安装核心", coreInstalledSubtitle(installed.copy(isInstalled = false), utc))
    }
}

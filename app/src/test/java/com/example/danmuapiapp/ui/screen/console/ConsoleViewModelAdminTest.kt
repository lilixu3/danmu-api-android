package com.example.danmuapiapp.ui.screen.console

import com.example.danmuapiapp.domain.model.AdminSessionState
import com.example.danmuapiapp.domain.model.LogEntry
import com.example.danmuapiapp.domain.model.RuntimeState
import com.example.danmuapiapp.domain.repository.AdminSessionRepository
import com.example.danmuapiapp.domain.repository.RuntimeRepository
import com.example.danmuapiapp.domain.repository.SettingsRepository
import java.lang.reflect.Proxy
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConsoleViewModelAdminTest {
    @Test
    fun `reading logs stays available without admin mode`() {
        val fixture = Fixture()

        fixture.viewModel.refreshLogs()

        assertEquals(1, fixture.refreshCalls)
        assertEquals(0, fixture.clearCalls)
        assertFalse(fixture.viewModel.showAdminRequiredDialog.value)
    }

    @Test
    fun `clearing without an admin password shows setup prompt without deleting logs`() {
        val fixture = Fixture()

        fixture.viewModel.clearLogs()

        assertTrue(fixture.viewModel.showAdminRequiredDialog.value)
        assertEquals(0, fixture.clearCalls)
    }

    @Test
    fun `a configured password alone does not authorize clearing logs`() {
        val fixture = Fixture(AdminSessionState(hasAdminTokenConfigured = true))

        fixture.viewModel.clearLogs()

        assertTrue(fixture.viewModel.showAdminRequiredDialog.value)
        assertEquals(0, fixture.clearCalls)
    }

    @Test
    fun `cancelling the admin prompt leaves logs untouched`() {
        val fixture = Fixture()
        fixture.viewModel.clearLogs()

        fixture.viewModel.dismissAdminRequiredDialog()

        assertFalse(fixture.viewModel.showAdminRequiredDialog.value)
        assertEquals(0, fixture.clearCalls)
    }

    @Test
    fun `an active admin session can clear logs`() {
        val fixture = Fixture(AdminSessionState(isAdminMode = true, hasAdminTokenConfigured = true))

        fixture.viewModel.clearLogs()

        assertFalse(fixture.viewModel.showAdminRequiredDialog.value)
        assertEquals(1, fixture.clearCalls)
    }

    @Test
    fun `admin login permits retry and logout requires authorization again`() {
        val fixture = Fixture(AdminSessionState(hasAdminTokenConfigured = true))
        fixture.viewModel.clearLogs()
        fixture.adminState.value = fixture.adminState.value.copy(isAdminMode = true)

        fixture.viewModel.clearLogs()

        assertFalse(fixture.viewModel.showAdminRequiredDialog.value)
        assertEquals(1, fixture.clearCalls)

        fixture.adminState.value = fixture.adminState.value.copy(isAdminMode = false)
        fixture.viewModel.clearLogs()

        assertTrue(fixture.viewModel.showAdminRequiredDialog.value)
        assertEquals(1, fixture.clearCalls)
    }

    private class Fixture(initialAdminState: AdminSessionState = AdminSessionState()) {
        val adminState = MutableStateFlow(initialAdminState)
        var clearCalls = 0
            private set
        var refreshCalls = 0
            private set

        private val runtimeRepository = repository<RuntimeRepository> { method ->
            when (method) {
                "getRuntimeState" -> MutableStateFlow(RuntimeState())
                "getLogs" -> MutableStateFlow<List<LogEntry>>(emptyList())
                "clearLogs" -> {
                    clearCalls += 1
                    Unit
                }
                "refreshLogs" -> {
                    refreshCalls += 1
                    Unit
                }
                else -> error("Unexpected runtime call: $method")
            }
        }
        private val settingsRepository = repository<SettingsRepository> { method ->
            when (method) {
                "getLogEnabled", "getLogPreviewEnabled" -> MutableStateFlow(true)
                "getLogMaxCount" -> MutableStateFlow(1000)
                else -> error("Unexpected settings call: $method")
            }
        }
        private val adminSessionRepository = repository<AdminSessionRepository> { method ->
            when (method) {
                "getSessionState" -> adminState
                else -> error("Unexpected admin call: $method")
            }
        }

        val viewModel = ConsoleViewModel(runtimeRepository, settingsRepository, adminSessionRepository)
    }

    companion object {
        private inline fun <reified Repository : Any> repository(noinline invoke: (String) -> Any): Repository {
            return Proxy.newProxyInstance(
                Repository::class.java.classLoader,
                arrayOf(Repository::class.java)
            ) { _, method, _ -> invoke(method.name) } as Repository
        }
    }
}

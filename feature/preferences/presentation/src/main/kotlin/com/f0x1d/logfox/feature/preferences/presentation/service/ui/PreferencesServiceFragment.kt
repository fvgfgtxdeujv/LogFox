package com.f0x1d.logfox.feature.preferences.presentation.service.ui

import android.app.ActivityManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.EditText
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.preference.Preference
import androidx.preference.SwitchPreferenceCompat
import com.f0x1d.logfox.core.context.isHorizontalOrientation
import com.f0x1d.logfox.core.context.toast
import com.f0x1d.logfox.core.tea.BaseStorePreferenceFragment
import com.f0x1d.logfox.core.ui.icons.Icons
import com.f0x1d.logfox.core.ui.preference.setupAsListPreference
import com.f0x1d.logfox.core.ui.view.setupBackButtonForNavController
import com.f0x1d.logfox.feature.preferences.presentation.R
import com.f0x1d.logfox.feature.preferences.presentation.service.PreferencesServiceCommand
import com.f0x1d.logfox.feature.preferences.presentation.service.PreferencesServiceSideEffect
import com.f0x1d.logfox.feature.preferences.presentation.service.PreferencesServiceState
import com.f0x1d.logfox.feature.preferences.api.data.ServiceSettingsRepository
import com.f0x1d.logfox.feature.preferences.presentation.service.PreferencesServiceViewModel
import com.f0x1d.logfox.feature.preferences.presentation.service.PreferencesServiceViewState
import com.f0x1d.logfox.feature.strings.Strings
import com.f0x1d.logfox.feature.terminals.api.base.TerminalType
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import dev.chrisbanes.insetter.applyInsetter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@AndroidEntryPoint
internal class PreferencesServiceFragment :
    BaseStorePreferenceFragment<
        PreferencesServiceViewState,
        PreferencesServiceState,
        PreferencesServiceCommand,
        PreferencesServiceSideEffect,
        PreferencesServiceViewModel,
        >() {

    override val viewModel by viewModels<PreferencesServiceViewModel>()

    @Inject
    lateinit var serviceSettingsRepository: ServiceSettingsRepository

    private var detailExpanded = false

    private val mcpSubPreferences by lazy {
        listOf(
            "pref_mcp_server_status",
            "pref_mcp_server_port",
            "pref_mcp_server_host",
            "pref_mcp_server_start",
            "pref_mcp_server_stop",
            "pref_mcp_server_detail_port",
            "pref_mcp_server_detail_host",
        )
    }

    private val detailPreferenceKeys by lazy {
        listOf(
            "pref_mcp_server_detail_port",
            "pref_mcp_server_detail_host",
        )
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        addPreferencesFromResource(R.xml.settings_service)

        val mcpEnabled = serviceSettingsRepository.mcpServerEnabled().value

        findPreference<SwitchPreferenceCompat>("pref_start_on_boot")?.apply {
            setOnPreferenceChangeListener { _, newValue ->
                send(PreferencesServiceCommand.StartOnBootChanged(newValue as Boolean))
                true
            }
        }

        findPreference<SwitchPreferenceCompat>("pref_show_logs_from_app_launch")?.apply {
            setOnPreferenceChangeListener { _, newValue ->
                send(PreferencesServiceCommand.ShowLogsFromAppLaunchChanged(newValue as Boolean))
                true
            }
        }

        findPreference<Preference>("pref_mcp_server_port")?.setOnPreferenceClickListener {
            showPortDialog()
            true
        }

        findPreference<Preference>("pref_mcp_server_host")?.setOnPreferenceClickListener {
            showHostDialog()
            true
        }

        findPreference<Preference>("pref_mcp_server_status")?.setOnPreferenceClickListener {
            detailExpanded = !detailExpanded
            toggleDetailPreferences(detailExpanded)
            true
        }

        findPreference<SwitchPreferenceCompat>("pref_mcp_server_enabled")?.apply {
            isChecked = mcpEnabled

            setOnPreferenceChangeListener { _, newValue ->
                if (newValue as Boolean) {
                    serviceSettingsRepository.mcpServerEnabled().set(true)
                    startMcpServer()
                    updateMcpUiVisibility(true)
                    true
                } else {
                    showMcpDisableConfirmDialog()
                    false
                }
            }
        }

        updateMcpUiVisibility(mcpEnabled)

        if (mcpEnabled) {
            lifecycleScope.launch {
                val manager = requireContext().getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
                val isRunning = manager?.runningAppProcesses?.any {
                    it.processName.contains(":mcp")
                } == true
                if (isRunning) {
                    val port = serviceSettingsRepository.mcpServerPort().value
                    val healthy = performHealthCheck(port)
                    updateStatusDisplay(healthy, port)
                }
            }
        }
    }

    private companion object {
        private const val MCP_SERVICE_CLASS = "com.f0x1d.logfox.mcp.impl.McpServerService"
        private const val MCP_ACTION_STOP = "mcp.STOP_SERVER"
        private const val DEFAULT_PORT = 8765
        private const val DEFAULT_HOST = "0.0.0.0"
        const val EXTRA_PORT = "mcp.port"
        const val EXTRA_HOST = "mcp.host"
        private const val HEALTH_CHECK_TIMEOUT_MS = 2000
    }

    private fun startMcpServer() {
        findPreference<Preference>("pref_mcp_server_status")?.summary =
            getString(Strings.mcp_server_status_checking)

        val port = serviceSettingsRepository.mcpServerPort().value
        val host = serviceSettingsRepository.mcpServerHost().value
        val intent = Intent().apply {
            component = ComponentName(requireContext().packageName, MCP_SERVICE_CLASS)
            putExtra(EXTRA_PORT, port)
            putExtra(EXTRA_HOST, host)
        }
        requireContext().startForegroundService(intent)

        lifecycleScope.launch {
            delay(500)
            val healthy = performHealthCheck(port)
            updateStatusDisplay(healthy, port)
        }
    }

    private fun stopMcpServer() {
        val intent = Intent().apply {
            component = ComponentName(requireContext().packageName, MCP_SERVICE_CLASS)
            action = MCP_ACTION_STOP
        }
        requireContext().startForegroundService(intent)
    }

    private fun updateMcpUiVisibility(visible: Boolean) {
        mcpSubPreferences.forEach { key ->
            findPreference<Preference>(key)?.isVisible = visible
        }
        if (!visible) {
            detailExpanded = false
            toggleDetailPreferences(false)
            findPreference<Preference>("pref_mcp_server_status")?.summary = ""
        }
    }

    private fun toggleDetailPreferences(expanded: Boolean) {
        detailPreferenceKeys.forEach { key ->
            findPreference<Preference>(key)?.isVisible = expanded
        }
    }

    private fun updateStatusDisplay(healthy: Boolean, port: Int) {
        if (healthy) {
            findPreference<Preference>("pref_mcp_server_status")?.summary =
                getString(Strings.mcp_server_status_running, port)
            findPreference<Preference>("pref_mcp_server_detail_port")?.summary = port.toString()
            findPreference<Preference>("pref_mcp_server_detail_host")?.summary =
                serviceSettingsRepository.mcpServerHost().value
        } else {
            findPreference<Preference>("pref_mcp_server_status")?.summary =
                getString(Strings.mcp_server_status_failed)
            findPreference<Preference>("pref_mcp_server_detail_port")?.summary = ""
            findPreference<Preference>("pref_mcp_server_detail_host")?.summary = ""
        }
    }

    private suspend fun performHealthCheck(port: Int) = withContext(Dispatchers.IO) {
        try {
            val url = java.net.URL("http://127.0.0.1:$port/health")
            val connection = url.openConnection() as java.net.HttpURLConnection
            connection.connectTimeout = HEALTH_CHECK_TIMEOUT_MS
            connection.readTimeout = HEALTH_CHECK_TIMEOUT_MS
            connection.requestMethod = "GET"
            val code = connection.responseCode
            connection.disconnect()
            code == 200
        } catch (_: Exception) {
            false
        }
    }

    private fun showMcpDisableConfirmDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(Strings.mcp_server_disable_confirm_title)
            .setMessage(Strings.mcp_server_disable_confirm_message)
            .setPositiveButton(Strings.yes) { _, _ ->
                serviceSettingsRepository.mcpServerEnabled().set(false)
                stopMcpServer()
                updateMcpUiVisibility(false)
                findPreference<SwitchPreferenceCompat>("pref_mcp_server_enabled")?.isChecked = false
            }
            .setNegativeButton(Strings.no, null)
            .show()
    }

    private fun showPortDialog() {
        val editText = EditText(requireContext())
        editText.setText(serviceSettingsRepository.mcpServerPort().value.toString())

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(Strings.mcp_server_port)
            .setView(editText)
            .setPositiveButton(Strings.save) { _, _ ->
                val port = editText.text.toString().toIntOrNull() ?: DEFAULT_PORT
                serviceSettingsRepository.mcpServerPort().set(port)
                requireContext().toast("${Strings.mcp_server_port}: $port")
                stopMcpServer()
                startMcpServer()
            }
            .setNegativeButton(Strings.close, null)
            .show()
    }

    private fun showHostDialog() {
        val hosts = arrayOf(
            getString(Strings.mcp_server_host_local),
            getString(Strings.mcp_server_host_all)
        )
        val hostValues = arrayOf("127.0.0.1", "0.0.0.0")
        val currentHost = serviceSettingsRepository.mcpServerHost().value
        val selectedIndex = hostValues.indexOf(currentHost).coerceAtLeast(1)

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(Strings.mcp_server_host)
            .setSingleChoiceItems(hosts, selectedIndex) { dialog, which ->
                val host = hostValues[which]
                serviceSettingsRepository.mcpServerHost().set(host)
                requireContext().toast("${Strings.mcp_server_host}: $host")
                dialog.dismiss()
                stopMcpServer()
                startMcpServer()
            }
            .setNegativeButton(Strings.close, null)
            .show()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        view.findViewById<MaterialToolbar>(R.id.toolbar).apply {
            setTitle(Strings.service)
            setupBackButtonForNavController()
        }

        listView.apply {
            clipToPadding = false
            applyInsetter {
                type(navigationBars = true) {
                    padding(vertical = requireContext().isHorizontalOrientation)
                }
            }
        }
    }

    override fun render(state: PreferencesServiceViewState) {
        if (state.terminalNames.isEmpty()) return

        findPreference<Preference>("pref_selected_terminal_index")?.apply {
            val terminalNamesArray = state.terminalNames.toTypedArray()
            val selectedIndex = TerminalType.entries.indexOf(state.selectedTerminalType)

            setupAsListPreference(
                setupDialog = { setIcon(Icons.ic_dialog_terminal) },
                items = terminalNamesArray,
                selected = { selectedIndex },
                onSelected = { index ->
                    val type = TerminalType.entries[index]
                    send(PreferencesServiceCommand.TerminalSelected(type))
                },
            )

            summary = terminalNamesArray.getOrNull(selectedIndex) ?: ""
        }
    }

    override fun handleSideEffect(sideEffect: PreferencesServiceSideEffect) {
        when (sideEffect) {
            is PreferencesServiceSideEffect.ShowTerminalRestartDialog -> {
                MaterialAlertDialogBuilder(requireContext())
                    .setIcon(Icons.ic_dialog_terminal)
                    .setTitle(Strings.new_terminal_selected)
                    .setMessage(Strings.new_terminal_selected_question)
                    .setPositiveButton(Strings.yes) { _, _ ->
                        send(PreferencesServiceCommand.ConfirmRestartLogging)
                    }
                    .setNeutralButton(Strings.no, null)
                    .show()
            }

            is PreferencesServiceSideEffect.ShowTerminalUnavailableToast -> {
                requireContext().toast(Strings.terminal_unavailable)
            }

            is PreferencesServiceSideEffect.ShowAndroid13WarningDialog -> {
                MaterialAlertDialogBuilder(requireContext())
                    .setIcon(Icons.ic_dialog_warning)
                    .setTitle(Strings.warning)
                    .setMessage(Strings.android13_start_on_boot_warning)
                    .setCancelable(false)
                    .setPositiveButton(android.R.string.ok, null)
                    .show()
            }

            else -> Unit
        }
    }
}
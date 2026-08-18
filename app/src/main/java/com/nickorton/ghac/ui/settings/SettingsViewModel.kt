package com.nickorton.ghac.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nickorton.ghac.data.settings.ServerSettings
import com.nickorton.ghac.data.settings.SettingsStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Backs the Settings screen, which replaces the TUI's `.ghacrc`.
 *
 * Ports are held as strings while editing so a half-typed value does not get
 * coerced to a number and fight the user's keystrokes; they are parsed and
 * validated only on save.
 */
class SettingsViewModel(private val store: SettingsStore) : ViewModel() {

    data class FormState(
        val mpdHost: String = "",
        val mpdPort: String = "",
        val snapHost: String = "",
        val snapPort: String = "",
        val error: String? = null,
        val saved: Boolean = false,
        val loaded: Boolean = false,
    )

    private val _form = MutableStateFlow(FormState())
    val form: StateFlow<FormState> = _form.asStateFlow()

    init {
        viewModelScope.launch {
            val saved = store.settings.first()
            _form.value = FormState(
                mpdHost = saved.mpdHost,
                mpdPort = saved.mpdPort.toString(),
                snapHost = saved.snapHost,
                snapPort = saved.snapPort.toString(),
                loaded = true,
            )
        }
    }

    fun onMpdHostChanged(value: String) = edit { it.copy(mpdHost = value) }
    fun onMpdPortChanged(value: String) = edit { it.copy(mpdPort = value.filter(Char::isDigit)) }
    fun onSnapHostChanged(value: String) = edit { it.copy(snapHost = value) }
    fun onSnapPortChanged(value: String) = edit { it.copy(snapPort = value.filter(Char::isDigit)) }

    private fun edit(transform: (FormState) -> FormState) =
        _form.update { transform(it).copy(error = null, saved = false) }

    fun save() {
        val current = _form.value
        val settings = ServerSettings(
            mpdHost = current.mpdHost.trim(),
            mpdPort = current.mpdPort.toIntOrNull() ?: 0,
            snapHost = current.snapHost.trim(),
            snapPort = current.snapPort.toIntOrNull() ?: 0,
        )

        val error = settings.validationError()
        if (error != null) {
            _form.update { it.copy(error = error, saved = false) }
            return
        }

        viewModelScope.launch {
            store.save(settings)
            // Saving is what triggers reconnection: the container watches the
            // settings flow and redials both backends when it changes.
            _form.update { it.copy(error = null, saved = true) }
        }
    }
}

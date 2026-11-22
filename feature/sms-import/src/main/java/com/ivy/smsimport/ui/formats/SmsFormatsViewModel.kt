package com.ivy.smsimport.ui.formats

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewModelScope
import com.ivy.sms.SmsTemplate
import com.ivy.sms.SmsTemplateCompiler
import com.ivy.sms.TemplateCompileResult
import com.ivy.smsimport.data.SmsTemplateRepository
import com.ivy.smsimport.domain.ScanMessagesUseCase
import com.ivy.smsimport.domain.UnrecognisedMessagesUseCase
import com.ivy.ui.ComposeViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

@Stable
@HiltViewModel
class SmsFormatsViewModel @Inject constructor(
    private val templates: SmsTemplateRepository,
    private val unrecognised: UnrecognisedMessagesUseCase,
    private val scanMessages: ScanMessagesUseCase,
) : ComposeViewModel<SmsFormatsState, SmsFormatsEvent>() {

    private var rows by mutableStateOf<ImmutableList<FormatRow>>(persistentListOf())
    private var unreadable by mutableIntStateOf(0)
    private var counting by mutableStateOf(true)

    init {
        observeTemplates()
        countUnrecognised()
    }

    @Composable
    override fun uiState(): SmsFormatsState = SmsFormatsState(
        formats = rows,
        unrecognisedCount = unreadable,
        counting = counting,
    )

    override fun onEvent(event: SmsFormatsEvent) {
        when (event) {
            is SmsFormatsEvent.SetEnabled -> setEnabled(event.id, event.enabled)
            is SmsFormatsEvent.Delete -> delete(event.id)
            SmsFormatsEvent.Refresh -> countUnrecognised()
        }
    }

    private fun observeTemplates() {
        viewModelScope.launch {
            templates.templates.collectLatest { list ->
                rows = list.map { it.toRow() }.toImmutableList()
            }
        }
    }

    private fun countUnrecognised() {
        viewModelScope.launch {
            counting = true
            unreadable = runCatching { unrecognised.find().size }.getOrDefault(0)
            counting = false
        }
    }

    private fun setEnabled(id: String, enabled: Boolean) {
        viewModelScope.launch {
            templates.setEnabled(id, enabled)
            // Switching a format on has to look at the messages it was written
            // for, which are all behind the scan watermark by now.
            if (enabled) scanMessages.scan(rescan = true)
            countUnrecognised()
        }
    }

    private fun delete(id: String) {
        viewModelScope.launch {
            templates.delete(id)
            countUnrecognised()
        }
    }

    private fun SmsTemplate.toRow(): FormatRow {
        val problem = when (val compiled = SmsTemplateCompiler.compile(this)) {
            is TemplateCompileResult.Error -> compiled.message
            is TemplateCompileResult.Ok -> null
        }
        return FormatRow(
            id = id,
            label = label,
            sender = senderFragment,
            direction = direction,
            enabled = enabled,
            problem = problem,
            preview = template.replace(Regex("""\s+"""), " ").take(PREVIEW_LENGTH),
        )
    }

    companion object {
        private const val PREVIEW_LENGTH = 90
    }
}

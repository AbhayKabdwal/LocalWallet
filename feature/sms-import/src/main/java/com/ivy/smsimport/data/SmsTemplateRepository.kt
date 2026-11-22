package com.ivy.smsimport.data

import com.ivy.sms.SmsRule
import com.ivy.sms.SmsTemplate
import com.ivy.sms.SmsTemplateCompiler
import com.ivy.sms.TemplateCompileResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The formats the user taught, and the rules they compile to.
 *
 * Compilation happens on the way out rather than at save time so that a
 * template which stops working after a bank reword can still be opened and
 * edited - storing only the compiled rule would leave the user with a format
 * they can delete but not fix.
 */
@Singleton
class SmsTemplateRepository @Inject constructor(
    private val dao: SmsImportDao,
) {
    val templates: Flow<List<SmsTemplate>> =
        dao.templates().map { rows -> rows.map { it.toDomain() } }

    suspend fun find(id: String): SmsTemplate? = dao.template(id)?.toDomain()

    /** Rules for the parser: only enabled templates, only ones that compile. */
    suspend fun activeRules(): List<SmsRule> = dao.enabledTemplates()
        .map { it.toDomain() }
        .mapNotNull { template ->
            when (val compiled = SmsTemplateCompiler.compile(template)) {
                is TemplateCompileResult.Ok -> compiled.rule
                is TemplateCompileResult.Error -> null
            }
        }

    suspend fun save(template: SmsTemplate): SmsTemplate {
        val stored = template.copy(
            id = template.id.ifBlank { UUID.randomUUID().toString() },
            createdAtMillis = template.createdAtMillis
                .takeIf { it > 0L }
                ?: System.currentTimeMillis(),
        )
        dao.saveTemplate(stored.toEntity())
        return stored
    }

    suspend fun delete(id: String) = dao.deleteTemplate(id)

    suspend fun setEnabled(id: String, enabled: Boolean) = dao.setTemplateEnabled(id, enabled)

    private fun SmsTemplate.toEntity() = SmsTemplateEntity(
        id = id,
        label = label,
        senderFragment = senderFragment,
        template = template,
        direction = direction,
        currency = currency,
        enabled = enabled,
        createdAtMillis = createdAtMillis,
    )
}

private fun SmsTemplateEntity.toDomain() = SmsTemplate(
    id = id,
    label = label,
    senderFragment = senderFragment,
    template = template,
    direction = direction,
    currency = currency,
    enabled = enabled,
    createdAtMillis = createdAtMillis,
)

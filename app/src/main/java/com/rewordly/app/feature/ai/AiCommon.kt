package com.rewordly.app.feature.ai

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.rewordly.app.R
import com.rewordly.app.core.common.AppError
import com.rewordly.app.core.ui.messageRes
import com.rewordly.app.core.ui.theme.Dimens
import com.rewordly.app.domain.model.GenerationMode
import com.rewordly.app.domain.model.UsageInfo

@get:StringRes
val GenerationMode.titleRes: Int
    get() = when (this) {
        GenerationMode.TOPIC -> R.string.ai_title_topic
        GenerationMode.TEXT -> R.string.ai_title_text
        GenerationMode.WORD -> R.string.ai_title_word
    }

@get:StringRes
val GenerationMode.shortRes: Int
    get() = when (this) {
        GenerationMode.TOPIC -> R.string.ai_mode_topic
        GenerationMode.TEXT -> R.string.ai_mode_text
        GenerationMode.WORD -> R.string.ai_mode_word
    }

/** User facing text for a failed generation; rate limits include the wait time when the backend gave one. */
@Composable
fun generationErrorMessage(error: AppError): String {
    if (error is AppError.RateLimited && error.retryAfterSeconds != null && error.retryAfterSeconds > 0) {
        val seconds = error.retryAfterSeconds
        return if (seconds >= SECONDS_PER_MINUTE) {
            val minutes = (seconds + SECONDS_PER_MINUTE - 1) / SECONDS_PER_MINUTE
            pluralStringResource(R.plurals.ai_rate_limit_retry_minutes, minutes, minutes)
        } else {
            pluralStringResource(R.plurals.ai_rate_limit_retry, seconds, seconds)
        }
    }
    return when (error) {
        // Naming the provider and repeating its own message is what makes a bad key or model fixable.
        is AppError.InvalidApiKey -> stringResource(R.string.error_invalid_api_key, error.provider)
        is AppError.ProviderRejected -> if (error.detail.isBlank()) {
            stringResource(R.string.error_provider_rejected_bare, error.provider, error.status)
        } else {
            stringResource(R.string.error_provider_rejected, error.provider, error.status, error.detail)
        }
        else -> stringResource(error.messageRes)
    }
}

/** Shows only the numbers the backend actually reported; nothing is inferred or invented. */
@Composable
fun UsageSummary(usage: UsageInfo?, modifier: Modifier = Modifier) {
    if (usage == null || usage.isEmpty) return
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(Dimens.spaceXxs)) {
        usage.remainingRequests?.let { UsageLine(stringResource(R.string.ai_usage_remaining, it)) }
        if (usage.dailyUsed != null && usage.dailyLimit != null) {
            UsageLine(stringResource(R.string.ai_usage_daily, usage.dailyUsed, usage.dailyLimit))
        }
        if (usage.monthlyUsed != null && usage.monthlyLimit != null) {
            UsageLine(stringResource(R.string.ai_usage_monthly, usage.monthlyUsed, usage.monthlyLimit))
        }
    }
}

@Composable
private fun UsageLine(text: String) {
    Text(text = text, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

private const val SECONDS_PER_MINUTE = 60

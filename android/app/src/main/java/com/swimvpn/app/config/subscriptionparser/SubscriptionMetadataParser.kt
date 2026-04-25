package com.swimvpn.app.config.subscriptionparser

import java.net.URI
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToLong

private data class TrafficMetadataSnapshot(
    val usedBytes: Long? = null,
    val totalBytes: Long? = null,
    val warnings: List<String> = emptyList(),
)

internal data class SubscriptionMetadataEnvelope(
    val providerName: String? = null,
    val trafficUsedBytes: Long? = null,
    val trafficTotalBytes: Long? = null,
    val expiresAt: String? = null,
    val autoUpdateIntervalHours: Int? = null,
    val warnings: List<String> = emptyList(),
)

internal object SubscriptionMetadataParser {
    private val russianMonthNumbers = mapOf(
        "января" to 1,
        "февраля" to 2,
        "марта" to 3,
        "апреля" to 4,
        "мая" to 5,
        "июня" to 6,
        "июля" to 7,
        "августа" to 8,
        "сентября" to 9,
        "октября" to 10,
        "ноября" to 11,
        "декабря" to 12,
    )

    private val trafficRegex = Regex(
        """(?iu)(\d+(?:[.,]\d+)?)\s*((?:[KMGT]B|[КМГТ]Б))\s*/\s*(∞|infinity|unlimited|illimited|безлимит(?:ный)?|неогранич(?:енно|енный)?|(\d+(?:[.,]\d+)?)\s*((?:[KMGT]B|[КМГТ]Б)))"""
    )
    private val expiryRegex = Regex(
        """(?iu)(?:expires?|expiry|exp|истекает|действует\s+до|до)\s*[:\-]?\s*(\d{2}\.\d{2}\.\d{4})"""
    )
    private val russianTextDateRegex = Regex(
        """(?iu)(?:истекает|действует\s+до|до)\s*[:\-]?\s*(\d{1,2})\s+([а-яё]+)\s+(\d{4})\s*(?:года|г\.?)?"""
    )
    private val bareDateRegex = Regex("""\b(\d{2}\.\d{2}\.\d{4})\b""")
    private val autoUpdateRegex = Regex(
        """(?iu)(?:autoupdate|auto[-\s]?update|автообновление)\s*[-: ]*\s*(\d+)\s*(?:h|hr|hrs|hour|hours|ч|час|часа|часов)\.?"""
    )

    fun parse(payload: String, sourceUrl: String? = null): SubscriptionMetadataEnvelope {
        val normalized = payload.replace("\r", "\n")
        val traffic = parseTraffic(normalized)
        val expiresAt = parseExpiry(normalized)
        val providerName = detectProviderName(normalized, sourceUrl)
        val autoUpdateHours = parseAutoUpdateHours(normalized)
        val warnings = buildList {
            addAll(traffic.warnings)
        }

        return SubscriptionMetadataEnvelope(
            providerName = providerName,
            trafficUsedBytes = traffic.usedBytes,
            trafficTotalBytes = traffic.totalBytes,
            expiresAt = expiresAt,
            autoUpdateIntervalHours = autoUpdateHours,
            warnings = warnings,
        )
    }

    fun extractCountryEmoji(text: String?): String? {
        if (text.isNullOrBlank()) return null
        val codePoints = text.codePoints().toArray()
        for (index in 0 until codePoints.size - 1) {
            val first = codePoints[index]
            val second = codePoints[index + 1]
            if (first in 0x1F1E6..0x1F1FF && second in 0x1F1E6..0x1F1FF) {
                return String(Character.toChars(first)) + String(Character.toChars(second))
            }
        }
        return null
    }

    private fun parseTraffic(text: String): TrafficMetadataSnapshot {
        val match = trafficRegex.find(text)
        val warnings = mutableListOf<String>()

        val usedBytes = match?.let {
            toBytes(it.groupValues[1], it.groupValues[2])
        }

        val totalToken = match?.groupValues?.getOrNull(3)?.trim().orEmpty()
        val explicitTotalValue = match?.groupValues?.getOrNull(4)?.trim().orEmpty()
        val explicitTotalUnit = match?.groupValues?.getOrNull(5)?.trim().orEmpty()

        val totalBytes = when {
            explicitTotalValue.isNotBlank() && explicitTotalUnit.isNotBlank() -> toBytes(explicitTotalValue, explicitTotalUnit)
            totalToken.equals("∞", ignoreCase = true) -> null
            totalToken.contains("infinity", ignoreCase = true) -> null
            totalToken.contains("unlimited", ignoreCase = true) -> null
            totalToken.contains("безлимит", ignoreCase = true) -> null
            totalToken.contains("неогранич", ignoreCase = true) -> null
            else -> null
        }

        if (text.contains("Закончился трафик", ignoreCase = true)) {
            warnings += "Закончился трафик"
        }

        if (text.contains("Сбросить", ignoreCase = true)) {
            warnings += "Сбросить"
        }

        return TrafficMetadataSnapshot(
            usedBytes = usedBytes,
            totalBytes = totalBytes,
            warnings = warnings,
        )
    }

    private fun parseExpiry(text: String): String? {
        val numericDate = expiryRegex.find(text)?.groupValues?.getOrNull(1)
            ?: bareDateRegex.find(text)?.groupValues?.getOrNull(1)

        if (!numericDate.isNullOrBlank()) {
            return runCatching {
                LocalDate.parse(numericDate, DateTimeFormatter.ofPattern("dd.MM.yyyy"))
                    .atStartOfDay()
                    .toInstant(ZoneOffset.UTC)
                    .toString()
            }.getOrNull()
        }

        val russianTextDate = russianTextDateRegex.find(text) ?: return null
        val day = russianTextDate.groupValues.getOrNull(1)?.toIntOrNull() ?: return null
        val monthName = russianTextDate.groupValues.getOrNull(2)?.lowercase(Locale.ROOT) ?: return null
        val month = russianMonthNumbers[monthName] ?: return null
        val year = russianTextDate.groupValues.getOrNull(3)?.toIntOrNull() ?: return null

        return runCatching {
            LocalDate.of(year, month, day)
                .atStartOfDay()
                .toInstant(ZoneOffset.UTC)
                .toString()
        }.getOrNull()
    }

    private fun parseAutoUpdateHours(text: String): Int? {
        return autoUpdateRegex.find(text)
            ?.groupValues
            ?.getOrNull(1)
            ?.toIntOrNull()
    }

    private fun detectProviderName(text: String, sourceUrl: String?): String? {
        val candidateLine = text
            .lines()
            .map { it.trim() }
            .firstOrNull { line ->
                line.isNotBlank() &&
                    !line.startsWith("http://", ignoreCase = true) &&
                    !line.startsWith("https://", ignoreCase = true) &&
                    !line.startsWith("vless://", ignoreCase = true) &&
                    !line.startsWith("vmess://", ignoreCase = true) &&
                    !line.startsWith("trojan://", ignoreCase = true) &&
                    !line.startsWith("ss://", ignoreCase = true) &&
                    !trafficRegex.containsMatchIn(line) &&
                    !expiryRegex.containsMatchIn(line) &&
                    !russianTextDateRegex.containsMatchIn(line) &&
                    !autoUpdateRegex.containsMatchIn(line) &&
                    !line.contains("ваша подписка", ignoreCase = true) &&
                    !line.contains("осталось", ignoreCase = true) &&
                    !line.contains("тариф", ignoreCase = true) &&
                    !line.contains("трафик", ignoreCase = true) &&
                    !line.contains("израсходовано", ignoreCase = true) &&
                    !line.contains("лимит устройств", ignoreCase = true) &&
                    !line.contains("подключили", ignoreCase = true) &&
                    !line.equals("Закончился трафик", ignoreCase = true) &&
                    !line.equals("Сбросить", ignoreCase = true) &&
                    !line.startsWith("{") &&
                    !line.startsWith("[")
            }
            ?.removeSurrounding("\"")
            ?.trim()

        if (!candidateLine.isNullOrBlank()) {
            val flag = extractCountryEmoji(candidateLine)
            return candidateLine.removePrefix(flag ?: "").trim().ifBlank { candidateLine.trim() }
        }

        val host = sourceUrl?.let {
            runCatching { URI(it).host }.getOrNull()
        }?.removePrefix("www.")?.trim()

        return host?.takeIf { it.isNotBlank() }
    }

    private fun toBytes(value: String, unit: String): Long {
        val normalizedValue = value.replace(',', '.').toDouble()
        val normalizedUnit = unit.uppercase(Locale.ROOT)
            .replace('К', 'K')
            .replace('М', 'M')
            .replace('Г', 'G')
            .replace('Т', 'T')
            .replace('Б', 'B')
        val multiplier = when (normalizedUnit) {
            "KB" -> 1024.0
            "MB" -> 1024.0 * 1024.0
            "GB" -> 1024.0 * 1024.0 * 1024.0
            "TB" -> 1024.0 * 1024.0 * 1024.0 * 1024.0
            else -> 1.0
        }
        return (normalizedValue * multiplier).roundToLong()
    }
}

package codeshield.cache

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

actual fun currentTimeMs(): Long = System.currentTimeMillis()

actual fun formatTs(epochMs: Long): String = runCatching {
    val fmt = DateTimeFormatter.ofPattern("dd/MM HH:mm").withZone(ZoneId.systemDefault())
    fmt.format(Instant.ofEpochMilli(epochMs))
}.getOrDefault("#$epochMs")

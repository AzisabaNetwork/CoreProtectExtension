package net.azisaba.coreprotectextension.config

import com.charleskorn.kaml.Yaml
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import java.io.File
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.util.TimeZone

@Serializable
data class PluginConfig(
    val timeZone: String = TimeZone.getDefault().id,
) {
    fun getZoneId(): ZoneId = TimeZone.getTimeZone(timeZone).toZoneId()
    fun getCurrentZoneOffset(): ZoneOffset = ZonedDateTime.now(getZoneId()).offset
    fun getZoneOffsetAt(dateTime: LocalDateTime): ZoneOffset = ZonedDateTime.of(dateTime, getZoneId()).offset

    companion object {
        lateinit var instance: PluginConfig

        fun load() {
            val file = File("plugins/CoreProtectExtension/config.yml")
            if (!file.exists() || file.readText().isBlank()) {
                if (!file.parentFile.exists()) file.parentFile.mkdirs()
                file.writeText(Yaml.default.encodeToString(PluginConfig()))
            }
            instance = Yaml.default.decodeFromString(file.readText())
        }
    }
}

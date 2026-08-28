package io.github.Earth1283.aptMc.commands.sub

import com.google.gson.JsonObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ListCommandTest {

    @Test
    fun pluginDisplayName_usesModrinthProjectTitleInsteadOfProjectId() {
        val version = JsonObject().apply {
            addProperty("project_id", "9eGKb6K1")
        }

        val displayName = pluginDisplayName(
            "ViaVersion-5.4.2.jar",
            version,
            mapOf("9eGKb6K1" to "ViaVersion")
        )

        assertEquals("ViaVersion", displayName)
    }

    @Test
    fun pluginDisplayName_fallsBackToFilenameWhenProjectNameIsUnavailable() {
        val version = JsonObject().apply {
            addProperty("project_id", "9eGKb6K1")
        }

        assertEquals("ViaVersion-5.4.2.jar", pluginDisplayName("ViaVersion-5.4.2.jar", version, emptyMap()))
    }
}

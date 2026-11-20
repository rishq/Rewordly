package com.rewordly.app.core.backup

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupSettingsTest {
    @Test
    fun `backup is on until the user explicitly turns it off`() {
        assertTrue(BackupSettings.enabledOrDefault(null))
        assertTrue(BackupSettings.enabledOrDefault(true))
        assertFalse(BackupSettings.enabledOrDefault(false))
    }
}

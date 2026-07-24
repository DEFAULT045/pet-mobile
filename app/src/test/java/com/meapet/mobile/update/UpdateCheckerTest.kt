package com.meapet.mobile.update

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UpdateCheckerTest {

    @Test
    fun `compareVersions treats v prefix as equal`() {
        assertEquals(0, UpdateChecker.compareVersions("v1.0.2", "1.0.2"))
        assertEquals(0, UpdateChecker.compareVersions("1.0.0", "v1.0.0"))
    }

    @Test
    fun `isNewer detects patch and minor bumps`() {
        assertTrue(UpdateChecker.isNewer("1.0.3", "1.0.2"))
        assertTrue(UpdateChecker.isNewer("v1.1.0", "1.0.9"))
        assertFalse(UpdateChecker.isNewer("1.0.2", "1.0.2"))
        assertFalse(UpdateChecker.isNewer("1.0.1", "1.0.2"))
    }

    @Test
    fun `compareVersions handles uneven segment counts`() {
        assertTrue(UpdateChecker.compareVersions("1.0.0.1", "1.0.0") > 0)
        assertTrue(UpdateChecker.compareVersions("1.0", "1.0.1") < 0)
    }
}

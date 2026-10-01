package io.github.mwalczak.spelucky.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateStateTest {

    @Test
    fun readsBuildNumberFromReleaseTag() {
        assertEquals(12, UpdateState.buildFromTag("build-12"))
        assertEquals(0, UpdateState.buildFromTag("v1.0"))
        assertEquals(0, UpdateState.buildFromTag("build-"))
        assertEquals(0, UpdateState.buildFromTag("rebuild-3"))
    }

    @Test
    fun updateOnlyOfferedForNewerBuilds() {
        val u = UpdateState()
        u.currentBuild = 12
        u.latestBuild = 12
        assertFalse(u.available)
        u.latestBuild = 11
        assertFalse(u.available)
        u.latestBuild = 13
        assertTrue(u.available)
        u.currentBuild = 0 // local build
        assertFalse(u.available)
    }
}

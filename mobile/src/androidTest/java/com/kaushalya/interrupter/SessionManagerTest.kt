package com.kaushalya.interrupter

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kaushalya.interrupter.data.SessionManager
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SessionManagerTest {

    @Test
    fun sessionId_survives_instance_recreation() {
        val appContext = ApplicationProvider.getApplicationContext<android.content.Context>()
        appContext.getSharedPreferences("auth_session", android.content.Context.MODE_PRIVATE).edit().clear().apply()

        val sm1 = SessionManager(appContext)
        sm1.sessionId = "test-session-123"
        sm1.loginId = "testuser"
        sm1.parentName = "Test Parent"
        sm1.isGuest = false

        assertEquals("test-session-123", sm1.sessionId)
        assertEquals("testuser", sm1.loginId)
        assertEquals("Test Parent", sm1.parentName)
        assertFalse(sm1.isGuest)
        assertTrue(sm1.isLoggedIn())

        val sm2 = SessionManager(appContext)
        assertEquals("test-session-123", sm2.sessionId)
        assertEquals("testuser", sm2.loginId)
        assertEquals("Test Parent", sm2.parentName)
        assertFalse(sm2.isGuest)
        assertTrue(sm2.isLoggedIn())
    }

    @Test
    fun clear_removes_all_data() {
        val appContext = ApplicationProvider.getApplicationContext<android.content.Context>()
        appContext.getSharedPreferences("auth_session", android.content.Context.MODE_PRIVATE).edit().clear().apply()

        val sm = SessionManager(appContext)
        sm.sessionId = "session-to-clear"
        sm.loginId = "user"
        sm.parentName = "Parent"
        assertTrue(sm.isLoggedIn())

        sm.clear()
        assertNull(sm.sessionId)
        assertNull(sm.loginId)
        assertNull(sm.parentName)
        assertFalse(sm.isLoggedIn())
    }

    @Test
    fun guest_flag_persists() {
        val appContext = ApplicationProvider.getApplicationContext<android.content.Context>()
        appContext.getSharedPreferences("auth_session", android.content.Context.MODE_PRIVATE).edit().clear().apply()

        val sm1 = SessionManager(appContext)
        sm1.isGuest = true
        assertTrue(sm1.isGuest)
        assertTrue(sm1.isLoggedIn())

        val sm2 = SessionManager(appContext)
        assertTrue(sm2.isGuest)
        assertTrue(sm2.isLoggedIn())
    }

    @Test
    fun hasSeenCarousel_persists() {
        val appContext = ApplicationProvider.getApplicationContext<android.content.Context>()
        appContext.getSharedPreferences("auth_session", android.content.Context.MODE_PRIVATE).edit().clear().apply()

        val sm1 = SessionManager(appContext)
        assertFalse(sm1.hasSeenCarousel)
        sm1.hasSeenCarousel = true

        val sm2 = SessionManager(appContext)
        assertTrue(sm2.hasSeenCarousel)
    }

    @Test
    fun empty_session_is_not_logged_in() {
        val appContext = ApplicationProvider.getApplicationContext<android.content.Context>()
        appContext.getSharedPreferences("auth_session", android.content.Context.MODE_PRIVATE).edit().clear().apply()

        val sm = SessionManager(appContext)
        assertNull(sm.sessionId)
        assertFalse(sm.isLoggedIn())
    }
}

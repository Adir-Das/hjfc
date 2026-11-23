package com.example

import com.example.core.config.AppConfig
import com.example.core.i18n.AppLanguage
import com.example.core.i18n.Formatters
import com.example.core.i18n.StringsProvider
import com.example.domain.model.*
import org.junit.Assert.*
import org.junit.Test

class ExampleUnitTest {
    @Test
    fun `dhaka timezone and bdt currency formatting`() {
        assertEquals("Asia/Dhaka", AppConfig.DHAKA_ZONE.id)
        assertEquals("৳ 400", Formatters.formatBdt(400.0))
        assertEquals("৳ 1,250.50", Formatters.formatBdt(1250.5))
    }

    @Test
    fun `bengali default localization and english switch`() {
        assertEquals(AppLanguage.BENGALI, AppLanguage.fromCode(null))
        val bn = StringsProvider.get(AppLanguage.BENGALI)
        val en = StringsProvider.get(AppLanguage.ENGLISH)
        assertEquals("হোম", bn.navHome)
        assertEquals("Home", en.navHome)
        assertTrue(bn.matchScheduleNote.isNotBlank())
        assertTrue(en.matchScheduleNote.contains("Super Admin"))
    }

    @Test
    fun `role and status enum parsing`() {
        assertEquals(UserRole.SUPER_ADMIN, UserRole.from("SUPER_ADMIN"))
        assertEquals(UserRole.TREASURER, UserRole.from("TREASURER"))
        assertEquals(UserRole.MEMBER, UserRole.from("unknown"))
        assertEquals(AvailabilityStatus.AVAILABLE, AvailabilityStatus.from("AVAILABLE"))
        assertEquals(AttendanceStatus.PRESENT, AttendanceStatus.from("PRESENT"))
        assertEquals(MatchStatus.UPCOMING, MatchStatus.from("UPCOMING"))
    }
}

package org.mpc.presentation.model

import de.infix.testBalloon.framework.core.testSuite
import org.mpc.domain.session.PortalNeed
import org.mpc.domain.session.PortalStatus
import org.mpc.domain.session.Unavailability
import kotlin.test.assertEquals

val portalAccountTests by testSuite {
    test("a live session offers sign-out") {
        val account = PortalStatus.Live.toPortalAccount()

        assertEquals(PortalAccountTone.SIGNED_IN, account.tone)
        assertEquals(listOf(PortalAccountAction.SIGN_OUT), account.actions)
    }

    test("an unconfirmed session offers retry before sign-in") {
        val account = PortalStatus.Unavailable(Unavailability.entries.first()).toPortalAccount()

        assertEquals(PortalAccountTone.ATTENTION, account.tone)
        assertEquals(listOf(PortalAccountAction.RETRY, PortalAccountAction.SIGN_IN), account.actions)
    }

    test("a displaced session warns that signing in again signs out elsewhere") {
        val account = PortalStatus.Blocked(PortalNeed.DISPLACED).toPortalAccount()

        assertEquals(PortalAccountTone.ATTENTION, account.tone)
        assertEquals("重新登入後，其他地方的 Portal 會被登出", account.detail)
        assertEquals(listOf(PortalAccountAction.SIGN_IN), account.actions)
    }

    test("checking offers no action yet") {
        assertEquals(emptyList(), PortalStatus.Unknown.toPortalAccount().actions)
    }
}

package org.mpc.presentation.model

import dev.icerock.moko.resources.StringResource
import org.mpc.domain.session.PortalNeed
import org.mpc.domain.session.PortalStatus
import org.mpc.shared.MR
import org.mpc.shared.portal_status_checking
import org.mpc.shared.portal_status_displaced
import org.mpc.shared.portal_status_displaced_detail
import org.mpc.shared.portal_status_needs_portal_page
import org.mpc.shared.portal_status_signed_in
import org.mpc.shared.portal_status_signed_out
import org.mpc.shared.portal_status_signed_out_detail
import org.mpc.shared.portal_status_unavailable
import org.mpc.shared.portal_status_unavailable_detail
import org.mpc.shared.portal_status_user_signed_out

enum class PortalAccountTone {
    CHECKING,
    SIGNED_IN,
    SIGNED_OUT,
    ATTENTION,
}

enum class PortalAccountAction {
    SIGN_IN,
    SIGN_OUT,
    RETRY,
}

/** The Portal tab's account row: what to say about the session and which actions to offer, most important first. */
data class PortalAccount(
    val title: StringResource,
    val detail: StringResource?,
    val tone: PortalAccountTone,
    val actions: List<PortalAccountAction>,
)

fun PortalStatus.toPortalAccount(): PortalAccount = when (this) {
    PortalStatus.Unknown -> PortalAccount(MR.strings.portal_status_checking, null, PortalAccountTone.CHECKING, emptyList())

    PortalStatus.Live -> PortalAccount(MR.strings.portal_status_signed_in, null, PortalAccountTone.SIGNED_IN, listOf(PortalAccountAction.SIGN_OUT))

    PortalStatus.SignedOut -> signedOut(MR.strings.portal_status_signed_out)

    is PortalStatus.Unavailable -> PortalAccount(
        title = MR.strings.portal_status_unavailable,
        detail = MR.strings.portal_status_unavailable_detail,
        tone = PortalAccountTone.ATTENTION,
        actions = listOf(PortalAccountAction.RETRY, PortalAccountAction.SIGN_IN),
    )

    is PortalStatus.Blocked -> when (need) {
        PortalNeed.DISPLACED -> PortalAccount(
            title = MR.strings.portal_status_displaced,
            detail = MR.strings.portal_status_displaced_detail,
            tone = PortalAccountTone.ATTENTION,
            actions = listOf(PortalAccountAction.SIGN_IN),
        )

        PortalNeed.USER_SIGNED_OUT -> signedOut(MR.strings.portal_status_user_signed_out)

        PortalNeed.NEEDS_PORTAL_PAGE -> signedOut(MR.strings.portal_status_needs_portal_page)

        PortalNeed.MANUAL_SIGN_IN,
        PortalNeed.COOLDOWN,
        PortalNeed.NO_CREDENTIALS,
        -> signedOut(MR.strings.portal_status_signed_out)
    }
}

private fun signedOut(title: StringResource) = PortalAccount(
    title = title,
    detail = MR.strings.portal_status_signed_out_detail,
    tone = PortalAccountTone.SIGNED_OUT,
    actions = listOf(PortalAccountAction.SIGN_IN),
)

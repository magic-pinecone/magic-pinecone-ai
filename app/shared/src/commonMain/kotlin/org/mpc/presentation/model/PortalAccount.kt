package org.mpc.presentation.model

import org.mpc.domain.session.PortalNeed
import org.mpc.domain.session.PortalStatus

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
    val title: String,
    val detail: String?,
    val tone: PortalAccountTone,
    val actions: List<PortalAccountAction>,
)

fun PortalStatus.toPortalAccount(): PortalAccount = when (this) {
    PortalStatus.Unknown -> PortalAccount("正在確認登入狀態", null, PortalAccountTone.CHECKING, emptyList())

    PortalStatus.Live -> PortalAccount("已登入 Portal", null, PortalAccountTone.SIGNED_IN, listOf(PortalAccountAction.SIGN_OUT))

    PortalStatus.SignedOut -> signedOut("尚未登入 Portal")

    is PortalStatus.Unavailable -> PortalAccount(
        title = "無法確認登入狀態",
        detail = "請檢查網路連線後重試",
        tone = PortalAccountTone.ATTENTION,
        actions = listOf(PortalAccountAction.RETRY, PortalAccountAction.SIGN_IN),
    )

    is PortalStatus.Blocked -> when (need) {
        PortalNeed.DISPLACED -> PortalAccount(
            title = "Portal 已在其他地方登入",
            detail = "重新登入後，其他地方的 Portal 會被登出",
            tone = PortalAccountTone.ATTENTION,
            actions = listOf(PortalAccountAction.SIGN_IN),
        )

        PortalNeed.USER_SIGNED_OUT -> signedOut("已登出 Portal")

        PortalNeed.NEEDS_PORTAL_PAGE -> signedOut("請在 Portal 頁面完成登入")

        PortalNeed.MANUAL_SIGN_IN,
        PortalNeed.COOLDOWN,
        PortalNeed.NO_CREDENTIALS,
        -> signedOut("尚未登入 Portal")
    }
}

private fun signedOut(title: String) = PortalAccount(
    title = title,
    detail = "登入後即可直接開啟校務系統",
    tone = PortalAccountTone.SIGNED_OUT,
    actions = listOf(PortalAccountAction.SIGN_IN),
)

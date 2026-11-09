package org.mpc.domain.session

/** Why a campus site or Portal could not be reached. Never carries a URL or exception text. */
enum class Unavailability {
    NETWORK,
    UNEXPECTED_RESPONSE,
}

/**
 * Why the app stopped short of signing in to Portal by itself. Portal allows one active sign-in per account,
 * so every Portal sign-in ends the user's Portal session in their browser.
 */
enum class PortalNeed {
    /** MANUAL_PORTAL mode: the user always signs in on the Portal page. */
    MANUAL_SIGN_IN,

    /** Portal answered `/expired`: someone signed in elsewhere, and signing in again would sign them out. */
    DISPLACED,

    /** The user signed out everywhere; the app stays out until the user signs in. */
    USER_SIGNED_OUT,

    /** Portal asked for a step only its own page can do, such as TOTP. */
    NEEDS_PORTAL_PAGE,

    /** The app signed in to Portal by itself recently; it will not do so again until the cooldown ends. */
    COOLDOWN,

    /** SECURE_CREDENTIALS mode without saved credentials. */
    NO_CREDENTIALS,
}

package org.mpc.data.session

import de.infix.testBalloon.framework.core.testSuite
import kotlinx.coroutines.test.runTest
import kotlin.test.assertEquals

private const val SITE = "https://ncueeclass.ncu.edu.tw/"

private fun cookieNames(header: CookieHeader?): Set<String> = header?.value.orEmpty()
    .split(';')
    .map { it.substringBefore('=').trim() }
    .filter { it.isNotEmpty() }
    .toSet()

val platformCookieJarDeviceTests by testSuite {
    test("clear removes the site's cookies and keeps parent-domain and named ones") {
        runTest {
            val jar = createPlatformCookieJar()
            jar.storeSetCookies(
                SITE,
                listOf(
                    "PHPSESSID=site; path=/; secure; HttpOnly; SameSite=Lax",
                    "locale=en-us; Max-Age=2592000; path=/; secure; HttpOnly; SameSite=Lax",
                    "scoped=host; Domain=ncueeclass.ncu.edu.tw; Path=/; Secure",
                    "shared=campus; Domain=ncu.edu.tw; Path=/; Secure",
                    "kept=yes; Path=/; Secure",
                ),
            )
            assertEquals(setOf("PHPSESSID", "locale", "scoped", "shared", "kept"), cookieNames(jar.cookieHeader(SITE)))

            jar.clear(SITE, keep = setOf("kept"))

            assertEquals(setOf("shared", "kept"), cookieNames(jar.cookieHeader(SITE)))
        }
    }
}

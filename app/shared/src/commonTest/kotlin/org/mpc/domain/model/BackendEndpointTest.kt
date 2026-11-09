package org.mpc.domain.model

import de.infix.testBalloon.framework.core.testSuite
import kotlin.test.assertEquals
import kotlin.test.assertNull

val backendEndpointTests by testSuite {
    test("an http or https address is kept without surrounding space or trailing slashes") {
        assertEquals("http://10.0.2.2:8000", normalizeBackendBaseUrl("  http://10.0.2.2:8000/ "))
        assertEquals("https://api.example.edu/pinecone", normalizeBackendBaseUrl("https://api.example.edu/pinecone//"))
    }

    test("anything that is not an http address is rejected") {
        assertNull(normalizeBackendBaseUrl(""))
        assertNull(normalizeBackendBaseUrl("localhost:8000"))
        assertNull(normalizeBackendBaseUrl("ftp://example.edu"))
        assertNull(normalizeBackendBaseUrl("http://"))
        assertNull(normalizeBackendBaseUrl("http://exa mple.edu"))
        assertNull(normalizeBackendBaseUrl("http://example.edu/?semester=115-1"))
    }
}

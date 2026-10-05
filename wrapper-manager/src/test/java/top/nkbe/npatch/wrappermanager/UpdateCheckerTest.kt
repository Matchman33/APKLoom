package top.nkbe.npatch.wrappermanager

import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

internal fun releaseJson(version: String = "1.0.10"): String = """
    {"tag_name":"v$version","draft":false,"prerelease":false,
     "html_url":"https://github.com/Matchman33/APKLoom/releases/tag/v$version",
     "assets":[{"name":"apkloom-cli-$version.jar","browser_download_url":"https://example.com/cli.jar"},
       {"name":"APK-Loom-$version.apk",
        "browser_download_url":"https://github.com/Matchman33/APKLoom/releases/download/v$version/APK-Loom-$version.apk"}]}
""".trimIndent()

class UpdateCheckerTest {
    private lateinit var server: MockWebServer
    private var response = releaseJson()
    private var status = 200
    private var limited = false

    @Before fun startServer() {
        server = MockWebServer()
        server.start()
    }

    @After fun stopServer() { server.shutdown() }

    private fun checker() = UpdateChecker(server.url("/latest").toUrl())
    private fun fetch(): ReleaseInfo {
        val reply = MockResponse().setResponseCode(status).setBody(response)
        if (limited) reply.setHeader("X-RateLimit-Remaining", "0")
        if (status == 302) reply.setHeader("Location", "https://example.com/")
        server.enqueue(reply)
        return checker().latest()
    }
    private fun failure(reason: UpdateFailure, action: () -> Unit) {
        assertEquals(reason, assertThrows(UpdateCheckException::class.java) { action() }.failure)
    }

    @Test fun constructionDoesNotPerformNetworkRequests() {
        checker()
        assertEquals(0, server.requestCount)
    }

    @Test fun explicitCheckSelectsManagerApkWithoutSendingCredentials() {
        val result = fetch()
        assertEquals("1.0.10", result.version)
        assertTrue(result.downloadUrl.endsWith("/v1.0.10/APK-Loom-1.0.10.apk"))
        assertEquals(1, server.requestCount)
        val request = server.takeRequest()
        assertNull(request.getHeader("Authorization"))
        assertTrue(request.getHeader("User-Agent")!!.startsWith("APK-Loom/"))
    }

    @Test fun numericComparisonHandlesDoubleDigitsAndMissingPatchSegments() {
        assertTrue(AppVersion.parse("1.0.10")!! > AppVersion.parse("1.0.9")!!)
        assertTrue(AppVersion.parse("1.10.0")!! > AppVersion.parse("1.9.99")!!)
        assertEquals(0, AppVersion.parse("v1.0.9")!!.compareTo(AppVersion.parse("1.0.9.0")!!))
        assertEquals(0, AppVersion.parse("1.0")!!.compareTo(AppVersion.parse("1.0.0")!!))
        assertTrue(AppVersion.parse("1.0.9")!! > AppVersion.parse("1.0.9-beta.1")!!)
    }

    @Test fun invalidAndOverflowingVersionsAreRejected() {
        for (value in listOf("latest", "", "1", "1.-2.3", "1.2.3.4.5", "1.2.999999999999999999999999")) {
            assertNull(value, AppVersion.parse(value))
        }
    }

    @Test fun draftsAndPrereleasesAreRejected() {
        failure(UpdateFailure.INVALID_RELEASE) {
            UpdateChecker.parseRelease(releaseJson().replace("\"draft\":false", "\"draft\":true"))
        }
        failure(UpdateFailure.INVALID_RELEASE) {
            UpdateChecker.parseRelease(releaseJson().replace("\"prerelease\":false", "\"prerelease\":true"))
        }
    }

    @Test fun foreignReleaseAndDownloadLinksAreRejected() {
        failure(UpdateFailure.INVALID_RELEASE) {
            UpdateChecker.parseRelease(releaseJson().replace("github.com/Matchman33", "example.com/Matchman33"))
        }
        failure(UpdateFailure.INVALID_RELEASE) {
            UpdateChecker.parseRelease(releaseJson().replace("https://github.com/Matchman33/APKLoom/releases/download/",
                "http://github.com/Matchman33/APKLoom/releases/download/"))
        }
    }

    @Test fun malformedPayloadAndMissingManagerApkAreRejected() {
        for (body in listOf("not json", "null", "{}", releaseJson().replace("APK-Loom-1.0.10.apk", "other.apk"),
            releaseJson().replace("\"v1.0.10\"", "10"))) {
            failure(UpdateFailure.INVALID_RELEASE) { UpdateChecker.parseRelease(body) }
        }
    }

    @Test fun statusCodesAreMappedToRecoverableErrors() {
        status = 404
        failure(UpdateFailure.NO_RELEASE) { fetch() }
        status = 500
        failure(UpdateFailure.SERVICE) { fetch() }
        status = 429
        failure(UpdateFailure.RATE_LIMIT) { fetch() }
        status = 403
        failure(UpdateFailure.SERVICE) { fetch() }
        limited = true
        failure(UpdateFailure.RATE_LIMIT) { fetch() }
    }

    @Test fun redirectsAreNotFollowed() {
        status = 302
        failure(UpdateFailure.SERVICE) { fetch() }
        assertEquals(1, server.requestCount)
    }

    @Test fun oversizedResponseIsRejected() {
        response = " ".repeat(1024 * 1024 + 1)
        failure(UpdateFailure.INVALID_RELEASE) { fetch() }
    }

    @Test fun connectionFailureIsReported() {
        val checker = checker()
        server.shutdown()
        failure(UpdateFailure.NETWORK) { checker.latest() }
    }
}

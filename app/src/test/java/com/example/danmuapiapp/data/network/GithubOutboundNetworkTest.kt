package com.example.danmuapiapp.data.network

import org.junit.Assert.*
import org.junit.Test
import java.net.URI

class GithubOutboundNetworkTest {
    @Test fun routeOnlyExactOfficialHttpsHosts() {
        GithubOutboundNetwork.hosts.forEach { host ->
            assertTrue(GithubOutboundNetwork.isTarget(URI("https://$host/path")))
            assertTrue(GithubOutboundNetwork.isTarget(URI("https://$host:443/path")))
            assertFalse(GithubOutboundNetwork.isTarget(URI("https://$host.evil.test/path")))
            assertFalse(GithubOutboundNetwork.isTarget(URI("http://$host/path")))
            assertFalse(GithubOutboundNetwork.isTarget(URI("https://$host:444/path")))
            assertFalse(GithubOutboundNetwork.isTarget(URI("https://user:secret@$host/path")))
        }
        assertFalse(GithubOutboundNetwork.isTarget(URI("https://gh-proxy.org/https://github.com/repo")))
        assertFalse(GithubOutboundNetwork.isTarget(URI("https://127.0.0.1/path")))
    }
}

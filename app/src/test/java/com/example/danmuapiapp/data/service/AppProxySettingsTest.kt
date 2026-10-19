package com.example.danmuapiapp.data.service

import org.junit.Assert.*
import org.junit.Test
import org.json.JSONObject

class AppProxySettingsTest {
    @Test fun `native proxy settings roundtrip without losing enhanced settings`() {
        val node=AppProxyNode(id="one",name="HK",type="socks5",host="proxy.example",port=1080,username="user",password="pass")
        val settings=AppOutboundSettings(enabled=true,sources=setOf("douban"),proxy=AppProxySettings(true,nodes=listOf(node),rules=mapOf("aiyifan" to "node:one")))
        assertEquals(settings,AppOutboundSettings.decode(settings.encode()))
        assertEquals(AppProxySettings(),AppProxySettings.decode(null))
    }
    @Test fun `URI imports preserve encoded credentials IPv6 and explicit default ports`() {
        val node=AppProxyNode.fromUri("socks5://u%40x:p%3Aa@[2001:db8::1]:1080#Hong%20Kong")
        assertEquals("u@x",node.username);assertEquals("p:a",node.password);assertEquals("2001:db8::1",node.host)
        assertEquals("Hong Kong",node.name);assertEquals(443,AppProxyNode.fromUri("https://proxy.test").port)
        for(uri in listOf("vless://user@host:443","https://host.test/sub?token=private","http://host.test:0","http://host.test:99999")) {
            assertThrows(Exception::class.java) {AppProxyNode.fromUri(uri)}
        }
    }
    @Test fun `upgrade discards subscriptions and unsupported nodes but keeps source bindings and enhanced choices`() {
        val raw="""{"enabled":true,"sources":["douban","bahamut"],"httpVersion":"h2","connectTimeoutMs":5000,
            "proxy":{"enabled":true,"rules":{"aiyifan":"node:old","github":"node:ok"},"subscriptions":[{"url":"https://private.test/token"}],
            "nodes":[{"id":"old","name":"old","type":"trojan","host":"old.test","port":443,"password":"secret","protocolOptions":{"network":"tcp"}},
            {"id":"ok","name":"ok","type":"http","host":"node.test","port":8080,"subscriptionId":"removed","protocolOptions":{}}]}}"""
        val settings=AppOutboundSettings.decode(raw)
        assertTrue(settings.enabled);assertEquals(setOf("douban","bahamut"),settings.sources);assertEquals("h2",settings.httpVersion)
        assertEquals(5000,settings.connectTimeoutMs);assertEquals(listOf("ok"),settings.proxy.nodes.map {it.id})
        assertEquals("node:old",settings.proxy.rules["aiyifan"]);assertTrue(settings.proxy.label("node:old").contains("失效"))
        val encoded=settings.encode();assertFalse(encoded.contains("subscriptions"));assertFalse(encoded.contains("private.test"));assertFalse(encoded.contains("subscriptionId"));assertFalse(encoded.contains("protocolOptions"))
    }
    @Test fun `native TLS options survive upgrade and never lower target TLS verification`() {
        val old=JSONObject("""{"id":"https","name":"https","type":"https","host":"proxy.test","port":443,"protocolOptions":{"sni":"tls.test","skip-cert-verify":true,"udp":true}}""")
        val node=AppProxyNode.decode(old);assertEquals("tls.test",node.tlsServerName);assertTrue(node.skipCertVerify);assertTrue(node.usesTls)
        assertEquals(node,AppProxyNode.decode(node.json()))
        assertThrows(Exception::class.java) {node.copy(type="http").validate()}
    }
    @Test fun `paste accepts only explicit supported node links and gives redacted line errors`() {
        assertEquals(2,ProxyNodeLinks.parse("http://host.test:8080#one\nsocks5://host.test:1080#two").size)
        val error=assertThrows(IllegalArgumentException::class.java) {ProxyNodeLinks.parse("http://host.test\ntrojan://private-password@remote.test:443")}
        assertTrue(error.message!!.contains("第 2 行"));assertFalse(error.message!!.contains("private-password"))
    }
    @Test fun `a retained root process cannot claim current networking without matching runtime fingerprint`() {
        assertTrue(networkRuntimeNeedsRestart("new-hash",""));assertTrue(networkRuntimeNeedsRestart("new-hash","old-hash"))
        assertFalse(networkRuntimeNeedsRestart("new-hash","new-hash"))
    }
    @Test fun `old global direct defaults migrate while per-source device network choices remain explicit`() {
        val settings=AppOutboundSettings.decode("""{"proxy":{"enabled":true,"routeVersion":2,"defaultRoute":"direct",
            "rules":{"aiyifan":"direct","tmdb":"node:one","douban":"enhanced"},
            "nodes":[{"id":"one","name":"HTTP","host":"proxy.test","port":2080,"username":"user","password":"pass"}]}}""")
        assertEquals("inherit",settings.proxy.defaultRoute)
        assertEquals("direct",settings.proxy.rules["aiyifan"])
        assertEquals("node:one",settings.proxy.rules["tmdb"])
        assertEquals("enhanced",settings.proxy.rules["douban"])
        assertEquals("pass",settings.proxy.nodes.single().password)
        assertTrue(settings.encode().contains("\"direct\""))
        assertEquals(settings,AppOutboundSettings.decode(settings.encode()))
        val catalog=ProxySourceCatalog(listOf("aiyifan","tmdb"),setOf("aiyifan"),setOf("aiyifan","tmdb"))
        assertEquals("direct",settings.sourceRoute("aiyifan",catalog))
        assertEquals("node:one",settings.sourceRoute("tmdb",catalog))
        assertEquals("沿用核心配置",settings.proxy.label("inherit"))
    }
    @Test fun `old per-source direct route keeps its historical core-inherit meaning`() {
        val settings=AppOutboundSettings.decode("""{"proxy":{"enabled":true,"rules":{"aiyifan":"direct"}}}""")
        assertEquals("inherit",settings.proxy.rules["aiyifan"])
        assertTrue(settings.encode().contains("\"routeVersion\":2"))
    }
    @Test fun `explicit inheritance overrides an old default node without changing another source binding`() {
        val node=AppProxyNode(id="one",name="HTTP",host="proxy.test")
        val original=AppOutboundSettings(proxy=AppProxySettings(true,"node:one",listOf(node),mapOf("tmdb" to "node:one")))
        val inherited=original.withSourceRoute("aiyifan","inherit")
        val catalog=ProxySourceCatalog(listOf("aiyifan","tmdb","douban"),emptySet())
        assertEquals("inherit",inherited.sourceRoute("aiyifan",catalog))
        assertEquals("node:one",inherited.sourceRoute("tmdb",catalog))
        assertEquals("node:one",inherited.sourceRoute("douban",catalog))
        val direct=inherited.withSourceRoute("aiyifan","direct")
        assertEquals("direct",direct.sourceRoute("aiyifan",catalog))
        assertEquals("node:one",direct.sourceRoute("tmdb",catalog))
    }
    @Test fun `core proxy sources inherit core config even when App has a global default node`() {
        val node=AppProxyNode(id="one",name="HTTP",host="proxy.test")
        val settings=AppOutboundSettings(proxy=AppProxySettings(true,"node:one",listOf(node)))
        val catalog=ProxySourceCatalog(listOf("bahamut","tmdb","bilibili","animeko","aiyifan","douban"),emptySet(),
            setOf("bahamut","tmdb","bilibili","animeko","aiyifan"))
        for(source in AppProxySourceCatalog.coreProxySources) assertEquals("legacy",settings.sourceRoute(source,catalog))
        assertEquals("node:one",settings.sourceRoute("douban",catalog))
        val override=settings.withSourceRoute("bilibili","node:one")
        assertEquals("node:one",override.sourceRoute("bilibili",catalog))
        assertEquals("legacy",override.sourceRoute("tmdb",catalog))
    }
    @Test fun `default labels and legacy proxy attribution follow core source support`() {
        val supported=setOf("bahamut","tmdb","bilibili","animeko","aiyifan")
        val unsupported=listOf("douban","dandan","tencent","youku","iqiyi","imgo","custom","other","vod","newsource")
        for(source in supported) assertEquals("沿用核心配置",AppProxySourceCatalog.inheritLabel(source))
        for(source in unsupported) assertEquals("使用设备当前网络",AppProxySourceCatalog.inheritLabel(source))
        assertEquals("沿用下载设置",AppProxySourceCatalog.inheritLabel("github"))
        val sources=supported.toList()+unsupported
        for(raw in listOf("@https://reverse.test","http://user:pass@proxy.test:2080/","HTTPS://proxy.test:2080")) {
            assertEquals(supported,AppProxySourceCatalog.legacyProxied(sources,raw))
        }
        assertEquals(setOf("aiyifan"),AppProxySourceCatalog.legacyProxied(sources,"aiyifan@https://reverse.test"))
        assertEquals(emptySet<String>(),AppProxySourceCatalog.legacyProxied(sources,"douban@https://unused.test"))
    }
}

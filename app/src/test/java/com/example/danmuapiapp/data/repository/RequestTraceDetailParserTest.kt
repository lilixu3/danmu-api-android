package com.example.danmuapiapp.data.repository

import com.example.danmuapiapp.domain.model.TraceValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.json.JSONObject
import org.junit.Test

class RequestTraceDetailParserTest {
    @Test
    fun recognizesFileNameOnlyMatchRequestsByTheirParameters() {
        assertEquals("女友的朋友 S01E01", parseFileNameOnlyMatch(JSONObject("""{
            "fileName": "女友的朋友 S01E01",
            "fileHash": "123d05841b9456ccc7420b3f0bb21c3b",
            "fileSize": 0,
            "videoDuration": 0,
            "matchMode": "fileNameOnly"
        }"""))?.fileName)
        val emptyMetadata = parseFileNameOnlyMatch(JSONObject("""{
            "fileName":" 女友的朋友 S01E01 ", "fileHash":"hash", "fileSize":0,
            "videoDuration":0, "matchMode":"fileNameOnly"
        }"""))!!
        assertEquals(0L, emptyMetadata.fileSize)
        assertEquals(0L, emptyMetadata.videoDuration)
        assertEquals("hash", emptyMetadata.fileHash)
        assertNull(parseFileNameOnlyMatch(JSONObject("""{"fileName":"女友的朋友 S01E01","matchMode":"match"}""")))
        assertNull(parseFileNameOnlyMatch(JSONObject("""{"matchMode":"fileNameOnly"}""")))
        assertNull(parseFileNameOnlyMatch(JSONObject("""{"fileName":"   ","matchMode":"fileNameOnly"}""")))
        assertNull(parseFileNameOnlyMatch(null))
    }

    @Test
    fun parsesOverviewDiagnosisPipelineAndConfigUsage() {
        val detail = parseRequestTraceDetail(
            """{
                "trace": {
                    "id": "trace-abc-1",
                    "method": "GET",
                    "path": "/api/v2/search/anime",
                    "query": "?keyword=test",
                    "clientIp": "192.168.1.*",
                    "statusCode": 200,
                    "timestamp": 1795360872000,
                    "totalMs": 42,
                    "prepareMs": 2,
                    "handlerMs": 40,
                    "diagnosis": {
                        "conclusion": "结果在过滤阶段归零",
                        "zeroStep": "结果过滤",
                        "chain": [{"name": "搜索", "rawCount": 3, "count": 0, "zero": true}]
                    },
                    "steps": [{
                        "name": "搜索·结果过滤",
                        "status": "ok",
                        "ms": 12,
                        "inputs": {"count": 3},
                        "outputs": {"count": 0},
                        "table": {"columns":["源","数量"], "rows":[{"数量":0, "源":"测试源"}, {"源":"其他源"}]},
                        "changes": [{
                            "label":"默认匹配","kind":"decision","summary":"未命中","config":"TITLE_FILTER",
                            "context":{"hit":false,"q":"样例"},
                            "removed":[{"value":"剧名","aliases":["别名"],"source":"测试源"}],"removedCount":3
                        }, {
                            "label":"改写","before":null,"after":{"新字段":[1,2]}
                        }]
                    }],
                    "configs": [{
                        "name": "TITLE_FILTER",
                        "category": "match",
                        "description": "标题过滤规则",
                        "value": "test",
                        "used": true,
                        "hit": false,
                        "step": "搜索·结果过滤",
                        "effectStep": "标题过滤"
                    }]
                }
            }"""
        )!!

        assertEquals("trace-abc-1", detail.id)
        assertEquals("/api/v2/search/anime", detail.path)
        assertEquals(1795360872000L, detail.timestamp)
        assertEquals(42L, detail.totalMs)
        assertEquals("结果在过滤阶段归零", detail.diagnosis?.conclusion)
        assertTrue(detail.diagnosis?.chain?.single()?.zero == true)
        assertEquals(12L, detail.steps.single().durationMs)
        assertEquals(3, detail.diagnosis?.chain?.single()?.rawCount)
        val step = detail.steps.single()
        assertEquals(TraceValue.Fields(mapOf("count" to TraceValue.Scalar("3"))), step.inputs)
        assertEquals(listOf("源", "数量"), step.table?.columns)
        assertEquals(listOf(TraceValue.Scalar("测试源"), TraceValue.Scalar("0")), step.table?.rows?.first())
        assertEquals(TraceValue.Empty, step.table?.rows?.last()?.last())
        val change = step.changes.first()
        assertEquals("TITLE_FILTER", change.config)
        assertEquals("decision", change.kind)
        assertEquals(TraceValue.Flag(false), (change.context as TraceValue.Fields).entries["hit"])
        assertEquals(3, change.removedCount)
        assertEquals(TraceValue.Items(listOf(TraceValue.Scalar("别名"))),
            (change.removed.single() as TraceValue.Fields).entries["aliases"])
        assertEquals(TraceValue.Empty, step.changes.last().before)
        assertNull(change.before)
        assertEquals(TraceValue.Items(listOf(TraceValue.Scalar("1"), TraceValue.Scalar("2"))),
            (step.changes.last().after as TraceValue.Fields).entries["新字段"])
        assertEquals("TITLE_FILTER", detail.configs.single().name)
        assertTrue(detail.configs.single().used)
        assertFalse(detail.configs.single().hit!!)
        assertEquals("标题过滤", detail.configs.single().effectStep)
    }

    @Test
    fun ignoresMalformedOrMissingTracePayloads() {
        assertNull(parseRequestTraceDetail("not-json"))
        assertNull(parseRequestTraceDetail("""{"records": []}"""))
    }
}

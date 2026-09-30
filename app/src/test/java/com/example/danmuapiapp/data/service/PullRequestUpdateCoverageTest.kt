package com.example.danmuapiapp.data.service

import com.example.danmuapiapp.domain.model.CorePullRequestInclusion.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class PullRequestUpdateCoverageTest {
    private val old = "a".repeat(40)
    private val fresh = "b".repeat(40)
    private val base = "c".repeat(40)
    private val target = "d".repeat(40)
    private val merged = "e".repeat(40)
    private val included = PullRequestCommitComparison(Included, completeFiles = true)
    private val absent = PullRequestCommitComparison(NotIncluded, completeFiles = true)

    private suspend fun evaluate(
        installed: String = fresh,
        merge: String? = null,
        responses: Map<Pair<String, String>, PullRequestCommitComparison?> = emptyMap(),
        files: Set<String>? = setOf("feature.js")
    ) = PullRequestUpdateCoveragePolicy.evaluate(installed, base, fresh, merge, target,
        compare = { a, b -> responses[a to b] }, currentPullRequestPaths = { files })

    @Test fun `ordinary merge is included only when proven ancestor of target`() = runBlocking {
        assertEquals(Included, evaluate(responses = mapOf((fresh to target) to included)).inclusion)
        assertEquals(NotIncluded, evaluate(responses = mapOf((fresh to target) to absent)).inclusion)
    }

    @Test fun `squash or rebase needs its real merge result inside the selected target`() = runBlocking {
        assertEquals(Included, evaluate(merge = merged, responses = mapOf(
            (fresh to target) to absent, (merged to target) to included)).inclusion)
        assertEquals(NotIncluded, evaluate(merge = merged, responses = mapOf(
            (fresh to target) to absent, (merged to target) to absent)).inclusion)
    }

    @Test fun `merged status cannot stand in for a force pushed local head`() = runBlocking {
        assertEquals(Unknown, evaluate(installed = old, merge = merged, responses = mapOf(
            (old to target) to absent, (fresh to target) to absent, (merged to target) to included)).inclusion)
    }

    @Test fun `target containing old PR head still prompts for new PR commits`() = runBlocking {
        assertEquals(NotIncluded, evaluate(installed = old, responses = mapOf(
            (old to target) to included, (fresh to target) to absent)).inclusion)
    }

    @Test fun `later modifications or revert of a PR file cannot silently count as preserved`() = runBlocking {
        val touched = included.copy(changedPaths = setOf("feature.js"))
        assertEquals(Unknown, evaluate(responses = mapOf((fresh to target) to touched)).inclusion)
        assertEquals(Unknown, evaluate(merge = merged, responses = mapOf(
            (fresh to target) to absent, (merged to target) to touched)).inclusion)
    }

    @Test fun `unrelated changes after integration do not cause a duplicate merge`() = runBlocking {
        assertEquals(Included, evaluate(responses = mapOf(
            (fresh to target) to included.copy(changedPaths = setOf("unrelated.js")))).inclusion)
    }

    @Test fun `old head checks its old file list instead of newer PR file list`() = runBlocking {
        assertEquals(Unknown, evaluate(installed = old, responses = mapOf(
            (old to target) to included.copy(changedPaths = setOf("old-feature.js")),
            (base to old) to absent.copy(changedPaths = setOf("old-feature.js")),
            (fresh to target) to included
        ), files = setOf("new-feature.js")).inclusion)
    }

    @Test fun `missing or truncated evidence stays unknown`() = runBlocking {
        assertEquals(Unknown, evaluate().inclusion)
        assertEquals(Unknown, evaluate(installed = "").inclusion)
        assertEquals(Unknown, evaluate(responses = mapOf((fresh to target) to included.copy(completeFiles = false))).inclusion)
        assertEquals(Unknown, evaluate(responses = mapOf(
            (fresh to target) to included.copy(changedPaths = setOf("other.js"))), files = null).inclusion)
        assertEquals(Unknown, evaluate(merge = merged, responses = mapOf((merged to target) to absent)).inclusion)
    }

    @Test fun `comparison parser preserves rename paths and rejects omitted or capped file evidence`() {
        val renamed = PullRequestCommitComparison.parse("""{"status":"ahead","files":[{"filename":"new.js","previous_filename":"feature.js"}]}""")!!
        assertEquals(setOf("new.js", "feature.js"), renamed.changedPaths)
        assertTrue(renamed.completeFiles)
        assertFalse(PullRequestCommitComparison.parse("""{"status":"ahead"}""")!!.completeFiles)
        assertFalse(PullRequestCommitComparison.parse("""{"status":"ahead","files":[{}]}""")!!.completeFiles)
        val capped = (1..300).joinToString(",") { """{"filename":"$it.js"}""" }
        assertFalse(PullRequestCommitComparison.parse("""{"status":"ahead","files":[$capped]}""")!!.completeFiles)
        assertNull(PullRequestCommitComparison.parse("not json"))
    }
}

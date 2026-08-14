package com.example.danmuapiapp.data.service

import org.eclipse.jgit.internal.JGitText
import org.eclipse.jgit.nls.NLS
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class JGitTranslationBundleTest {
    @Test
    fun `JGit translation bundle supports its reflective constructor and fields`() {
        NLS.clear()
        val bundle = NLS.getBundleFor(JGitText::class.java)

        assertNotNull(bundle)
        assertTrue(bundle.abbreviationLengthMustBeNonNegative.isNotBlank())
    }
}

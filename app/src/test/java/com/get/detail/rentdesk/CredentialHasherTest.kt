package com.get.detail.rentdesk

import com.get.detail.rentdesk.lock.CredentialHasher
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CredentialHasherTest {
    @Test
    fun verifier_acceptsOriginalCredentialAndRejectsWrongCredential() {
        val result = CredentialHasher.create("2580".toCharArray())

        assertTrue(CredentialHasher.verify("2580".toCharArray(), result.salt, result.hash))
        assertFalse(CredentialHasher.verify("0852".toCharArray(), result.salt, result.hash))
    }
}

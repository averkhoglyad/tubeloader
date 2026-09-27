package io.averkhogliad.tubeloader.core

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify

private interface StubbedCollaborator {
    fun answer(): Int
}

class CoreTestStackSmokeTest : StringSpec({
    "kotest assertions run" {
        (1 + 1) shouldBe 2
    }

    "mockk stubs and verifies" {
        val collaborator = mockk<StubbedCollaborator>()
        every { collaborator.answer() } returns 42

        collaborator.answer() shouldBe 42
        verify(exactly = 1) { collaborator.answer() }
    }
})

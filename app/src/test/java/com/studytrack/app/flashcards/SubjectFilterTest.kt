package com.studytrack.app.flashcards

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SubjectFilterTest {
    @Test fun `deleted subject resets to all while existing selection survives`() {
        assertEquals("biology", validSubjectFilter("biology", listOf("maths", "biology")))
        assertNull(validSubjectFilter("maths", listOf("biology")))
        assertNull(validSubjectFilter(null, listOf("biology")))
    }
}

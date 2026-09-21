package com.thozhilthunai.app

import com.thozhilthunai.app.data.model.EducationLevel
import com.thozhilthunai.app.domain.DeterministicTextParser
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class TextParserTest {

    private lateinit var parser: DeterministicTextParser

    @Before
    fun setUp() {
        parser = DeterministicTextParser()
    }

    @Test
    fun `parse 10th and Erode and dairy gives correct fields`() {
        val result = parser.parse("I studied 10th in Erode, I like milk work")
        assertEquals(EducationLevel.STANDARD_10, result.education)
        assertEquals("Erode", result.district)
        assertTrue("dairy" in result.interests)
    }

    @Test
    fun `parse 12th gives STANDARD_12`() {
        val result = parser.parse("I completed 12th standard in Salem")
        assertEquals(EducationLevel.STANDARD_12, result.education)
        assertEquals("Salem", result.district)
    }

    @Test
    fun `parse graduate gives GRADUATE`() {
        val result = parser.parse("I am a bsc graduate from Coimbatore")
        assertEquals(EducationLevel.GRADUATE, result.education)
        assertEquals("Coimbatore", result.district)
    }

    @Test
    fun `parse ITI gives ITI_DIPLOMA`() {
        val result = parser.parse("I have an ITI certificate")
        assertEquals(EducationLevel.ITI_DIPLOMA, result.education)
    }

    @Test
    fun `parse SSLC gives STANDARD_10`() {
        val result = parser.parse("I passed SSLC in Madurai")
        assertEquals(EducationLevel.STANDARD_10, result.education)
        assertEquals("Madurai", result.district)
    }

    @Test
    fun `parse poultry interest`() {
        val result = parser.parse("I want to work with poultry farming")
        assertTrue("poultry" in result.interests)
        assertTrue("farming" in result.interests)
    }

    @Test
    fun `parse Tamil keywords - dairy`() {
        val result = parser.parse("நான் பால் வேலை செய்கிறேன்")
        assertTrue("dairy" in result.interests)
    }

    @Test
    fun `parse empty string gives defaults`() {
        val result = parser.parse("")
        assertEquals(EducationLevel.STANDARD_10, result.education)
        assertEquals("", result.district)
        assertTrue(result.interests.isEmpty())
    }

    @Test
    fun `parse multiple interests`() {
        val result = parser.parse("I like dairy, poultry and tailoring work in Erode")
        assertTrue("dairy" in result.interests)
        assertTrue("poultry" in result.interests)
        assertTrue("tailor" in result.interests)
        assertEquals("Erode", result.district)
    }

    @Test
    fun `parse Kanyakumari aliases`() {
        val result = parser.parse("I am from nagercoil and want work")
        assertEquals("Kanyakumari", result.district)
    }
}

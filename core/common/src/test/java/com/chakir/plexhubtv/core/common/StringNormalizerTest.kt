package com.chakir.plexhubtv.core.common

import org.junit.Assert.assertEquals
import org.junit.Test

class StringNormalizerTest {

    @Test
    fun `normalizes accented characters`() {
        assertEquals("EVADES", StringNormalizer.normalizeForSorting("Évadés"))
        assertEquals("CAFE", StringNormalizer.normalizeForSorting("Café"))
        assertEquals("FRANCAIS", StringNormalizer.normalizeForSorting("Français"))
        assertEquals("GARCON", StringNormalizer.normalizeForSorting("Garçon"))
        assertEquals("NOEL", StringNormalizer.normalizeForSorting("Noël"))
    }

    @Test
    fun `normalizes German and Spanish characters`() {
        assertEquals("UBER", StringNormalizer.normalizeForSorting("Über"))
        assertEquals("ESPANA", StringNormalizer.normalizeForSorting("España"))
        assertEquals("ANOS", StringNormalizer.normalizeForSorting("Años"))
    }

    @Test
    fun `strips special characters`() {
        assertEquals("REC", StringNormalizer.normalizeForSorting("[REC]"))
        assertEquals("CUT", StringNormalizer.normalizeForSorting("Cut!"))
        assertEquals("MISSION IMPOSSIBLE", StringNormalizer.normalizeForSorting("Mission: Impossible"))
        assertEquals("DONNIE DARKO", StringNormalizer.normalizeForSorting("Donnie Darko (2001)"))
        assertEquals("BRUCE LEE", StringNormalizer.normalizeForSorting("Bruce Lee - La fureur de vaincre"))
    }

    @Test
    fun `removes leading articles - English`() {
        assertEquals("MATRIX", StringNormalizer.normalizeForSorting("The Matrix"))
        assertEquals("GODFATHER", StringNormalizer.normalizeForSorting("The Godfather"))
        assertEquals("GOOD THE BAD AND THE UGLY", StringNormalizer.normalizeForSorting("The Good, the Bad and the Ugly"))
        assertEquals("AMERICAN", StringNormalizer.normalizeForSorting("An American"))
        assertEquals("DOG", StringNormalizer.normalizeForSorting("A Dog"))
    }

    @Test
    fun `removes leading articles - French`() {
        assertEquals("TOUR EIFFEL", StringNormalizer.normalizeForSorting("La Tour Eiffel"))
        assertEquals("EVADES", StringNormalizer.normalizeForSorting("Les Évadés"))
        assertEquals("PETIT PRINCE", StringNormalizer.normalizeForSorting("Le Petit Prince"))
        assertEquals("HOMME", StringNormalizer.normalizeForSorting("Un Homme"))
        assertEquals("HISTOIRE", StringNormalizer.normalizeForSorting("Une Histoire"))
    }

    @Test
    fun `handles mixed cases`() {
        assertEquals("ETE INDIEN", StringNormalizer.normalizeForSorting("Été indien"))
        assertEquals("EVADES", StringNormalizer.normalizeForSorting("Les Évadés"))
        assertEquals("MISSION IMPOSSIBLE 2", StringNormalizer.normalizeForSorting("Mission: Impossible 2"))
    }

    @Test
    fun `handles empty and whitespace`() {
        assertEquals("", StringNormalizer.normalizeForSorting(""))
        assertEquals("", StringNormalizer.normalizeForSorting("   "))
        assertEquals("ABC", StringNormalizer.normalizeForSorting("  abc  "))
    }

    @Test
    fun `preserves digits and spaces`() {
        assertEquals("300", StringNormalizer.normalizeForSorting("300"))
        assertEquals("28 JOURS PLUS TARD", StringNormalizer.normalizeForSorting("28 jours plus tard"))
        assertEquals("2001 A SPACE ODYSSEY", StringNormalizer.normalizeForSorting("2001: A Space Odyssey"))
    }

    @Test
    fun `real world examples`() {
        // User's specific examples
        assertEquals("CUT", StringNormalizer.normalizeForSorting("Cut!"))
        assertEquals("REC", StringNormalizer.normalizeForSorting("[REC]"))
        
        // More real examples
        assertEquals("AMELIE", StringNormalizer.normalizeForSorting("Le Fabuleux Destin d'Amélie Poulain"))
        assertEquals("INTOUCHABLES", StringNormalizer.normalizeForSorting("Intouchables"))
        assertEquals("ARMEE DES 12 SINGES", StringNormalizer.normalizeForSorting("L'Armée des 12 singes"))
    }

    @Test
    fun `does not remove article if not at start`() {
        // "the" in the middle should stay
        assertEquals("INTO THE WILD", StringNormalizer.normalizeForSorting("Into the Wild"))
        // "Le" at start is removed, but "Le" in middle stays
        assertEquals("MANS LE MANS", StringNormalizer.normalizeForSorting("Le Mans le Mans"))
    }
}

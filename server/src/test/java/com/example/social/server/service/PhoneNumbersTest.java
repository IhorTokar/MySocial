package com.example.social.server.service;

import com.example.social.server.exception.BadRequestException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Різні записи одного номера мають давати однаковий результат. */
class PhoneNumbersTest {

    @Test
    void differentNotationsOfSameNumberAreEqual() {
        String expected = "+380671234567";
        assertEquals(expected, PhoneNumbers.normalize("+380671234567"));
        assertEquals(expected, PhoneNumbers.normalize("+38 (067) 123-45-67"));
        assertEquals(expected, PhoneNumbers.normalize("067 123 45 67"));
        assertEquals(expected, PhoneNumbers.normalize("380671234567"));
        assertEquals(expected, PhoneNumbers.normalize("00380671234567"));
    }

    @Test
    void foreignNumberWithPlusIsKept() {
        assertEquals("+48601234567", PhoneNumbers.normalize("+48 601 234 567"));
    }

    @Test
    void blankMeansNoNumber() {
        assertNull(PhoneNumbers.normalize(null));
        assertNull(PhoneNumbers.normalize("   "));
    }

    @Test
    void rejectsGarbageAndTooShortNumbers() {
        assertThrows(BadRequestException.class, () -> PhoneNumbers.normalize("abc"));
        assertThrows(BadRequestException.class, () -> PhoneNumbers.normalize("12345"));
        assertThrows(BadRequestException.class, () -> PhoneNumbers.normalize("+380 67 123 45 67 89 00"));
    }
}
package tn.iteam.chat.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class PrivateChatKeyGeneratorTest {

    private final PrivateChatKeyGenerator generator = new PrivateChatKeyGenerator();

    @Test
    void shouldGenerateStableKeyRegardlessOfOrder() {
        String a = generator.generate(10L, 5L);
        String b = generator.generate(5L, 10L);
        assertEquals(a, b);
        assertEquals(64, a.length());
    }

    @Test
    void shouldGenerateDifferentKeysForDifferentPairs() {
        String first = generator.generate(1L, 2L);
        String second = generator.generate(1L, 3L);
        assertNotEquals(first, second);
    }
}


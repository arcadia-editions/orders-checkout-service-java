package com.arcadiaeditions.orders.checkout.infrastructure.ids;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class UuidV7OrderIdGeneratorTest {

    UuidV7OrderIdGenerator generator = new UuidV7OrderIdGenerator();

    @Test
    void generatesVersion7Variant2LowercaseCanonicalIds() {
        var id = generator.nextOrderId();
        var uuid = UUID.fromString(id);

        assertEquals(id.toLowerCase(), id);
        assertEquals(uuid.toString(), id);
        assertEquals(7, uuid.version());
        assertEquals(2, uuid.variant());
    }

    @Test
    void embedsUnixMillisAndSortsByTime() throws InterruptedException {
        var before = System.currentTimeMillis();
        var first = generator.nextOrderId();
        var after = System.currentTimeMillis();
        var millis = UUID.fromString(first).getMostSignificantBits() >>> 16;
        assertTrue(millis >= before && millis <= after);

        Thread.sleep(5);
        var second = generator.nextOrderId();
        assertTrue(first.compareTo(second) < 0);
        assertNotEquals(first, second);
    }
}

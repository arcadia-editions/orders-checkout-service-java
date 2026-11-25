package com.arcadiaeditions.orders.checkout.infrastructure.ids;

import com.arcadiaeditions.orders.checkout.core.outbound.ids.OrderIdGenerator;
import java.security.SecureRandom;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** UUIDv7 (RFC 9562): 48-bit unix millis, version 7, variant 2, random rest; lowercase canonical string. */
@Component
public class UuidV7OrderIdGenerator implements OrderIdGenerator {

    private final SecureRandom random = new SecureRandom();

    @Override
    public String nextOrderId() {
        long millis = System.currentTimeMillis();
        long msb = (millis << 16) | 0x7000L | (random.nextLong() & 0x0FFFL);
        long lsb = (random.nextLong() & 0x3FFFFFFFFFFFFFFFL) | 0x8000000000000000L;
        return new UUID(msb, lsb).toString();
    }
}

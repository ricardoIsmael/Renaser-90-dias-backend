package com.renaser.os.shared;

import com.renaser.os.shared.domain.IdGenerator;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Implementacion de produccion de {@link IdGenerator}. Es el unico lugar del backend donde
 * {@code UUID.randomUUID()} es legitimo, igual que {@link SystemClock} es el unico donde lo es
 * {@code Instant.now()}.
 */
@Component
class RandomUuidIdGenerator implements IdGenerator {

    @Override
    public UUID newId() {
        return UUID.randomUUID();
    }
}

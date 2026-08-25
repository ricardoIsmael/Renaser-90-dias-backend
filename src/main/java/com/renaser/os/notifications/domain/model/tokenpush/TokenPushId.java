package com.renaser.os.notifications.domain.model.tokenpush;

import java.util.UUID;

public record TokenPushId(UUID value) {

    public TokenPushId {
        if (value == null) {
            throw new IllegalArgumentException("TokenPushId no puede ser null");
        }
    }

    public static TokenPushId newId() {
        return new TokenPushId(UUID.randomUUID());
    }

    public static TokenPushId of(UUID value) {
        return new TokenPushId(value);
    }

    @Override
    public String toString() {
        return value.toString();
    }
}

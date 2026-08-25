package com.renaser.os.shared.event;

import java.time.Instant;

public interface DomainEvent {

    Instant occurredAt();
}

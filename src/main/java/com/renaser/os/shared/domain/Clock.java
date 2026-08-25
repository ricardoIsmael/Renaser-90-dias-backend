package com.renaser.os.shared.domain;

import java.time.Instant;
import java.time.LocalDate;

public interface Clock {

    Instant now();

    LocalDate today();
}

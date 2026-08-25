package com.renaser.os.habits.infrastructure.adapter.in.rest.radar;

import java.time.Instant;

/** Espejo de lo unico que radar.ts:136-158 (`getLatestRadarEntryTime`) necesita: el timestamp, o null si nunca hubo check-in. */
public record UltimoRadarResponse(Instant createdAt) {
}

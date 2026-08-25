package com.renaser.os.rocks.infrastructure.adapter.in.rest.rocamaestra;

import com.renaser.os.rocks.domain.model.rocamaestra.RocaMaestra;

import java.time.Instant;
import java.util.UUID;

public record RocaMaestraResponse(UUID id, String eje, String objetivo, Instant creadoEn) {

    public static RocaMaestraResponse from(RocaMaestra r) {
        return new RocaMaestraResponse(r.id().value(), r.eje().name(), r.objetivo(), r.creadoEn());
    }
}

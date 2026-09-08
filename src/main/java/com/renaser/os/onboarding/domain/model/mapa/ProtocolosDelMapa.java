package com.renaser.os.onboarding.domain.model.mapa;

import java.util.List;
import java.util.Objects;

/**
 * Los protocolos de reemplazo de UN aprendiz. El manual (§3 V07) pide <b>entre {@value #MIN} y
 * {@value #MAX}</b>: menos de uno no es un sistema, y mas de tres es una lista que nadie recuerda
 * en el momento en que hace falta.
 *
 * <p>Igual que {@link AccionesDelMapa}: es una regla que depende de las OTRAS filas, asi que no
 * puede ser un {@code CHECK} de Postgres y hoy solo vive en el telefono.
 *
 * <p><b>La lista vacia se acepta a proposito.</b> El mapa se llena de a pasos, y V07 puede estar
 * todavia sin tocar cuando se guarda el borrador. El minimo se exige al ACTIVAR (definicion de
 * terminado), no al guardar un paso a medias.
 */
public record ProtocolosDelMapa(List<ProtocoloReemplazoMapa> protocolos) {

    public static final int MIN = 1;
    public static final int MAX = 3;

    public ProtocolosDelMapa {
        Objects.requireNonNull(protocolos, "protocolos es obligatorio");
        if (protocolos.size() > MAX) {
            throw new IllegalArgumentException(
                    "El mapa admite hasta " + MAX + " protocolos de reemplazo, recibidos: " + protocolos.size());
        }
        long distintos = protocolos.stream().map(ProtocoloReemplazoMapa::protocoloId).distinct().count();
        if (distintos != protocolos.size()) {
            throw new IllegalArgumentException("Hay dos protocolos con el mismo protocoloId");
        }
        protocolos = List.copyOf(protocolos);
    }

    public static ProtocolosDelMapa vacio() {
        return new ProtocolosDelMapa(List.of());
    }

    /** La definicion de terminado de V07, que se exige al activar y no al guardar un paso. */
    public boolean completoParaActivar() {
        return protocolos.size() >= MIN;
    }
}

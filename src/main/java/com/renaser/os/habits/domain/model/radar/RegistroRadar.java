package com.renaser.os.habits.domain.model.radar;

import com.renaser.os.shared.domain.UserId;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.experimental.Accessors;

import java.time.Instant;
import java.util.Objects;

/** Check-in "Codigo Renaser" (tabla `registros_radar`) — append-only, sin edicion ni borrado. */
@Getter
@Accessors(fluent = true)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@EqualsAndHashCode(of = "id")
public final class RegistroRadar {

    /** radar.ts:65 (RADAR_TEXT_MAX_LENGTH) y schema.ts del backend viejo (daily-checkin) — mismo limite en las 3 capas. */
    public static final int TEXTO_MAX_LENGTH = 2_000;

    /** nivel_energia (CHECK BETWEEN 1 AND 10 en el baseline, linea ~630) y energyLevel en radar.ts/schema.ts viejo. */
    public static final int NIVEL_ENERGIA_MIN = 1;
    public static final int NIVEL_ENERGIA_MAX = 10;

    private final RegistroRadarId id;
    private final UserId participanteId;
    private final String queHago;
    private final String quePienso;
    private final String queSiento;
    private final int nivelEnergia;
    private final String queEvito;
    private final Instant creadoEn;

    public static RegistroRadar registrar(UserId participanteId, String queHago, String quePienso, String queSiento,
                                           int nivelEnergia, String queEvito, Instant ahora) {
        Objects.requireNonNull(participanteId, "participanteId es obligatorio");
        if (nivelEnergia < NIVEL_ENERGIA_MIN || nivelEnergia > NIVEL_ENERGIA_MAX) {
            throw new IllegalArgumentException(
                    "nivelEnergia fuera de rango " + NIVEL_ENERGIA_MIN + ".." + NIVEL_ENERGIA_MAX + ": " + nivelEnergia);
        }
        return new RegistroRadar(RegistroRadarId.newId(), participanteId, requireNotBlank(queHago, "queHago"),
                requireNotBlank(quePienso, "quePienso"), requireNotBlank(queSiento, "queSiento"), nivelEnergia,
                requireNotBlank(queEvito, "queEvito"), ahora);
    }

    /** Solo para el adaptador de persistencia. */
    public static RegistroRadar rehydrate(RegistroRadarId id, UserId participanteId, String queHago,
                                           String quePienso, String queSiento, int nivelEnergia, String queEvito,
                                           Instant creadoEn) {
        return new RegistroRadar(id, participanteId, queHago, quePienso, queSiento, nivelEnergia, queEvito, creadoEn);
    }

    private static String requireNotBlank(String value, String campo) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(campo + " es obligatorio");
        }
        String recortado = value.trim();
        if (recortado.length() > TEXTO_MAX_LENGTH) {
            throw new IllegalArgumentException(campo + " supera el maximo de " + TEXTO_MAX_LENGTH + " caracteres");
        }
        return recortado;
    }

    @Override
    public String toString() {
        return "RegistroRadar[" + id + ", " + participanteId + ", " + creadoEn + "]";
    }
}

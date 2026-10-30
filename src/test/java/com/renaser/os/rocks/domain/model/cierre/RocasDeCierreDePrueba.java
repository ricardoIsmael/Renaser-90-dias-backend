package com.renaser.os.rocks.domain.model.cierre;

import com.renaser.os.rocks.domain.model.rocadiaria.ColorPareto;
import com.renaser.os.rocks.domain.model.rocadiaria.RocaDiaria;
import com.renaser.os.rocks.domain.model.rocadiaria.RocaDiariaId;
import com.renaser.os.rocks.domain.model.rocamaestra.EjeObjetivo;
import com.renaser.os.shared.domain.UserId;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

/** Rocas armadas a mano para las pruebas del cierre del día (spec 017): sin Spring ni base. */
public final class RocasDeCierreDePrueba {

    public static final UserId ANA = UserId.of(UUID.fromString("00000000-0000-0000-0000-0000000000a1"));
    public static final Instant ANTES = Instant.parse("2026-10-28T14:00:00Z");

    private RocasDeCierreDePrueba() {
    }

    public static RocaDiaria pendiente(LocalDate fecha, int posicion, String titulo, LocalTime hora) {
        return roca(fecha, posicion, titulo, hora, false, null, 0, null);
    }

    public static RocaDiaria cumplida(LocalDate fecha, int posicion, String titulo, LocalTime hora, int puntos) {
        return roca(fecha, posicion, titulo, hora, true, ANTES, puntos, null);
    }

    public static RocaDiaria conCierre(RocaDiaria base, CierreDeAccion cierre) {
        return roca(base.fecha(), base.posicion(), base.titulo(), base.horaInicio(), base.completada(), base.completadaEn(),
                base.puntosOtorgados(), cierre, base.id());
    }

    public static RespuestaDeCierre respuesta(MotivoDeAccionPendiente motivo, PlanDeManana manana, String nota) {
        return new RespuestaDeCierre(motivo, manana, nota);
    }

    private static RocaDiaria roca(LocalDate fecha, int posicion, String titulo, LocalTime hora, boolean completada,
                                   Instant completadaEn, int puntos, CierreDeAccion cierre) {
        return roca(fecha, posicion, titulo, hora, completada, completadaEn, puntos, cierre, new RocaDiariaId(UUID.randomUUID()));
    }

    private static RocaDiaria roca(LocalDate fecha, int posicion, String titulo, LocalTime hora, boolean completada,
                                   Instant completadaEn, int puntos, CierreDeAccion cierre, RocaDiariaId id) {
        return RocaDiaria.rehydrate(id, ANA, fecha, posicion, titulo, null, ColorPareto.paraPosicion(posicion), 5, false,
                EjeObjetivo.CUERPO, null, hora, null, List.of(), completada, completadaEn, puntos, ANTES, ANTES, cierre);
    }
}

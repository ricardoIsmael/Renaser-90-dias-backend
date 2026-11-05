package com.renaser.os.habits.application.ports.out.carrera;

import com.renaser.os.shared.domain.UserId;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/** Lecturas de las carreras para la agenda del día (FR-029) y para las fichas del acompañamiento (FR-044). Solo lectura. */
public interface LeerCarrerasPort {

    /** Cuántas carreras tiene la persona ese día y la suma de los km de las que cuentan. */
    CarrerasDelDia delDia(UserId participante, LocalDate fecha);

    /** El resumen de cada carrera de esas personas entre dos fechas (inclusive), en orden de inicio. */
    List<ResumenDeCarrera> entre(Collection<UserId> participantes, LocalDate desde, LocalDate hasta);

    record CarrerasDelDia(int cantidad, BigDecimal kilometros) {
    }

    record ResumenDeCarrera(UserId participanteId, LocalDate fecha, UUID carreraId, BigDecimal kilometros, int duracionActivaSeg,
                            int ritmoSegPorKm, boolean porRevisar, boolean cuentaParaElHabito, boolean tieneRuta) {
    }
}

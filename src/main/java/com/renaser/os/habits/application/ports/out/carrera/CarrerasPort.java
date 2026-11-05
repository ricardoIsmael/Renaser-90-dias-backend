package com.renaser.os.habits.application.ports.out.carrera;

import com.renaser.os.habits.domain.model.carrera.Carrera;
import com.renaser.os.habits.domain.model.carrera.EfectoEnElHabito;
import com.renaser.os.habits.domain.model.carrera.RutaDeCarrera;
import com.renaser.os.shared.domain.UserId;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

/** La tabla {@code carreras} (V170) para el camino de escritura de la spec 042. */
public interface CarrerasPort {

    /**
     * Guarda la carrera si su {@code (participante, carreraId)} no existe. {@code false} si ya existía: con otra transacción en
     * vuelo que guarda la misma, espera a que termine (lo resuelve el índice único) y responde {@code false}.
     */
    boolean guardarSiEsNueva(Carrera carrera);

    Optional<Carrera> de(UserId participante, UUID carreraId);

    /** Anota lo que la carrera hizo con el hábito, en la misma transacción que la guardó. */
    void anotarEfecto(UUID id, EfectoEnElHabito efecto);

    /** La suma de los km de las carreras que cuentan, de esa persona y ese día (con la que se acaba de guardar). */
    BigDecimal kilometrosQueCuentan(UserId participante, LocalDate fecha);

    /** La ruta guardada de una carrera de esa persona; vacío si no hay. */
    Optional<RutaDeCarrera> rutaDe(UserId participante, UUID carreraId);
}

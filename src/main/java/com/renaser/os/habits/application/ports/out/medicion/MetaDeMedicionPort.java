package com.renaser.os.habits.application.ports.out.medicion;

import com.renaser.os.habits.domain.model.habito.HabitoId;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

/**
 * La meta diaria de un hábito medible (spec 037): {@code habitos.meta_medicion}. Vacía = la del programa
 * ({@code PoliticaKilometros.META_POR_DEFECTO_KM}); quién decide cuál rige es la política, no el puerto.
 *
 * <p>Va aparte de {@code Habito} a propósito: es un solo dato que solo lee el cierre automático, la agenda de hoy y el panel de
 * Administración, y meterlo en el agregado habría cambiado su constructor y cada lugar que lo arma.
 */
public interface MetaDeMedicionPort {

    /** La meta fijada por Administración para ese hábito, o vacío si usa la del programa. */
    Optional<BigDecimal> metaDe(HabitoId habitoId);

    /** Fija la meta; {@code null} vuelve a la del programa. */
    void fijar(HabitoId habitoId, BigDecimal meta, Instant ahora);
}

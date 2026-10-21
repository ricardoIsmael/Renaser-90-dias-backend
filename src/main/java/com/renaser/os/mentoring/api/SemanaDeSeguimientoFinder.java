package com.renaser.os.mentoring.api;

import com.renaser.os.shared.domain.UserId;

import java.time.LocalDate;
import java.util.UUID;

/**
 * "La semana de esta persona, entre estas dos fechas": lo que la ficha del mentor y la de administración ya muestran,
 * ahora también para la ficha del líder (spec 005, R-2).
 *
 * <p><b>Por qué existe.</b> El líder mira la ventana del SEMÁFORO (los siete días vigentes, o una semana de sábado a
 * viernes), no la de lunes a domingo del mentor. En vez de armar una segunda semana, {@code leadership} pide la misma por acá con su propia ventana: mismas
 * obligaciones históricas, mismas entregas y mismo motor, así que el líder y el administrador ven lo mismo del mismo
 * día (SC-002).
 *
 * <p><b>No autoriza</b>, igual que {@code SemaforoFinder}: quien llama ya estableció que el actor puede ver a esa
 * persona. Este puerto responde por el id que le dan.
 */
public interface SemanaDeSeguimientoFinder {

    /**
     * @param grupoId el grupo desde el que se mira; viaja en la respuesta y puede ser null
     * @param desde   primer día, inclusivo, fecha local de la persona ya resuelta por quien llama
     * @param hasta   último día, inclusivo
     * @throws java.util.NoSuchElementException si la persona no existe
     * @throws IllegalArgumentException         si {@code desde} es posterior a {@code hasta}
     */
    SemanaDelAlumno semanaDe(UserId personaId, UUID grupoId, LocalDate desde, LocalDate hasta);
}

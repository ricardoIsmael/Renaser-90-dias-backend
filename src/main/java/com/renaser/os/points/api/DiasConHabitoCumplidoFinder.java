package com.renaser.os.points.api;

import com.renaser.os.shared.domain.UserId;

import java.time.LocalDate;
import java.util.List;

/**
 * "Qué días cumplió esta persona al menos un hábito."
 *
 * <p><b>Por qué vive en {@code points.api} y no en {@code habits.api}.</b> Por el mismo motivo
 * documentado en {@link HabitosDelDiaFinder} y {@code PorcentajeRocasFinder}: {@code habits} ya
 * depende de {@code points} para otorgar puntos, así que {@code points} no puede depender de
 * {@code habits} en la otra dirección sin crear un ciclo que Spring Modulith rechaza. Se aplica
 * inversión de dependencia — el consumidor declara lo que necesita y el proveedor
 * ({@code habits.application.services.DiasConHabitoCumplidoFinderService}) lo implementa.
 *
 * <p><b>Un día, no un hábito.</b> Devuelve <b>fechas distintas</b>, no registros: si alguien
 * cumplió siete hábitos el martes, el martes aparece una sola vez. Esa deduplicación es la regla
 * de negocio de la racha —<i>"es por día, no por hábito"</i>, pedido del dueño del producto el
 * 2026-09-14— y vive acá, en el contrato, para que ningún consumidor pueda saltársela contando
 * filas.
 *
 * <p><b>No recibe {@code actorId}, a propósito</b> — mismo criterio que
 * {@link HabitosDelDiaFinder}: es una llamada de módulo a módulo que quien invoca ya autorizó.
 */
public interface DiasConHabitoCumplidoFinder {

    /**
     * Fechas <b>locales del participante</b>, entre {@code desde} y {@code hasta} inclusive, en las
     * que hubo al menos un hábito en estado cumplido.
     *
     * <p>Quien llama resuelve la zona horaria antes de pedir ({@code .claude/rules/02} §1): acá ya
     * llegan fechas locales, no instantes.
     *
     * @return orden ascendente y sin repetidos; lista vacía si no hubo nada, nunca {@code null}.
     */
    List<LocalDate> entre(UserId participanteId, LocalDate desde, LocalDate hasta);
}

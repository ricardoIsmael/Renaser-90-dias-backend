package com.renaser.os.rag.application.ports.out.habitos;

import com.renaser.os.shared.domain.UserId;

import java.time.LocalDate;
import java.util.List;

/**
 * Los habitos que le tocaban a la persona y vencieron sin cumplirse, entre dos fechas (D-177,
 * {@code consultar_desvio_de_la_semana}). El adaptador delega en
 * {@code habits.api.ObligacionesHistoricasFinder} (D-41): el registro de cada dia es un snapshot
 * historico, asi que reprogramar hoy no cambia lo que vencio la semana pasada.
 *
 * <p><b>Que cuenta.</b> Solo lo que {@code habits} marca como {@code vencidoSinCumplir} (fallido o
 * expirado) y que ese dia no era opcional. Un dia sin registro no es incumplimiento (plan.md §7).
 */
public interface ConsultarHabitosVencidosPort {

    /** @param desde inclusivo, @param hasta inclusivo, fechas locales de la persona ya resueltas */
    List<HabitoVencido> vencidosEntre(UserId participanteId, LocalDate desde, LocalDate hasta);

    record HabitoVencido(LocalDate fecha, String titulo) {
    }
}

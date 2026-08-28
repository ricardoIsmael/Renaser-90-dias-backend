package com.renaser.os.habits.application.ports.in.audioterapia;

import com.renaser.os.shared.domain.UserId;

import java.util.Optional;

public interface ConsultarAudioterapiaSemanalUseCase {

    EstadoAudioterapia consultar(UserId actorId);

    /**
     * {@code semanaActual}/{@code audio} vacios = el hábito todavía no desbloqueó (día de
     * programa anterior al día de inicio) o el catálogo no tiene contenido para esa semana
     * todavía (mismo criterio "esperando contenido" que Espíritu).
     */
    record EstadoAudioterapia(Optional<Integer> semanaActual, Optional<AudioResuelto> audio) {

        public record AudioResuelto(String titulo, String url, Integer diaSiguienteCambio) {
        }
    }
}

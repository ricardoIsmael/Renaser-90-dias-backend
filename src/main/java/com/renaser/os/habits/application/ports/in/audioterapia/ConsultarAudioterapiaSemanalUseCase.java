package com.renaser.os.habits.application.ports.in.audioterapia;

import com.renaser.os.shared.domain.UserId;

public interface ConsultarAudioterapiaSemanalUseCase {

    EstadoAudioterapia consultar(UserId actorId);

    /** Variantes cerradas: o hay un audio resuelto para la semana en curso, o no hay nada que escuchar todavía. */
    sealed interface EstadoAudioterapia permits AudioDeLaSemana, EsperandoContenido {
    }

    /** Audio que le toca al aprendiz hoy; {@code url} viene firmada (o {@code null} si la ruta está vacía). */
    record AudioDeLaSemana(int semanaActual, String titulo, String url, Integer diaSiguienteCambio)
            implements EstadoAudioterapia {
    }

    /**
     * El hábito todavía no desbloqueó (día de programa anterior al día de inicio) o el catálogo no
     * tiene contenido para esa semana todavía (mismo criterio "esperando contenido" que Espíritu).
     */
    record EsperandoContenido() implements EstadoAudioterapia {
    }
}

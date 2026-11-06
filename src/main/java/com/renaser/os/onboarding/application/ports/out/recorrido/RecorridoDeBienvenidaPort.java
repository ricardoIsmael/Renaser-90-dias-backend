package com.renaser.os.onboarding.application.ports.out.recorrido;

import com.renaser.os.onboarding.application.ports.in.recorrido.RecorridoDeBienvenidaUseCase.ComoTermino;
import com.renaser.os.shared.domain.UserId;

import java.time.Instant;
import java.util.Optional;

/** Spec 045: la fila de `recorrido_de_bienvenida` de cada persona. */
public interface RecorridoDeBienvenidaPort {

    Optional<FilaDelRecorrido> de(UserId persona);

    /** Guarda el paso si el recorrido no terminó (crea la fila si no existe). */
    void guardarPaso(UserId persona, int paso, Instant ahora);

    /** Lo termina si todavía no terminó (crea la fila si no existe). */
    void terminar(UserId persona, ComoTermino como, Instant ahora);

    record FilaDelRecorrido(int paso, Instant terminadoEn, ComoTermino como) {
    }
}

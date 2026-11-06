package com.renaser.os.onboarding.application.ports.in.recorrido;

import com.renaser.os.shared.domain.UserId;

import java.time.Instant;

/**
 * Spec 045 (D-330): el recorrido de bienvenida que lleva a la persona por Hoy, Plan y Entrenar hasta su primer post en el Muro.
 *
 * <p>El servidor solo recuerda en qué paso va y si ya terminó; los textos, el foco y el compositor del Muro son de la app.
 */
public interface RecorridoDeBienvenidaUseCase {

    /** Último paso del recorrido (el del Muro). */
    int ULTIMO_PASO = 5;

    VistaDelRecorrido de(UserId persona);

    /** Guarda el paso al que llegó (1 a 5). No reabre un recorrido terminado. */
    VistaDelRecorrido avanzar(UserId persona, int paso);

    /** Lo da por terminado. Idempotente: terminar dos veces conserva la primera. */
    VistaDelRecorrido terminar(UserId persona, ComoTermino como);

    enum ComoTermino {
        /** Publicó su primer post desde el recorrido. */
        PUBLICO,
        /** Llegó al final sin el paso del Muro (ya había publicado o el Muro no está disponible). */
        LISTO,
        /** Lo saltó. */
        SALTO,
        /** La cuenta existía antes del recorrido (decisión D2: solo cuentas nuevas). Lo pone la migración, nunca la app. */
        CUENTA_ANTERIOR
    }

    /**
     * @param mostrar si la app tiene que mostrarlo solo: no terminado, con programa propio activado y la cuenta sin suspender
     *                (D4: el personal lo ve solo si hace su programa propio)
     * @param paso    el paso en el que va (1 si nunca empezó)
     */
    record VistaDelRecorrido(boolean mostrar, int paso, Instant terminadoEn, ComoTermino como) {
    }
}

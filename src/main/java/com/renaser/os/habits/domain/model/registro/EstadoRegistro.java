package com.renaser.os.habits.domain.model.registro;

/**
 * Maquina de estados del registro diario de un habito (espejo de `estado_registro`
 * en el baseline SQL). Fuente vieja: HabitStatus de Prisma (PENDING/IN_PROGRESS/
 * COMPLETED/FAILED/EXPIRED) — ver docs/MODULO_HABITS.md paso 0.
 *
 * <pre>
 *   PENDIENTE --iniciar()--&gt; EN_CURSO   (solo habitos BLOQUEO/racha sin celular)
 *   PENDIENTE --completar()--&gt; COMPLETADO
 *   EN_CURSO  --completar()--&gt; COMPLETADO
 *   PENDIENTE --expirar()--&gt; EXPIRADO   (vencio la ventana de entrega, sin penalizacion)
 *   EN_CURSO  --expirar()--&gt; EXPIRADO   (racha huerfana vencida)
 *   EN_CURSO  --marcarFallido()--&gt; FALLIDO (Santuario roto: SALIDA_TEMPRANA/VIOLACION_APP_USADA)
 *   EN_CURSO  --liberar()--&gt; PENDIENTE  (hito parcial de racha sin celular, mismo dia)
 * </pre>
 *
 * COMPLETADO/FALLIDO/EXPIRADO son terminales: ninguna transicion los abandona
 * (service.ts:11, "FAILED and EXPIRED tracks cannot be completed").
 */
public enum EstadoRegistro {
    PENDIENTE,
    EN_CURSO,
    COMPLETADO,
    FALLIDO,
    EXPIRADO;

    public boolean esTerminal() {
        return this == COMPLETADO || this == FALLIDO || this == EXPIRADO;
    }

    public boolean puedeIniciar() {
        return this == PENDIENTE;
    }

    /**
     * EXPIRADO tambien se puede completar, y esa es la parte que sorprende.
     *
     * <p>Antes no: pasada la ventana, el registro quedaba EXPIRADO y la app respondia
     * {@code 409 El habito expiro -- no se puede completar}. El dueno del proyecto lo pidio al
     * reves y tiene razon: registrar tarde es informacion, y perderla no ayuda a nadie. Alguien
     * que se desperto a las 10 y lo anota a las 11 hizo el habito; lo unico que no hizo fue
     * llegar a tiempo, y eso ya se cobra donde corresponde -- {@code ResultadoOtorgamiento}
     * devuelve 0 puntos en fase EXPIRADO. Bloquear ademas el registro cobra dos veces.
     *
     * <p>FALLIDO sigue fuera, y a proposito: lo marca el barrido nocturno cuando el dia CIERRA.
     * Un dia cerrado es un veredicto, y dejar completarlo despues seria poder reescribir el
     * pasado -- la misma linea que ya sostiene que una pausa no borra lo ya vencido.
     */
    public boolean puedeCompletarse() {
        return this == PENDIENTE || this == EN_CURSO || this == EXPIRADO;
    }

    public boolean puedeExpirar() {
        return this == PENDIENTE || this == EN_CURSO;
    }

    public boolean puedeMarcarseFallido() {
        return this == PENDIENTE || this == EN_CURSO;
    }
}

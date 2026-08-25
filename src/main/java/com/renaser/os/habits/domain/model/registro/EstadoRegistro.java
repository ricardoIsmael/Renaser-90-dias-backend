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

    public boolean puedeCompletarse() {
        return this == PENDIENTE || this == EN_CURSO;
    }

    public boolean puedeExpirar() {
        return this == PENDIENTE || this == EN_CURSO;
    }

    public boolean puedeMarcarseFallido() {
        return this == PENDIENTE || this == EN_CURSO;
    }
}

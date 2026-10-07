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
 *   PENDIENTE --expirar()--&gt; EXPIRADO   (termino su dia local, E-534; sin penalizacion)
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
     * PENDIENTE y EN_CURSO se pueden completar; EXPIRADO, FALLIDO y COMPLETADO no.
     *
     * <p><b>Corregido 2026-10-06 (D-259, E-573).</b> Aca decia que EXPIRADO <i>tambien</i> se podia completar,
     * con este argumento: «pasada la ventana, el registro quedaba EXPIRADO y la app respondia 409. Registrar tarde
     * es informacion; {@code ResultadoOtorgamiento} ya devuelve 0 puntos en fase EXPIRADO, y bloquear ademas el
     * registro cobra dos veces». El argumento sigue valiendo, pero para el MISMO dia: desde E-534 un registro solo
     * pasa a EXPIRADO cuando su dia local ya termino (el barrido de {@link CorteDeExpiracion}, o
     * {@link RegistroHabito#liberar} de una racha de un dia pasado). Pasada la hora y dentro del dia, el registro
     * sigue PENDIENTE y se completa con menos puntos o ninguno. EXPIRADO quiere decir «era de un dia que ya
     * cerro», y la regla confirmada por el dueño (2026-10-06) es que un habito de un dia que ya termino no se
     * registra. Dejar EXPIRADO completable permitia anotar hoy los habitos de cualquier dia anterior.
     *
     * <p>FALLIDO sigue fuera por lo mismo: lo marca el Santuario roto, y un veredicto no se reescribe.
     */
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

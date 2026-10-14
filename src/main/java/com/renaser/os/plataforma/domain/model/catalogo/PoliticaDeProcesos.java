package com.renaser.os.plataforma.domain.model.catalogo;

/**
 * Qué hacen las tareas programadas y los oyentes de una funcionalidad mientras está apagada (spec 002, FR-001;
 * columna «Proceso» del catálogo). Lo aplica cada proceso al consultar {@code Interruptores.prendida}.
 */
public enum PoliticaDeProcesos {
    /** No actúa y no vuelve a intentarlo: lo de ese turno se pierde (un aviso de hábito de una hora que ya pasó). */
    SALTA,
    /** No actúa ahora y lo intenta de nuevo después (el podio de los lunes, una bienvenida que espera en el outbox). */
    POSPONE,
    /** No tiene procesos, o siguen aunque esté apagada (el corte del ranking es dato; la purga de cuentas es legal). */
    NO_APLICA
}

package com.renaser.os.habits.api;

import com.renaser.os.shared.domain.UserId;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Una obligación de hábito de un día concreto, con la programación que tenía ESE día.
 *
 * <p>Sale de {@code registros_habito}, que es un snapshot histórico y no una proyección del
 * catálogo de hoy: guarda su propio {@code dia_programa} y {@code tipo_dia}. Es la única forma
 * de que reprogramar un hábito hoy no reescriba la semana pasada del alumno (plan.md §7).
 *
 * @param registroId        identidad de la obligación. Es lo que deduplica una entrega.
 * @param requiereEvidencia si el hábito exigía archivo. No todo hábito se acredita subiendo algo.
 * @param opcional          si ese día no era exigible. No entra al denominador de la evaluación.
 * @param claveSistema      la clave fija del hábito del catálogo ({@code habitos.clave_sistema}), o {@code null} si
 *                          no tiene. Sirve para reconocer a un hábito por lo que es y no por su título, que se
 *                          renombra desde el panel.
 * @param respuestaTexto    lo que la persona escribió al cumplirlo («¿Qué sentiste?», el resumen de la Pastilla), o
 *                          {@code null} si no escribió nada. Es evidencia: la leen la ficha del mentor, la de
 *                          administración y la del líder (spec 005). Nunca entra a un cálculo.
 */
public record ObligacionHabito(UUID registroId, UserId participanteId, LocalDate fecha, int diaPrograma,
                                String titulo, EstadoObligacion estado, boolean requiereEvidencia,
                                boolean opcional, String claveSistema, String respuestaTexto) {

    /**
     * La clave del POST DIARIO EN COMUNIDAD ({@code V24}). Vive en la API porque la necesitan dos módulos:
     * {@code habits} (su política) y {@code mentoring}, que liga la publicación del Muro a la obligación (E-628).
     */
    public static final String CLAVE_POST_DIARIO_COMUNIDAD = "COMMUNITY_POST";

    /** Sin clave de sistema ni respuesta: la forma de los llamadores anteriores a E-628. */
    public ObligacionHabito(UUID registroId, UserId participanteId, LocalDate fecha, int diaPrograma, String titulo,
                            EstadoObligacion estado, boolean requiereEvidencia, boolean opcional) {
        this(registroId, participanteId, fecha, diaPrograma, titulo, estado, requiereEvidencia, opcional, null, null);
    }

    /** Si este hábito se cumple publicando en el Muro (el post diario): su evidencia es la publicación. */
    public boolean seCumplePublicandoEnElMuro() {
        return CLAVE_POST_DIARIO_COMUNIDAD.equals(claveSistema);
    }

    /** Cuenta para la evaluación: exigía evidencia y no era opcional. */
    public boolean exigible() {
        return requiereEvidencia && !opcional;
    }
}

package com.renaser.os.habits.domain.model.preferencia;

/**
 * Cuantos minutos antes de la hora de un habito suena su recordatorio
 * ({@code minutos_recordatorio}) y que valores puede tener (PLN-09 del e2e, 2026-09-27).
 *
 * <p><b>El rango es el que ya guarda la base, no una regla nueva.</b> La columna es
 * {@code smallint} con {@code CHECK (minutos_recordatorio >= 0)} en {@code preferencias_horario} (V1)
 * y en {@code horarios_habito_por_fecha} (V37). El dominio no la miraba: un -5 lo frenaba el CHECK, y
 * un 99999, que no entra en un {@code smallint}, el mapper lo convertia con {@code shortValue()} en un
 * numero negativo que tambien frenaba el CHECK. Los dos volvian como 409 «La operacion entra en
 * conflicto con datos que ya existen», que no le dice nada a nadie. Ahora se rechazan antes de
 * escribir, con un 400 que dice el rango.
 *
 * <p>La app deja elegir de 0 («a la hora») a 1440 (un dia, {@code etiquetaDeAntelacion.ts}). Si el
 * servidor tiene que acotar a eso es una pregunta abierta para el dueño (D-216): no se inventa aca.
 */
public final class AntelacionDelRecordatorio {

    /** 0 es «a la hora»: la app lo manda asi. */
    public static final int MINUTOS_MINIMOS = 0;

    /** El tope de un {@code smallint}, el tipo de la columna. */
    public static final int MINUTOS_MAXIMOS = Short.MAX_VALUE;

    private AntelacionDelRecordatorio() {
    }

    /**
     * {@code null} sigue valiendo: sin minutos elegidos (recordatorio apagado, o la antelacion global).
     *
     * @throws IllegalArgumentException si los minutos quedan fuera de lo que la base puede guardar
     */
    public static Integer requireDentroDelRango(Integer minutos) {
        if (minutos != null && (minutos < MINUTOS_MINIMOS || minutos > MINUTOS_MAXIMOS)) {
            throw new IllegalArgumentException("Los minutos del recordatorio deben estar entre " + MINUTOS_MINIMOS
                    + " y " + MINUTOS_MAXIMOS + "; llegaron " + minutos);
        }
        return minutos;
    }
}

package com.renaser.os.rag.domain.model.seguridad;

/**
 * Veredicto de la compuerta de seguridad para un turno de conversacion: los dos ejes juntos.
 *
 * <p><b>Por que vive en el dominio y no en el adaptador.</b> Mismo criterio que
 * {@code Evidencia.registrarIntentoFallido()}, que guarda la regla de los tres intentos en el
 * agregado y no en el adaptador de IA: una garantia de seguridad tiene que poder probarse sin
 * Spring y sin base de datos. Una precedencia escrita en un prompt es un sesgo fuerte; una
 * escrita aca es un invariante con prueba.
 *
 * <p><b>La regla de monotonia.</b> Una vez que una senal de riesgo subio, ninguna capa posterior
 * la baja sola. {@link #combinar(EvaluacionRiesgo)} es el camino normal y solo sabe subir. Bajar
 * exige pasar por {@link #rebajarCon(EvaluacionRiesgo, String)}, que obliga a dejar escrito el
 * motivo. No es una promesa de comportamiento: no hay forma de bajar el nivel sin nombrar por que.
 *
 * <p><b>Lo que este tipo todavia no hace.</b> No mapea a un modo de respuesta. Esa tabla
 * (que combinacion apaga las herramientas, cual exige mentor, cual entra en crisis) es una regla
 * de negocio sin confirmar, y CLAUDE.MD §0.6 prohibe rellenarla con supuestos. Se agrega cuando
 * el dueno del producto y quien firme los criterios clinicos la definan.
 *
 * <p><b>Dependencia de datos que hoy no existe (D-80).</b> Cuando se escriba ese mapeo, el camino
 * de crisis va a necesitar dos datos que el backend todavia no tiene de forma confiable: si la
 * persona es menor de edad, y en que pais esta, para elegir el recurso de crisis correcto entre
 * Peru, Bolivia y Colombia. Hoy la edad se valida solo en el formulario del movil, que es una
 * cortesia y no un control, y fecha de nacimiento y pais viven como respuestas de texto libre en
 * {@code respuestas_onboarding}, sin columna con tipo ni validacion de servidor. Eso hay que
 * resolverlo antes de escribir el mapeo, no despues.
 */
public record EvaluacionRiesgo(NivelRiesgo riesgo, Severidad severidad) {

    public EvaluacionRiesgo {
        if (riesgo == null) {
            throw new IllegalArgumentException("El nivel de riesgo es obligatorio");
        }
        if (severidad == null) {
            throw new IllegalArgumentException("La severidad es obligatoria");
        }
    }

    public static EvaluacionRiesgo de(NivelRiesgo riesgo, Severidad severidad) {
        return new EvaluacionRiesgo(riesgo, severidad);
    }

    /** El punto de partida de un turno sin senales: ningun riesgo, severidad baja. */
    public static EvaluacionRiesgo sinSenales() {
        return new EvaluacionRiesgo(NivelRiesgo.NINGUNO, Severidad.BAJA);
    }

    /**
     * Suma una evaluacion nueva a esta, quedandose con el maximo de cada eje por separado.
     *
     * <p>Los dos ejes se combinan de forma independiente a proposito: una segunda lectura puede
     * subir la severidad sin tocar el riesgo, o al reves. Este metodo nunca devuelve un valor
     * menor al que ya habia — esa es la monotonia.
     */
    public EvaluacionRiesgo combinar(EvaluacionRiesgo nueva) {
        if (nueva == null) {
            throw new IllegalArgumentException("La evaluacion nueva es obligatoria");
        }
        return new EvaluacionRiesgo(
                riesgo.compareTo(nueva.riesgo()) >= 0 ? riesgo : nueva.riesgo(),
                severidad.compareTo(nueva.severidad()) >= 0 ? severidad : nueva.severidad());
    }

    /**
     * La unica puerta para bajar un nivel ya elevado. Exige un motivo no vacio, que es la
     * "evidencia nueva y suficiente" del contrato: sin nombrarla, no se baja nada.
     *
     * <p>Si la evaluacion nueva no baja ningun eje, se comporta igual que
     * {@link #combinar(EvaluacionRiesgo)} y el motivo queda sin usar — rebajar algo que no baja
     * no es un error.
     */
    public EvaluacionRiesgo rebajarCon(EvaluacionRiesgo nueva, String motivo) {
        if (nueva == null) {
            throw new IllegalArgumentException("La evaluacion nueva es obligatoria");
        }
        if (motivo == null || motivo.isBlank()) {
            throw new IllegalArgumentException("Bajar un nivel de riesgo exige un motivo explicito");
        }
        return nueva;
    }

    /** Peligro inmediato. Manda el modo crisis sin importar la severidad. */
    public boolean esCritico() {
        return riesgo == NivelRiesgo.CRITICO;
    }

    /** Hay algo que atender, por cualquiera de los dos ejes. */
    public boolean requiereAtencion() {
        return riesgo != NivelRiesgo.NINGUNO || severidad != Severidad.BAJA;
    }
}

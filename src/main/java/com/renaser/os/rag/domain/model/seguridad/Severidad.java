package com.renaser.os.rag.domain.model.seguridad;

/**
 * Cuanto esta sufriendo la persona. Es el segundo eje de la compuerta de seguridad, junto a
 * {@link NivelRiesgo}.
 *
 * <p>Gobierna el <b>grado de escalamiento</b> hacia un humano, no el modo de respuesta. Esa
 * division es la que evita el disparo de mas: la intensidad del sufrimiento es severidad, no
 * riesgo. Alguien que escribe que no puede mas con un habito tiene severidad, no peligro, y
 * contestarle con recursos de emergencia hace que silencie al agente — y entonces el agente ya
 * no esta el dia que si hace falta.
 *
 * <p>El orden de declaracion importa: {@link #compareTo} lo usa la regla de monotonia de
 * {@link EvaluacionRiesgo}. De menor a mayor.
 */
public enum Severidad {

    /** Molestia o frustracion corriente, del tipo que produce cualquier programa exigente. */
    BAJA,

    /** Malestar sostenido que conviene que un mentor conozca. */
    MODERADA,

    /** Sufrimiento intenso. Amerita escalar a un humano aunque el riesgo sea NINGUNO. */
    ALTA
}

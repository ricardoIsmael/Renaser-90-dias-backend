package com.renaser.os.community.domain.model.celula;

/**
 * En que momento de su vida esta un grupo, mirado un dia concreto.
 *
 * <p>No es una columna: se deriva del periodo cada vez que alguien pregunta. Guardarlo obligaria
 * a un job que lo moviera a medianoche, y el dia que ese job no corriera el grupo se quedaria
 * mintiendo (constitution.md: "derivar estados del calendario, no incrementar contadores").
 *
 * <p>{@link #SIN_PERIODO} no es un estado a medias ni un dato faltante: es lo que son todos los
 * grupos anteriores a V48, y significa que ese grupo no caduca. Distinguirlo de {@link #VIGENTE}
 * importa porque la pantalla no puede prometerle al administrador una fecha de cierre que nadie
 * escribio.
 */
public enum EstadoGrupo {

    /** Todavia no arranco: existe, se puede componer, pero hoy no da acceso ni chat. */
    PROGRAMADO,

    /** Corriendo hoy. El ultimo dia del periodo entra entero. */
    VIGENTE,

    /** Su periodo termino. Administracion lo sigue consultando; el aprendiz ya no lo ve. */
    CERRADO,

    /** Sin fechas. No caduca. */
    SIN_PERIODO
}

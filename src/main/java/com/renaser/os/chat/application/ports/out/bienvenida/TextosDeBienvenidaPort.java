package com.renaser.os.chat.application.ports.out.bienvenida;

/**
 * Los textos de la bienvenida de Operaciones (OPE-01-01, D-190). Viven en un recurso versionado
 * del repo, no en variables de entorno: se cambian editando el archivo y redesplegando.
 *
 * <p>Un texto vacío quiere decir «ese mensaje no se manda». Nunca devuelve {@code null}.
 */
public interface TextosDeBienvenidaPort {

    /** Acompaña la tarjeta en el soporte. {@code {nombre}} = primer nombre del aprendiz. */
    String soporteConLaTarjeta();

    /** El mensaje formal de bienvenida en el soporte. {@code {nombre}} = primer nombre del aprendiz. */
    String soporteFormal();

    /**
     * El mensaje del mentor en el chat del grupo estable (D-191). {@code {nombre}} = primer nombre del
     * aprendiz, {@code {mentor}} = primer nombre del mentor.
     */
    String grupo();
}

package com.renaser.os.chat.application.ports.out.bienvenida;

/**
 * Los textos VIGENTES de la bienvenida de Operaciones (OPE-01-01, D-190): los que salen hoy. Cada uno es
 * el que guardó Administración o Alquimista desde la app, si lo hay (D-210), o si no el original de
 * {@code bienvenida/mensajes.yaml} ({@link TextosOriginalesDeBienvenidaPort}).
 * <blockquote><b>Corregido 2026-09-27 (D-210).</b> Decía «Viven en un recurso versionado del repo, no en
 * variables de entorno: se cambian editando el archivo y redesplegando». El recurso sigue siendo el
 * original (y sigue sin haber variables de entorno), pero desde D-210 también se cambian desde la app.
 * </blockquote>
 *
 * <p>Un texto vacío quiere decir «ese mensaje no se manda» (solo puede venir del recurso: desde la app no
 * se guarda vacío). Nunca devuelve {@code null}.
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

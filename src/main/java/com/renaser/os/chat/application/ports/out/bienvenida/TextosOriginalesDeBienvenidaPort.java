package com.renaser.os.chat.application.ports.out.bienvenida;

import com.renaser.os.chat.domain.model.bienvenida.PiezaDeBienvenida;

/**
 * Los textos ORIGINALES de la bienvenida: los de {@code src/main/resources/bienvenida/mensajes.yaml},
 * versionados con el código (D-190). Son los que salen mientras Administración no guarde otro, y a los
 * que se vuelve con «Volver al texto original» (D-210).
 *
 * <p>Un texto vacío en el recurso quiere decir «ese mensaje no se manda». Nunca devuelve {@code null}.
 */
public interface TextosOriginalesDeBienvenidaPort {

    /** @throws IllegalArgumentException si la pieza es la portada */
    String original(PiezaDeBienvenida pieza);
}

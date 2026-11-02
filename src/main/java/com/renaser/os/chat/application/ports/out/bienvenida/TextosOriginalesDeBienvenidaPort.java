package com.renaser.os.chat.application.ports.out.bienvenida;

import com.renaser.os.chat.domain.model.bienvenida.PiezaDeBienvenida;
import com.renaser.os.shared.domain.Formacion;

/**
 * Los textos ORIGINALES de la bienvenida: los de {@code src/main/resources/bienvenida/mensajes.yaml},
 * versionados con el código (D-190). Son los que salen mientras Administración no guarde otro, y a los
 * que se vuelve con «Volver al texto original» (D-210).
 *
 * <p>Cada formación tiene los suyos (spec 035): la Escuela los de hoy y las otras dos los de su sección del recurso, y si le falta
 * uno, el de la Escuela. Un texto vacío en el recurso quiere decir «ese mensaje no se manda». Nunca devuelve {@code null}.
 */
public interface TextosOriginalesDeBienvenidaPort {

    /** @throws IllegalArgumentException si la pieza no es un texto */
    String original(Formacion formacion, PiezaDeBienvenida pieza);
}

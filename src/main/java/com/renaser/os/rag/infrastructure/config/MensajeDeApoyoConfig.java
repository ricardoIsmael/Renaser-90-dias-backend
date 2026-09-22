package com.renaser.os.rag.infrastructure.config;

import com.renaser.os.rag.domain.model.seguridad.MensajeDeApoyo;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Convierte la propiedad de entorno en el valor de dominio {@link MensajeDeApoyo}. Mismo molde que
 * {@code AvisosHabitoConfig} en {@code habits}, y por el mismo motivo: el valor definitivo es una
 * decision del dueno que todavia no esta tomada, y el repo prohibe rellenarla con supuestos.
 *
 * <p><b>El default es vacio, y eso no es un olvido.</b> El texto del MINSA —numero, horario,
 * alcance— no esta confirmado. Un telefono inventado no es un placeholder: es mandar a alguien que
 * esta mal a llamar a la nada. Con la propiedad vacia, el aviso a ADMIN/ALQUIMISTA se emite igual y
 * a la persona no se le muestra nada.
 *
 * <p>Se completa con {@code RENASIA_MENSAJE_APOYO} o escribiendo
 * {@code renaser.renasia.apoyo.mensaje} en el YAML. No hace falta desplegar codigo para encenderlo.
 */
@Configuration
class MensajeDeApoyoConfig {

    @Bean
    MensajeDeApoyo mensajeDeApoyo(@Value("${renaser.renasia.apoyo.mensaje:}") String texto) {
        return new MensajeDeApoyo(texto);
    }
}

package com.renaser.os.chat.infrastructure.adapter.out.bienvenida;

import com.renaser.os.chat.application.ports.out.bienvenida.TextosDeBienvenidaPort;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.util.Properties;

/**
 * Lee los textos de {@code src/main/resources/bienvenida/mensajes.yaml} una vez, al arrancar (D-190).
 *
 * <p>Si el archivo falta, el arranque falla: es parte del artefacto, y que falte es un error de
 * empaquetado, no una configuración. Una clave que falta o viene vacía solo apaga ese mensaje.
 */
@Component
class TextosDeBienvenidaYamlAdapter implements TextosDeBienvenidaPort {

    static final String RECURSO = "bienvenida/mensajes.yaml";

    private final String soporteConLaTarjeta;
    private final String soporteFormal;

    TextosDeBienvenidaYamlAdapter() {
        this(new ClassPathResource(RECURSO));
    }

    TextosDeBienvenidaYamlAdapter(Resource recurso) {
        Properties textos = leer(recurso);
        this.soporteConLaTarjeta = texto(textos, "soporte.con-la-tarjeta");
        this.soporteFormal = texto(textos, "soporte.formal");
    }

    @Override
    public String soporteConLaTarjeta() {
        return soporteConLaTarjeta;
    }

    @Override
    public String soporteFormal() {
        return soporteFormal;
    }

    private static Properties leer(Resource recurso) {
        if (!recurso.exists()) {
            throw new IllegalStateException("Falta el recurso de textos de bienvenida: " + recurso);
        }
        YamlPropertiesFactoryBean yaml = new YamlPropertiesFactoryBean();
        yaml.setResources(recurso);
        Properties textos = yaml.getObject();
        return textos == null ? new Properties() : textos;
    }

    private static String texto(Properties textos, String clave) {
        return textos.getProperty(clave, "").strip();
    }
}

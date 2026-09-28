package com.renaser.os.chat.infrastructure.adapter.out.bienvenida;

import com.renaser.os.chat.application.ports.out.bienvenida.TextosOriginalesDeBienvenidaPort;
import com.renaser.os.chat.domain.model.bienvenida.PiezaDeBienvenida;
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
 *
 * <p><b>Son los ORIGINALES</b> (D-210): salen mientras Administración no guarde otro desde la app, y a
 * ellos se vuelve con «Volver al texto original». Los que salen de verdad los arma
 * {@link TextosDeBienvenidaVigentes}.
 * <blockquote><b>Corregido 2026-09-27 (D-210).</b> Implementaba {@code TextosDeBienvenidaPort}: era la
 * única fuente de los textos que se mandaban.</blockquote>
 */
@Component
class TextosDeBienvenidaYamlAdapter implements TextosOriginalesDeBienvenidaPort {

    static final String RECURSO = "bienvenida/mensajes.yaml";

    private final String soporteConLaTarjeta;
    private final String soporteFormal;
    private final String grupo;

    TextosDeBienvenidaYamlAdapter() {
        this(new ClassPathResource(RECURSO));
    }

    TextosDeBienvenidaYamlAdapter(Resource recurso) {
        Properties textos = leer(recurso);
        this.soporteConLaTarjeta = texto(textos, "soporte.con-la-tarjeta");
        this.soporteFormal = texto(textos, "soporte.formal");
        this.grupo = texto(textos, "grupo");
    }

    @Override
    public String original(PiezaDeBienvenida pieza) {
        return switch (pieza) {
            case SOPORTE_CON_LA_TARJETA -> soporteConLaTarjeta;
            case SOPORTE_FORMAL -> soporteFormal;
            case GRUPO -> grupo;
            case PORTADA, CARTA_CAJA -> throw new IllegalArgumentException("La " + (pieza == PiezaDeBienvenida.PORTADA
                    ? "portada" : "carta de la caja") + " no es un texto de bienvenida");
        };
    }

    String soporteConLaTarjeta() {
        return soporteConLaTarjeta;
    }

    String soporteFormal() {
        return soporteFormal;
    }

    String grupo() {
        return grupo;
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

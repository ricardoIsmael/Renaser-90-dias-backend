package com.renaser.os.chat.infrastructure.adapter.out.bienvenida;

import com.renaser.os.chat.application.ports.out.bienvenida.TextosOriginalesDeBienvenidaPort;
import com.renaser.os.chat.domain.model.bienvenida.PiezaDeBienvenida;
import com.renaser.os.shared.domain.Formacion;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;

/**
 * Lee los textos de {@code src/main/resources/bienvenida/mensajes.yaml} una vez, al arrancar (D-190).
 *
 * <p>Si el archivo falta, el arranque falla: es parte del artefacto, y que falte es un error de
 * empaquetado, no una configuración. Una clave que falta o viene vacía solo apaga ese mensaje.
 *
 * <p><b>Una sección por formación</b> (spec 035). Los de la Escuela son los de siempre ({@code soporte.*} y {@code grupo}); los del Diplomado y la
 * Certificación están bajo {@code diplomado.} y {@code certificacion.} con las mismas claves. <b>Si una formación no trae un texto, usa el de la
 * Escuela</b>: nunca queda una bienvenida sin texto por una clave que falta. Distinto es un texto que la formación trae VACÍO a propósito: ese mensaje
 * no se manda en ella (D-190).
 *
 * <p><b>Son los ORIGINALES</b> (D-210): salen mientras Administración no guarde otro desde la app, y a ellos se vuelve con «Volver al texto original».
 * Los que salen de verdad los arma {@link TextosDeBienvenidaVigentes}.
 */
@Component
class TextosDeBienvenidaYamlAdapter implements TextosOriginalesDeBienvenidaPort {

    static final String RECURSO = "bienvenida/mensajes.yaml";

    private final Map<Formacion, Map<PiezaDeBienvenida, String>> textos = new EnumMap<>(Formacion.class);

    TextosDeBienvenidaYamlAdapter() {
        this(new ClassPathResource(RECURSO));
    }

    TextosDeBienvenidaYamlAdapter(Resource recurso) {
        Properties leidos = leer(recurso);
        for (Formacion formacion : Formacion.values()) {
            Map<PiezaDeBienvenida, String> deLaFormacion = new EnumMap<>(PiezaDeBienvenida.class);
            for (PiezaDeBienvenida pieza : PiezaDeBienvenida.textos()) {
                String propio = leidos.getProperty(clave(formacion, pieza));
                String texto = propio != null || formacion == Formacion.POR_DEFECTO
                        ? limpio(propio) : textos.get(Formacion.POR_DEFECTO).get(pieza);
                deLaFormacion.put(pieza, texto);
            }
            textos.put(formacion, deLaFormacion);
        }
    }

    @Override
    public String original(Formacion formacion, PiezaDeBienvenida pieza) {
        if (!pieza.esTexto()) {
            throw new IllegalArgumentException("La " + (pieza == PiezaDeBienvenida.PORTADA ? "portada" : "imagen o carta")
                    + " no es un texto de bienvenida");
        }
        return textos.get(formacion).get(pieza);
    }

    String soporteConLaTarjeta() {
        return original(Formacion.POR_DEFECTO, PiezaDeBienvenida.SOPORTE_CON_LA_TARJETA);
    }

    String soporteFormal() {
        return original(Formacion.POR_DEFECTO, PiezaDeBienvenida.SOPORTE_FORMAL);
    }

    String grupo() {
        return original(Formacion.POR_DEFECTO, PiezaDeBienvenida.GRUPO);
    }

    /** La de la Escuela es la de siempre ({@code soporte.formal}); las otras llevan el código de la formación delante ({@code diplomado.soporte.formal}). */
    private static String clave(Formacion formacion, PiezaDeBienvenida pieza) {
        String propia = switch (pieza) {
            case SOPORTE_CON_LA_TARJETA -> "soporte.con-la-tarjeta";
            case SOPORTE_FORMAL -> "soporte.formal";
            case GRUPO -> "grupo";
            case PORTADA, CARTA_CAJA, IMAGEN_DE_LA_FORMACION -> throw new IllegalArgumentException(pieza + " no es un texto");
        };
        return formacion == Formacion.POR_DEFECTO ? propia : formacion.name().toLowerCase(Locale.ROOT) + "." + propia;
    }

    private static Properties leer(Resource recurso) {
        if (!recurso.exists()) {
            throw new IllegalStateException("Falta el recurso de textos de bienvenida: " + recurso);
        }
        YamlPropertiesFactoryBean yaml = new YamlPropertiesFactoryBean();
        yaml.setResources(recurso);
        Properties leidos = yaml.getObject();
        return leidos == null ? new Properties() : leidos;
    }

    private static String limpio(String texto) {
        return texto == null ? "" : texto.strip();
    }
}

package com.renaser.os.chat.application.ports.out.bienvenida;

/**
 * La tarjeta de bienvenida de Operaciones (el Canva "Programa Formación Renaser") con el nombre
 * de la persona escrito encima (D-174).
 *
 * <p><b>La portada puede cambiar</b> (D-210): Administración y Alquimista suben otra imagen de fondo
 * desde la app. {@link #dibujar(String)} usa siempre la VIGENTE; {@link #portadaVigente()} dice cuál es,
 * y {@link #dibujar(String, String)} dibuja sobre una dada (la vigente, o una candidata ya revisada).
 * Una misma portada dibuja siempre la misma tarjeta: por eso su versión sirve de clave para guardar
 * tarjetas ya dibujadas ({@code TarjetasConNombreEnMemoria}).
 */
public interface DibujarBienvenidaPort {

    String TIPO_CONTENIDO = "image/jpeg";

    /** La versión de la portada de Operaciones (el fondo de Canva del recurso): la que hay si nadie la cambió. */
    String PORTADA_ORIGINAL = "original";

    /**
     * @return la tarjeta sobre la portada vigente, en {@link #TIPO_CONTENIDO}; con {@code nombre} vacío, la
     *         portada sola.
     */
    byte[] dibujar(String nombre);

    /**
     * {@link #PORTADA_ORIGINAL} o la ruta de la portada que subió Administración.
     *
     * <p>Por defecto, la original: quien no sabe de portadas (un doble de prueba) dibuja siempre sobre ella.
     */
    default String portadaVigente() {
        return PORTADA_ORIGINAL;
    }

    /**
     * La tarjeta sobre la portada {@code portada} ({@link #PORTADA_ORIGINAL} o una ruta ya revisada con
     * {@link PortadaDeBienvenidaPort#revisar}).
     *
     * <p>Por defecto ignora la portada y dibuja como {@link #dibujar(String)}: alcanza para quien tiene una
     * sola (los dobles de prueba). El adaptador real la respeta.
     */
    default byte[] dibujar(String nombre, String portada) {
        return dibujar(nombre);
    }
}

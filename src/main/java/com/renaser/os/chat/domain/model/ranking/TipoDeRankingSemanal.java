package com.renaser.os.chat.domain.model.ranking;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;

/**
 * Los cuatro rankings que salen cada lunes en el grupo general (D-262, ampliado en D-367): el General, la Coherencia, los
 * Kilómetros y el Gimnasio, cada uno con su mensaje. La Liga no entra (decisión del dueño, 23/11/2026).
 *
 * <p>El orden es el de publicación: primero el General, y después los otros tres, seguidos. Cada uno lleva lo que cambia en
 * la imagen —el rótulo de arriba, el título y la unidad del valor— y cómo se escribe su valor.
 *
 * <p>Qué mide cada uno <b>esa semana</b> (de lunes a domingo):
 * <ul>
 *   <li>GENERAL: el puntaje general (75 % hábitos de los 7 días + 25 % cursos), con puntos.</li>
 *   <li>COHERENCIA: el % de acciones diarias cumplidas de los 7 días (con 1 decimal).</li>
 *   <li>KILOMETROS: los km de la semana (no el acumulado del programa), con 1 decimal.</li>
 *   <li>GIMNASIO: los días de gimnasio de la semana (no el acumulado del programa), enteros.</li>
 * </ul>
 */
public enum TipoDeRankingSemanal {

    GENERAL("general", "RANKING GENERAL", "Podio de la ", "semana", 1),
    COHERENCIA("coherencia", "COHERENCIA", "Podio de ", "coherencia", 1),
    KILOMETROS("kilometros", "KILÓMETROS", "Podio de ", "kilómetros", 1),
    GIMNASIO("gimnasio", "GIMNASIO", "Podio de ", "gimnasio", 0);

    private final String codigo;
    private final String rotulo;
    private final String tituloRecto;
    private final String tituloCursiva;
    private final int decimales;

    TipoDeRankingSemanal(String codigo, String rotulo, String tituloRecto, String tituloCursiva, int decimales) {
        this.codigo = codigo;
        this.rotulo = rotulo;
        this.tituloRecto = tituloRecto;
        this.tituloCursiva = tituloCursiva;
        this.decimales = decimales;
    }

    /** El nombre en minúscula y sin tilde: va en el id del mensaje, en la ruta de la imagen y en el pedido de la vista previa. */
    public String codigo() {
        return codigo;
    }

    /** Lo que va arriba de la imagen, después de «FORMACIÓN RENASER ·». */
    public String rotulo() {
        return rotulo;
    }

    /** La parte recta del título de la imagen: «Podio de la » o «Podio de ». */
    public String tituloRecto() {
        return tituloRecto;
    }

    /** La palabra en cursiva dorada del título: «semana», «coherencia»… */
    public String tituloCursiva() {
        return tituloCursiva;
    }

    /** «96,4», «42,5» o «5»: coma decimal, sin unidad. */
    public String numero(BigDecimal valor) {
        return valor.setScale(decimales, RoundingMode.HALF_UP).toPlainString().replace('.', ',');
    }

    /** La unidad que va junto al número, en singular o plural según el valor: «pts», «%», «km», «día(s)». */
    public String unidad(BigDecimal valor) {
        return switch (this) {
            case GENERAL -> "pts";
            case COHERENCIA -> "%";
            case KILOMETROS -> "km";
            case GIMNASIO -> valor.setScale(0, RoundingMode.HALF_UP).intValue() == 1 ? "día" : "días";
        };
    }

    /** «96,4 pts», «87,5 %», «42,5 km», «1 día», «5 días». */
    public String conUnidad(BigDecimal valor) {
        return numero(valor) + " " + unidad(valor);
    }

    /** @return el ranking de ese código (sin importar mayúsculas) o {@code null} si no existe */
    public static TipoDeRankingSemanal deCodigo(String codigo) {
        if (codigo == null) {
            return null;
        }
        String limpio = codigo.strip().toLowerCase(Locale.ROOT);
        for (TipoDeRankingSemanal tipo : values()) {
            if (tipo.codigo.equals(limpio)) {
                return tipo;
            }
        }
        return null;
    }
}

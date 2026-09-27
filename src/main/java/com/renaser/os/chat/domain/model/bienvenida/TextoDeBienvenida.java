package com.renaser.os.chat.domain.model.bienvenida;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Las reglas de un texto de bienvenida que guarda Administración (D-210).
 *
 * <p>Un texto guardado reemplaza al del recurso ({@code bienvenida/mensajes.yaml}) hasta que alguien
 * vuelva al original. Tiene que poder mandarse tal cual a cualquier aprendiz, así que:
 * <ul>
 *   <li><b>No puede quedar vacío.</b> En el recurso un texto vacío apaga ese mensaje (D-190); desde la
 *       app no: apagar un mensaje no es editarlo, y no es lo que se pidió.</li>
 *   <li><b>Conserva los marcadores que su pieza necesita</b> ({@link PiezaDeBienvenida#marcadores()}):
 *       sin {@code {nombre}} el mensaje saldría igual para todos, que es justo lo que la bienvenida
 *       personalizada vino a evitar.</li>
 *   <li><b>No trae marcadores que nadie reemplaza.</b> Un {@code {mentor}} en el soporte, o un
 *       {@code {Nombre}} mal escrito, le llegaría al aprendiz con las llaves.</li>
 *   <li><b>Hasta {@link #LARGO_MAXIMO} caracteres</b>, contados como los cuenta Postgres
 *       ({@code char_length}: un emoji es uno). El más largo de hoy tiene ~430; 1000 deja lugar de sobra
 *       para un mensaje de chat sin que se convierta en una carta. Decisión técnica, a confirmar.</li>
 * </ul>
 */
public final class TextoDeBienvenida {

    public static final String NOMBRE = "{nombre}";
    public static final String MENTOR = "{mentor}";
    public static final int LARGO_MAXIMO = 1000;

    /** Cualquier cosa entre llaves en una línea: lo que una persona leería como «un marcador». */
    private static final Pattern MARCADOR = Pattern.compile("\\{[^{}\\n]{0,40}\\}");

    private TextoDeBienvenida() {
    }

    /**
     * @return el texto sin espacios en los bordes, listo para guardar
     * @throws IllegalArgumentException con el motivo en palabras simples (la app lo muestra tal cual)
     */
    public static String validar(PiezaDeBienvenida pieza, String texto) {
        if (!pieza.esTexto()) {
            throw new IllegalArgumentException("La portada no es un mensaje: se cambia subiendo una imagen");
        }
        String limpio = texto == null ? "" : texto.strip();
        if (limpio.isEmpty()) {
            throw new IllegalArgumentException("El mensaje no puede quedar vacío.");
        }
        int largo = limpio.codePointCount(0, limpio.length());
        if (largo > LARGO_MAXIMO) {
            throw new IllegalArgumentException(
                    "El mensaje tiene " + largo + " caracteres: el máximo es " + LARGO_MAXIMO + ".");
        }
        exigirSoloMarcadoresConocidos(pieza, limpio);
        exigirSusMarcadores(pieza, limpio);
        return limpio;
    }

    private static void exigirSoloMarcadoresConocidos(PiezaDeBienvenida pieza, String texto) {
        Matcher marcador = MARCADOR.matcher(texto);
        while (marcador.find()) {
            if (!pieza.marcadores().contains(marcador.group())) {
                throw new IllegalArgumentException(motivoDeMarcadorDesconocido(pieza, marcador.group()));
            }
        }
    }

    private static String motivoDeMarcadorDesconocido(PiezaDeBienvenida pieza, String encontrado) {
        Optional<String> parecido = pieza.marcadores().stream().filter(v -> v.equalsIgnoreCase(encontrado)).findFirst();
        if (parecido.isPresent()) {
            return "El mensaje tiene " + encontrado + ": escríbelo " + parecido.get()
                    + ", en minúsculas, para que se reemplace.";
        }
        return "El mensaje tiene " + encontrado + ", que no se reemplaza por nada: en este mensaje solo se puede usar "
                + String.join(" y ", pieza.marcadores()) + ".";
    }

    private static void exigirSusMarcadores(PiezaDeBienvenida pieza, String texto) {
        for (String marcador : pieza.marcadores()) {
            if (!texto.contains(marcador)) {
                throw new IllegalArgumentException(NOMBRE.equals(marcador)
                        ? "Al mensaje le falta {nombre}: es donde va el nombre de la persona."
                        : "Al mensaje le falta {mentor}: es donde va el nombre de su mentor.");
            }
        }
    }
}

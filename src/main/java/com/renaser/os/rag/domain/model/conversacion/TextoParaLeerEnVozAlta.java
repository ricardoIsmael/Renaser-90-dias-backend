package com.renaser.os.rag.domain.model.conversacion;

/**
 * Lo que se le pasa a la voz del orbe (D-227): el mismo texto sin emojis.
 *
 * <p>Desde D-227 el acompanante escribe con emojis en el chat. En una respuesta por voz el prompt
 * se los prohibe ({@code prompts/modo-voz.st}), pero eso es una instruccion al modelo, no una
 * garantia, y la app tambien puede mandar a leer un texto escrito. Un sintetizador que recibe
 * "Bien hecho 💪" dice "bien hecho, bíceps flexionado" o un ruido raro: aca se quitan antes.
 *
 * <p>Se quita todo pictograma ({@link Character#isExtendedPictographic}) y las piezas que arman un
 * emoji compuesto (selector de variacion, union de ancho cero, tonos de piel, banderas, tecla
 * enmarcada), pero nunca un digito, "#" ni "*", que tambien son componentes de emoji ("1️⃣" queda
 * "1"). Despues se ordenan los espacios que dejo el hueco.
 */
public final class TextoParaLeerEnVozAlta {

    private static final int ULTIMO_ASCII = 0x7F;

    private TextoParaLeerEnVozAlta() {
    }

    /** El texto sin emojis, sin espacios dobles ni un espacio suelto antes de la puntuacion. */
    public static String sinEmojis(String texto) {
        if (texto == null) {
            return "";
        }
        StringBuilder limpio = new StringBuilder(texto.length());
        texto.codePoints().filter(caracter -> !esDeEmoji(caracter)).forEach(limpio::appendCodePoint);
        return limpio.toString()
                .replaceAll("[ \\t]{2,}", " ")
                .replaceAll(" +([.,;:!?)])", "$1")
                .replaceAll("([¡¿(]) +", "$1")
                .strip();
    }

    private static boolean esDeEmoji(int caracter) {
        return Character.isExtendedPictographic(caracter)
                || (caracter > ULTIMO_ASCII && Character.isEmojiComponent(caracter));
    }
}

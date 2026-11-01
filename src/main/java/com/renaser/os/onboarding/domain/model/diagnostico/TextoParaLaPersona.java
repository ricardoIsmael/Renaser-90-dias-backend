package com.renaser.os.onboarding.domain.model.diagnostico;

/**
 * Los textos del diagnóstico pueden nombrar el plazo y la formación de quien los lee: «En {duracion} quiero haber conseguido» se lee «En 90 días…» o
 * «En 1 año…» según la persona (FR-011, 026-12). Se reemplaza al entregarlos; lo que se guarda en la base y en la foto de cada versión es el texto con la marca.
 */
public final class TextoParaLaPersona {

    public static final String DURACION = "{duracion}";
    public static final String FORMACION = "{formacion}";

    private TextoParaLaPersona() {
    }

    public static String reemplazar(String texto, String duracion, String formacion) {
        if (texto == null) {
            return null;
        }
        return texto.replace(DURACION, duracion).replace(FORMACION, formacion);
    }

    /** «1 año» para 365 días; «90 días» para el resto (la Escuela y la Certificación son de 90; el Diplomado, de 90 o 365). */
    public static String duracionEnPalabras(int dias) {
        return dias == 365 ? "1 año" : dias + " días";
    }
}

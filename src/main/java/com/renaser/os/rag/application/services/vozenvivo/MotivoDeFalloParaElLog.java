package com.renaser.os.rag.application.services.vozenvivo;

import com.renaser.os.rag.domain.model.herramienta.ResultadoHerramienta;

/**
 * El motivo de una herramienta que fallo en la voz en vivo, recortado para el log (E-475).
 *
 * <p>El 2026-10-01 el log decia {@code proponer_cambio_de_horario -> fallo (55 ms)} y nada mas: no
 * habia forma de saber si era la hora, el cupo o la fecha. El motivo es texto escrito por el sistema
 * para el modelo, pero puede llevar el nombre de un habito o de una accion (que la persona pudo
 * escribir) entre comillas: eso se tapa. Solo queda la primera oracion y como mucho
 * {@value #LARGO_MAXIMO} caracteres: alcanza para reconocer la regla que corto.
 */
final class MotivoDeFalloParaElLog {

    static final int LARGO_MAXIMO = 140;

    private MotivoDeFalloParaElLog() {
    }

    /** {@code ""} si no fue un fallo. */
    static String de(ResultadoHerramienta resultado) {
        if (!(resultado instanceof ResultadoHerramienta.Fallo(String motivo)) || motivo == null) {
            return "";
        }
        String sinNombres = motivo.replaceAll("'[^']*'", "'…'").replaceAll("\"[^\"]*\"", "\"…\"");
        int finDeOracion = sinNombres.indexOf(". ");
        String primera = finDeOracion < 0 ? sinNombres : sinNombres.substring(0, finDeOracion + 1);
        return primera.length() <= LARGO_MAXIMO ? primera : primera.substring(0, LARGO_MAXIMO) + "…";
    }
}

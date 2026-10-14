package com.renaser.os.rag.domain.model.circuito;

import java.util.Arrays;
import java.util.Optional;

/**
 * Las partes de SER que tienen circuito automático propio (spec 002, FR-016; plan.md D7) y la funcionalidad del
 * catálogo de {@code plataforma} que apaga cada una cuando su circuito se abre. Se cuentan por separado porque un
 * proveedor puede fallar en una sin fallar en la otra: si la voz de Gemini se satura, el chat de texto sigue.
 */
public enum ParteDeLaIa {

    /** El chat de SER y el del curso, y los embeddings con los que busca en el material de los cursos. */
    CHAT("ia.chat"),
    /** La voz del orbe (texto a voz) y la voz en vivo (Gemini Live). */
    VOZ("ia.voz");

    private final String clave;

    ParteDeLaIa(String clave) {
        this.clave = clave;
    }

    /** La clave del catálogo de funcionalidades, por ejemplo {@code "ia.chat"}. */
    public String clave() {
        return clave;
    }

    /** La parte que apaga esa clave, si alguna: {@code "ia"} o {@code "ia.propuestas"} no tienen circuito. */
    public static Optional<ParteDeLaIa> deClave(String clave) {
        return Arrays.stream(values()).filter(parte -> parte.clave.equals(clave)).findFirst();
    }
}

package com.renaser.os.habits.domain.model.medicion;

import java.time.Instant;
import java.util.Objects;

/**
 * Lo que acompaña a una {@link MedicionDiaria} de origen {@link OrigenMedicion#HEALTH_CONNECT} (spec 037): los pasos del día
 * (opcionales: un reloj puede dar solo la distancia), la etiqueta legible de la fuente, el tipo de dispositivo, cuándo leyó
 * el teléfono y si el servidor la dejó «por revisar».
 *
 * <p>{@code fuente} es texto que lee una persona (el mentor): nunca se usa para decidir. {@code porRevisar} lo pone
 * {@code PoliticaKilometros}: una lectura dudosa se acepta y se marca, no se rechaza.
 */
public record LecturaAutomatica(Integer pasos, String fuente, DispositivoDeMedicion dispositivo, Instant leidaEn,
                                boolean porRevisar) {

    /** Largo máximo de la etiqueta: el de la columna {@code fuente_medicion}. */
    public static final int LARGO_MAXIMO_DE_LA_FUENTE = 60;

    public LecturaAutomatica {
        Objects.requireNonNull(fuente, "La fuente de la lectura es obligatoria");
        Objects.requireNonNull(dispositivo, "El dispositivo de la lectura es obligatorio");
        Objects.requireNonNull(leidaEn, "La hora de la lectura es obligatoria");
        fuente = fuente.strip();
        if (fuente.isEmpty() || fuente.length() > LARGO_MAXIMO_DE_LA_FUENTE) {
            throw new IllegalArgumentException("La fuente tiene que tener entre 1 y " + LARGO_MAXIMO_DE_LA_FUENTE
                    + " caracteres");
        }
        if (pasos != null && pasos < 0) {
            throw new IllegalArgumentException("Los pasos no pueden ser negativos");
        }
    }

    /** La misma lectura marcada como «por revisar» (o no). */
    public LecturaAutomatica conRevision(boolean porRevisar) {
        return new LecturaAutomatica(pasos, fuente, dispositivo, leidaEn, porRevisar);
    }
}

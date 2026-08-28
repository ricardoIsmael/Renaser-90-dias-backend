package com.renaser.os.habits.application.ports.out.audioterapia;

import java.util.List;
import java.util.Optional;

/**
 * Catalogo de audioterapias semanales (tabla {@code audioterapias}). A diferencia de
 * {@code AudioCatalogPort} (Espiritu), esta tabla no depende de Google Drive -- vive entera en
 * el esquema propio, asi que el adapter real (JPA) se puede implementar de una, sin placeholder.
 */
public interface AudioterapiaCatalogPort {

    Optional<Audioterapia> porSemana(int semana);

    /** Catalogo completo, orden ascendente de semana -- para recorrer duraciones acumuladas. */
    List<Audioterapia> todasOrdenadas();

    record Audioterapia(int semana, String titulo, String rutaStorage, String mime, Integer tamanoBytes,
                         int duracionDias) {
    }
}

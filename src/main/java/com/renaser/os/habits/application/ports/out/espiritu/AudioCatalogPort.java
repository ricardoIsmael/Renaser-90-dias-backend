package com.renaser.os.habits.application.ports.out.espiritu;

import java.util.List;
import java.util.Optional;

/**
 * Catalogo de audios de Espiritu (tabla {@code audios_espiritu}) — vive detras de un
 * puerto porque en produccion se sincroniza desde Google Drive (repo viejo,
 * {@code syncCatalogFromDrive}/{@code listSpiritAudioFiles}, D-34: toda integracion externa
 * detras de un puerto). Este encargo pide explicitamente NO integrar Drive todavia — la
 * unica implementacion de esta pasada es {@code NoOpAudioCatalogAdapter}, que siempre
 * devuelve vacio (mismo patron que {@code NoOpAlmacenamientoAdapter} para S3 y
 * {@code NoOpEvidenciaValidacionIAAdapter} para Gemini/Vertex).
 */
public interface AudioCatalogPort {

    Optional<AudioEspiritu> porDia(int dia);

    /** Catalogo completo, orden ascendente de dia — para construir la vista dia-por-dia. */
    List<AudioEspiritu> todos();

    record AudioEspiritu(int dia, String titulo, String driveFileId, String mime, Integer tamanoBytes) {
    }
}

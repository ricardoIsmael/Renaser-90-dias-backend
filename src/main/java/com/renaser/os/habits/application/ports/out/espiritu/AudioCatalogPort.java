package com.renaser.os.habits.application.ports.out.espiritu;

import java.util.List;
import java.util.Optional;

/**
 * Catalogo de audios de Espiritu (tabla {@code audios_espiritu}) — el audio que le toca a
 * cada dia del programa del aprendiz ("Pastilla Renacer").
 *
 * <p><b>Ojo con el numero de dia:</b> el {@code dia} de este catalogo es el <i>dia de
 * audio</i>, no el dia de programa. La conversion vive en un solo lugar,
 * {@code EspirituService.AUDIO_UNLOCK_START_DAY}: {@code diaAudio = diaPrograma - 7}, o sea
 * diaPrograma 8 → audio 1 (confirmado con el cliente 2026-08-10).
 *
 * <p><b>Historia de las implementaciones.</b> Nacio detras de un puerto porque en el backend
 * viejo el catalogo se sincronizaba desde Google Drive ({@code syncCatalogFromDrive}), y D-34
 * manda que toda integracion externa vaya detras de un puerto. Mientras no hubo integracion,
 * la unica implementacion fue {@code NoOpAudioCatalogAdapter} (siempre vacio). Hoy la tabla
 * `audios_espiritu` ya tiene las 43 filas reales cargadas por {@code V5}, asi que el adapter
 * por defecto es {@code AudiosEspirituPersistenceAdapter}, que las lee directo — sin Drive,
 * que sigue sin integrarse. El NoOp queda disponible con
 * {@code renaser.espiritu.catalogo=noop}.
 */
public interface AudioCatalogPort {

    Optional<AudioEspiritu> porDia(int dia);

    /** Catalogo completo, orden ascendente de dia — para construir la vista dia-por-dia. */
    List<AudioEspiritu> todos();

    /**
     * @param rutaStorage ruta del objeto en el bucket, la unica fuente de un audio
     *                    REPRODUCIBLE (se firma con {@code AlmacenamientoPort.firmarLectura},
     *                    igual que {@code audioterapias.ruta_storage}). Hoy {@code null} en
     *                    las 43 filas: los mp3 siguen en el Drive viejo, sin migrar (V25).
     * @param driveFileId referencia al archivo en el Drive del backend viejo. NO es una URL
     *                    ni sirve para reproducir: es la trazabilidad del origen, y lo que
     *                    permitira llenar {@code rutaStorage} cuando se migren los archivos.
     */
    record AudioEspiritu(int dia, String titulo, String driveFileId, String mime, Integer tamanoBytes,
                          String rutaStorage) {
    }
}

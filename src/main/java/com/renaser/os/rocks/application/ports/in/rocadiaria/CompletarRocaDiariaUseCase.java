package com.renaser.os.rocks.application.ports.in.rocadiaria;

import com.renaser.os.rocks.domain.model.rocadiaria.TipoEvidenciaRoca;
import com.renaser.os.rocks.domain.model.rocadiaria.RocaDiaria;
import com.renaser.os.rocks.domain.model.rocadiaria.RocaDiariaId;
import com.renaser.os.shared.application.SelfValidating;
import com.renaser.os.shared.domain.UserId;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;

/**
 * Completa una Roca Diaria enviando su evidencia (R-02). Atómico: inserta la
 * evidencia, marca la roca completada y aplica el premio de puntos, todo en
 * la misma transacción.
 */
public interface CompletarRocaDiariaUseCase {

    RocaDiaria completar(CompletarRocaDiariaCommand command);

    /**
     * Para {@code tipo != TEXTO}: {@code bucket}+{@code rutaStorage} (ya subidos vía
     * {@code SolicitarUrlAdjuntoRocaUseCase}). Para {@code TEXTO}: {@code contenidoTexto}.
     * {@code timestampExif} obligatorio solo para FOTO (Ley VI, ±15 min).
     *
     * <p>{@code esPrincipal} (Hueco #17): antes de esto viajaba hardcodeado en
     * {@code true} dentro de {@code RocaDiariaService} — la app lo necesita
     * como campo del cliente para elegir qué evidencia mostrar como
     * representativa de la roca al publicarla en el Muro. Se traduce 1:1 a
     * {@code evidence.api.RegistrarEvidenciaPort.RegistrarEvidenciaComando#esPrincipal},
     * que ya valida el mismo CHECK {@code principal_solo_en_roca} — acá
     * siempre es legal porque el destino de esta evidencia SIEMPRE es una
     * Roca Diaria.
     */
    record CompletarRocaDiariaCommand(@NotNull UserId actorId, @NotNull RocaDiariaId rocaDiariaId,
                                       @NotNull TipoEvidenciaRoca tipo, String bucket, String rutaStorage,
                                       String contenidoTexto, Instant timestampExif, Double gpsLat, Double gpsLng,
                                       boolean esPrincipal) {

        public CompletarRocaDiariaCommand {
            SelfValidating.validateConstructorArgs(CompletarRocaDiariaCommand.class, actorId, rocaDiariaId, tipo,
                    bucket, rutaStorage, contenidoTexto, timestampExif, gpsLat, gpsLng, esPrincipal);
            if (tipo == TipoEvidenciaRoca.TEXTO) {
                if (contenidoTexto == null || contenidoTexto.isBlank()) {
                    throw new IllegalArgumentException("contenidoTexto es obligatorio para evidencia de tipo TEXTO");
                }
            } else {
                if (bucket == null || bucket.isBlank() || rutaStorage == null || rutaStorage.isBlank()) {
                    throw new IllegalArgumentException("bucket y rutaStorage son obligatorios para evidencia no textual");
                }
                if (tipo == TipoEvidenciaRoca.FOTO && timestampExif == null) {
                    throw new IllegalArgumentException("timestampExif es obligatorio para evidencia de tipo FOTO (Ley VI)");
                }
            }
            requireGpsCoherente(gpsLat, gpsLng);
        }

        /**
         * Replica el CHECK `gps_completo` de `evidencias` (baseline V1) y sus rangos. Sin
         * esto, un cliente que manda una sola de las dos coordenadas pasaba toda la
         * validacion Java y recibia un 500 por violacion de restriccion en vez de un 400.
         */
        private static void requireGpsCoherente(Double gpsLat, Double gpsLng) {
            if ((gpsLat == null) != (gpsLng == null)) {
                throw new IllegalArgumentException("gpsLat y gpsLng viajan juntos: o los dos o ninguno");
            }
            if (gpsLat != null && (gpsLat < -90 || gpsLat > 90)) {
                throw new IllegalArgumentException("gpsLat fuera de rango (-90 a 90): " + gpsLat);
            }
            if (gpsLng != null && (gpsLng < -180 || gpsLng > 180)) {
                throw new IllegalArgumentException("gpsLng fuera de rango (-180 a 180): " + gpsLng);
            }
        }
    }
}

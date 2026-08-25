package com.renaser.os.evidence.api;

import com.renaser.os.shared.domain.UserId;

import java.time.Instant;
import java.util.UUID;

/**
 * Puerto de entrada público de {@code evidence} — la única forma en que otro módulo
 * ({@code rocks}, {@code habits}) inserta una evidencia. Reemplaza, para {@code rocks},
 * el INSERT nativo documentado como deuda en RK-2 de {@code docs/MODULO_ROCKS.md}, y
 * cierra D-H6 de {@code docs/MODULO_HABITS.md} para {@code habits} (que no tenía
 * ningún camino de subida de evidencia).
 *
 * <p>Es, a la vez, el "puerto de entrada" (in) para esta operación — no existe un
 * {@code RegistrarEvidenciaUseCase} interno separado porque nada dentro de
 * {@code evidence} expone "registrar" por su propio REST (solo {@code rocks}/
 * {@code habits} lo llaman); mismo criterio que {@code points.api.AjustarPuntosPort},
 * implementado directamente por el servicio de aplicación.
 */
public interface RegistrarEvidenciaPort {

    EvidenciaRegistrada registrar(RegistrarEvidenciaComando comando);

    /**
     * Para {@code tipo != TEXTO}: {@code bucket}+{@code rutaStorage} (ya subidos vía
     * {@code AlmacenamientoPort}, fuera de este puerto). Para {@code TEXTO}:
     * {@code contenidoTexto}. {@code esPrincipal} solo puede ser {@code true} cuando
     * {@code destino} es {@link DestinoEvidencia.RocaDiaria} (CHECK
     * {@code principal_solo_en_roca}) — validado en el constructor compacto para
     * fallar rápido (400), antes de llegar al dominio o a la base.
     */
    record RegistrarEvidenciaComando(UserId participanteId, DestinoEvidencia destino, TipoEvidencia tipo,
                                      String bucket, String rutaStorage, String contenidoTexto,
                                      Instant timestampExif, Double gpsLat, Double gpsLng, boolean esPrincipal,
                                      Instant subidaEn) {

        public RegistrarEvidenciaComando {
            if (participanteId == null) {
                throw new IllegalArgumentException("participanteId es obligatorio");
            }
            if (destino == null) {
                throw new IllegalArgumentException("destino es obligatorio");
            }
            if (tipo == null) {
                throw new IllegalArgumentException("tipo es obligatorio");
            }
            if (subidaEn == null) {
                throw new IllegalArgumentException("subidaEn es obligatorio");
            }
            requireMediaOTexto(tipo, bucket, rutaStorage, contenidoTexto);
            requireGpsCoherente(gpsLat, gpsLng);
            if (esPrincipal && !(destino instanceof DestinoEvidencia.RocaDiaria)) {
                throw new IllegalArgumentException(
                        "esPrincipal solo aplica a evidencia de Roca Diaria (CHECK principal_solo_en_roca)");
            }
        }

        /** Replica el CHECK {@code evidencia_media_o_texto} de la tabla {@code evidencias}. */
        private static void requireMediaOTexto(TipoEvidencia tipo, String bucket, String rutaStorage,
                                                 String contenidoTexto) {
            if (tipo == TipoEvidencia.TEXTO) {
                if (contenidoTexto == null || contenidoTexto.isBlank()) {
                    throw new IllegalArgumentException("contenidoTexto es obligatorio para evidencia de tipo TEXTO");
                }
            } else if (bucket == null || bucket.isBlank() || rutaStorage == null || rutaStorage.isBlank()) {
                throw new IllegalArgumentException("bucket y rutaStorage son obligatorios para evidencia no textual");
            }
        }

        /** Replica el CHECK {@code gps_completo} y los rangos de {@code gps_lat}/{@code gps_lng}. */
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

    record EvidenciaRegistrada(UUID id, EstadoValidacion estadoValidacion) {
    }
}

package com.renaser.os.evidence.domain.model.evidencia;

import com.renaser.os.evidence.api.DestinoEvidencia;
import com.renaser.os.evidence.api.EstadoValidacion;
import com.renaser.os.evidence.api.TipoEvidencia;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.UserId;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.experimental.Accessors;

import java.time.Instant;
import java.util.Objects;

/**
 * Evidencia (tabla {@code evidencias}): la prueba (foto/video/audio/texto/captura) que
 * un aprendiz sube para un hábito, una roca diaria o un registro de espíritu — arco
 * exclusivo, ver {@link DestinoEvidencia}. Dueña de la máquina de estados de validación
 * ({@link EstadoValidacion}), incluido el fallback a revisión manual tras 3 intentos de
 * IA fallidos — la única parte de esta clase con valor de negocio real en este alcance,
 * ya que la IA todavía no está integrada (ver {@code docs/MODULO_EVIDENCE.md}).
 */
@Getter
@Accessors(fluent = true)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@EqualsAndHashCode(of = "id")
public final class Evidencia {

    /** Espejo del CHECK {@code intentos_ia BETWEEN 0 AND 3}: al llegar a 3, cae a REVISION_MANUAL. */
    public static final int MAX_INTENTOS_IA = 3;

    private final EvidenciaId id;
    private final UserId participanteId;
    private final DestinoEvidencia destino;
    private final TipoEvidencia tipo;
    private final String bucket;
    private final String rutaStorage;
    private final String contenidoTexto;
    private final Instant timestampExif;
    private final Instant subidaEn;
    private final Double gpsLat;
    private final Double gpsLng;
    private final boolean esPrincipal;
    private EstadoValidacion estadoValidacion;
    private String notasValidacion;
    private int intentosIa;
    private boolean penalizacionAplicada;
    private boolean publicadaEnMuro;
    private final Instant creadoEn;

    /**
     * Registra una evidencia nueva — siempre nace {@code PENDIENTE}, 0 intentos de IA.
     * Revalida las mismas invariantes que ya fallan rápido en
     * {@code RegistrarEvidenciaPort.RegistrarEvidenciaComando} (CLAUDE.MD §5.4.3, nivel
     * 3: la regla de negocio vive en el dominio, no solo en el comando de entrada).
     */
    public static Evidencia registrar(UserId participanteId, DestinoEvidencia destino, TipoEvidencia tipo,
                                       String bucket, String rutaStorage, String contenidoTexto,
                                       Instant timestampExif, Double gpsLat, Double gpsLng, boolean esPrincipal,
                                       Instant subidaEn, Clock clock) {
        Objects.requireNonNull(participanteId, "participanteId es obligatorio");
        Objects.requireNonNull(destino, "destino es obligatorio");
        Objects.requireNonNull(tipo, "tipo es obligatorio");
        Objects.requireNonNull(subidaEn, "subidaEn es obligatorio");
        requireMediaOTexto(tipo, bucket, rutaStorage, contenidoTexto);
        requireGpsCoherente(gpsLat, gpsLng);
        requirePrincipalSoloEnRoca(esPrincipal, destino);
        return new Evidencia(EvidenciaId.newId(), participanteId, destino, tipo, bucket, rutaStorage, contenidoTexto,
                timestampExif, subidaEn, gpsLat, gpsLng, esPrincipal, EstadoValidacion.PENDIENTE, null, 0, false,
                false, clock.now());
    }

    /** Reconstruye desde persistencia — sin volver a validar invariantes de creación (ya pasaron una vez). */
    public static Evidencia rehydrate(EvidenciaId id, UserId participanteId, DestinoEvidencia destino,
                                       TipoEvidencia tipo, String bucket, String rutaStorage, String contenidoTexto,
                                       Instant timestampExif, Instant subidaEn, Double gpsLat, Double gpsLng,
                                       boolean esPrincipal, EstadoValidacion estadoValidacion, String notasValidacion,
                                       int intentosIa, boolean penalizacionAplicada, boolean publicadaEnMuro,
                                       Instant creadoEn) {
        return new Evidencia(id, participanteId, destino, tipo, bucket, rutaStorage, contenidoTexto, timestampExif,
                subidaEn, gpsLat, gpsLng, esPrincipal, estadoValidacion, notasValidacion, intentosIa,
                penalizacionAplicada, publicadaEnMuro, creadoEn);
    }

    /** La IA (cuando exista, ver docs/MODULO_EVIDENCE.md) aprobó la evidencia. */
    public void aprobarPorIa() {
        requireEnPendiente();
        this.estadoValidacion = EstadoValidacion.VALIDA;
    }

    /** La IA rechazó la evidencia — termina, no reintenta (a diferencia de NO_DISPONIBLE). */
    public void rechazarPorIa(String notas) {
        requireEnPendiente();
        this.estadoValidacion = EstadoValidacion.RECHAZADA;
        this.notasValidacion = notas;
    }

    /**
     * La IA no respondió (error transitorio o, en este alcance, {@code NoOpValidacionIAAdapter}
     * siempre). Incrementa {@code intentosIa}; al llegar a {@link #MAX_INTENTOS_IA} cae a
     * {@code REVISION_MANUAL} — el fallback a revisión humana ya validado en producción
     * (CLAUDE.MD §5.3.6, "reintentos acotados con fallback a revisión manual").
     */
    public void registrarIntentoFallido() {
        requireEnPendiente();
        this.intentosIa++;
        if (this.intentosIa >= MAX_INTENTOS_IA) {
            this.estadoValidacion = EstadoValidacion.REVISION_MANUAL;
        }
    }

    /** Un admin resuelve una evidencia caída en REVISION_MANUAL. */
    public void revisarManualmente(boolean aprobar, String notas) {
        if (estadoValidacion != EstadoValidacion.REVISION_MANUAL) {
            throw new IllegalStateException(
                    "Solo se revisa manualmente una evidencia en REVISION_MANUAL, esta esta en " + estadoValidacion);
        }
        this.estadoValidacion = aprobar ? EstadoValidacion.VALIDA : EstadoValidacion.RECHAZADA;
        this.notasValidacion = notas;
    }

    /** Un admin anula el veredicto (IA o manual) de una evidencia ya resuelta. */
    public void anularVeredicto(String notas) {
        if (estadoValidacion != EstadoValidacion.VALIDA && estadoValidacion != EstadoValidacion.RECHAZADA) {
            throw new IllegalStateException(
                    "Solo se anula el veredicto de una evidencia VALIDA o RECHAZADA, esta esta en " + estadoValidacion);
        }
        this.estadoValidacion = EstadoValidacion.ANULADA_ADMIN;
        this.notasValidacion = notas;
    }

    private void requireEnPendiente() {
        if (estadoValidacion != EstadoValidacion.PENDIENTE) {
            throw new IllegalStateException(
                    "La evidencia ya no esta pendiente de validacion IA, esta en " + estadoValidacion);
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

    /** Replica el CHECK {@code principal_solo_en_roca}. */
    private static void requirePrincipalSoloEnRoca(boolean esPrincipal, DestinoEvidencia destino) {
        if (esPrincipal && !(destino instanceof DestinoEvidencia.RocaDiaria)) {
            throw new IllegalArgumentException(
                    "esPrincipal solo aplica a evidencia de Roca Diaria (CHECK principal_solo_en_roca)");
        }
    }
}

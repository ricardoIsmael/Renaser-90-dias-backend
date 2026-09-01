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

    /** Puntos descontados cuando una evidencia de HABITO es rechazada (y revertidos si un
     * admin anula ese veredicto, ver {@link #anularVeredicto}). Valor confirmado contra el
     * backend viejo (`Backend90dias/RenaserBack/src/features/evidence-ai/service.ts:21`,
     * {@code INVALID_EVIDENCE_PENALTY_POINTS} — "misma magnitud que la rotura de
     * Santuario", ver {@code habits.SesionBloqueo.PENALIZACION_ROTURA_PUNTOS}). Quién
     * aplica la penalización por primera vez sigue sin implementarse en este alcance
     * (pregunta abierta #2 de docs/MODULO_EVIDENCE.md) — esta constante hoy solo la usa
     * la reversión. */
    public static final int PENALIZACION_EVIDENCIA_INVALIDA_PUNTOS = 10;

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
     *
     * <p>El {@code id} entra por parámetro, no se genera acá: la identidad viene del puerto
     * {@code IdGenerator} que inyecta el caso de uso ({@code EvidenciaService.registrar}).
     * Así {@code registrar} es referencialmente transparente y un test puede fijar el id que
     * espera, en vez de tener que caer a {@link #rehydrate} para lograrlo.
     */
    public static Evidencia registrar(EvidenciaId id, UserId participanteId, DestinoEvidencia destino,
                                       TipoEvidencia tipo, String bucket, String rutaStorage, String contenidoTexto,
                                       Instant timestampExif, Double gpsLat, Double gpsLng, boolean esPrincipal,
                                       Instant subidaEn, Clock clock) {
        Objects.requireNonNull(id, "id es obligatorio");
        Objects.requireNonNull(participanteId, "participanteId es obligatorio");
        Objects.requireNonNull(destino, "destino es obligatorio");
        Objects.requireNonNull(tipo, "tipo es obligatorio");
        Objects.requireNonNull(subidaEn, "subidaEn es obligatorio");
        requireMediaOTexto(tipo, bucket, rutaStorage, contenidoTexto);
        requireGpsCoherente(gpsLat, gpsLng);
        requirePrincipalSoloEnRoca(esPrincipal, destino);
        return new Evidencia(id, participanteId, destino, tipo, bucket, rutaStorage, contenidoTexto,
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

    /**
     * Un admin anula el veredicto (IA o manual) de una evidencia ya resuelta — es el
     * equivalente de este backend al "override" del backend viejo
     * (`evidence-ai/service.ts:174`, `overrideEvidence`/`aiOverriddenByAdmin`): P-14
     * (`docs/db/AUDITORIA_REDISENO_BD.md`) documenta que el enum {@code estado_validacion}
     * reemplazó esa bandera junto a otras 3, así que {@code ANULADA_ADMIN} YA es
     * "overrideada por admin" — no hace falta una columna ni un estado nuevo.
     *
     * <p><b>Idempotente</b>, igual que el viejo {@code overrideEvidence}: si ya estaba
     * {@code ANULADA_ADMIN}, no hace nada (dos admins resolviendo la misma fila no es un
     * error). Si la evidencia tenía una penalización aplicada, la apaga acá mismo y
     * devuelve {@code true} para que el llamador ({@code EvidenciaService}, el único que
     * conoce {@code points.api}) revierta los puntos — el dominio no importa `points`.
     *
     * @return {@code true} si había una penalización que revertir, {@code false} en
     *         cualquier otro caso (incluida la llamada repetida, que es un no-op)
     */
    public boolean anularVeredicto(String notas) {
        if (estadoValidacion == EstadoValidacion.ANULADA_ADMIN) {
            return false;
        }
        if (estadoValidacion != EstadoValidacion.VALIDA && estadoValidacion != EstadoValidacion.RECHAZADA) {
            throw new IllegalStateException(
                    "Solo se anula el veredicto de una evidencia VALIDA o RECHAZADA, esta esta en " + estadoValidacion);
        }
        boolean teniaPenalizacion = this.penalizacionAplicada;
        this.estadoValidacion = EstadoValidacion.ANULADA_ADMIN;
        this.notasValidacion = notas;
        this.penalizacionAplicada = false;
        return teniaPenalizacion;
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

package com.renaser.os.onboarding.domain.model.media;

import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.UserId;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Registro de un archivo YA SUBIDO a almacenamiento (tabla {@code medias_onboarding}). El
 * flujo (igual que {@code phasecontracts.ContratoFase} con {@code AlmacenamientoPort}) es:
 * el cliente pide una URL prefirmada de subida ({@link #rutaNueva}), sube directo a S3, y
 * recien despues confirma con {@link #registrar} que crea esta fila.
 */
@Getter
@Accessors(fluent = true)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@EqualsAndHashCode(of = "id")
public final class MediaOnboarding {

    public static final String BUCKET_DEFAULT = "onboarding-media";
    private static final String PREFIJO_RUTA = "onboarding";

    private final Long id;
    private final UserId usuarioId;
    private final String flujo;
    private final String clavePregunta;
    private final ClaseMedia clase;
    private final String bucket;
    private final String rutaStorage;
    private final String mime;
    private final Long tamanoBytes;
    private final BigDecimal duracionSegundos;
    private final String metadatos;
    private final Instant creadoEn;
    private final Instant actualizadoEn;

    public static MediaOnboarding registrar(UserId usuarioId, String flujo, String clavePregunta, ClaseMedia clase,
                                             String bucket, String rutaStorage, String mime, Long tamanoBytes,
                                             BigDecimal duracionSegundos, String metadatos, Clock clock) {
        Objects.requireNonNull(usuarioId, "usuarioId es obligatorio");
        Objects.requireNonNull(clase, "clase es obligatoria");
        if (bucket == null || bucket.isBlank()) {
            throw new IllegalArgumentException("bucket es obligatorio");
        }
        if (rutaStorage == null || rutaStorage.isBlank()) {
            throw new IllegalArgumentException("rutaStorage es obligatoria");
        }
        if (!rutaStorage.startsWith(prefijoDe(usuarioId))) {
            throw new IllegalArgumentException("rutaStorage no corresponde al usuario autenticado");
        }
        Instant ahora = clock.now();
        return new MediaOnboarding(null, usuarioId, flujo, clavePregunta, clase, bucket, rutaStorage, mime,
                tamanoBytes, duracionSegundos, metadatos, ahora, ahora);
    }

    /** Solo para el adaptador de persistencia: reconstruye una fila ya existente. */
    public static MediaOnboarding rehydrate(Long id, UserId usuarioId, String flujo, String clavePregunta,
                                             ClaseMedia clase, String bucket, String rutaStorage, String mime,
                                             Long tamanoBytes, BigDecimal duracionSegundos, String metadatos,
                                             Instant creadoEn, Instant actualizadoEn) {
        return new MediaOnboarding(id, usuarioId, flujo, clavePregunta, clase, bucket, rutaStorage, mime,
                tamanoBytes, duracionSegundos, metadatos, creadoEn, actualizadoEn);
    }

    /**
     * Ruta nueva, no deterministica a proposito (a diferencia de {@code ContratoFase.rutaFirma}):
     * un aprendiz puede subir varias media para la misma pregunta (reintentos de audio, por
     * ejemplo), asi que cada subida necesita su propio nombre de archivo.
     *
     * <p>Ese discriminador por subida entra como parametro, no lo sortea el dominio: el metodo
     * es el gemelo de {@link #registrar} —{@code registrar} valida contra {@link #prefijoDe} la
     * misma ruta que este construye—, asi que la forma de la ruta se queda aca, en un solo lugar,
     * y lo unico que se va afuera es el azar. Lo genera el caso de uso con el puerto
     * {@link com.renaser.os.shared.domain.IdGenerator}, igual que la identidad de cualquier otro
     * agregado (CLAUDE.MD §5.4.7: {@code domain/} sin aleatoriedad). No es identidad de fila —
     * el id de {@code medias_onboarding} lo asigna la base y es un {@code Long}— pero la razon
     * para sacarlo es la misma: sin esto, dos llamadas con los mismos argumentos devuelven
     * resultados distintos.
     */
    public static String rutaNueva(UUID identificadorSubida, UserId usuarioId, ClaseMedia clase) {
        Objects.requireNonNull(identificadorSubida, "identificadorSubida es obligatorio");
        Objects.requireNonNull(usuarioId, "usuarioId es obligatorio");
        Objects.requireNonNull(clase, "clase es obligatoria");
        return prefijoDe(usuarioId) + clase.name().toLowerCase() + "/" + identificadorSubida;
    }

    /**
     * Todo {@code rutaStorage} que un actor registre debe caer bajo su propio prefijo — evita
     * que {@link #registrar} acepte una ruta con el UUID de otro usuario (no hay forma de
     * verificar contra la URL prefirmada real sin guardar estado de la emision, pero esto cierra
     * el caso concreto de suplantacion de {@code usuarioId} en la ruta).
     */
    private static String prefijoDe(UserId usuarioId) {
        return PREFIJO_RUTA + "/" + usuarioId + "/";
    }

    @Override
    public String toString() {
        return "MediaOnboarding[" + id + ", " + usuarioId + ", " + clase + ", " + bucket + "/" + rutaStorage + "]";
    }
}

package com.renaser.os.habits.domain.model.guia;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.experimental.Accessors;

import java.time.Instant;
import java.util.Objects;

/**
 * Adjunto (enlace, imagen o audio) de una seccion de guia (tabla `adjuntos_guia`).
 * Invariante XOR: ENLACE lleva {@code url} y nunca {@code rutaStorage}; IMAGEN/AUDIO
 * llevan {@code rutaStorage} (bucket habit-guide-media, D-34) y nunca {@code url} —
 * "jamas una URL" para lo que sale de S3 (regla heredada del baseline SQL).
 */
@Getter
@Accessors(fluent = true)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@EqualsAndHashCode(of = "id")
public final class AdjuntoGuia {

    private final AdjuntoGuiaId id;
    private final GuiaHabitoId guiaId;
    private final SeccionGuia seccion;
    private final TipoMedioGuia tipoMedio;
    private final String url;
    private final String rutaStorage;
    private String mime;
    private Integer tamanoBytes;
    private String nombreOriginal;
    private String titulo;
    private int orden;
    private final Instant creadoEn;
    private Instant actualizadoEn;

    public static AdjuntoGuia deEnlace(GuiaHabitoId guiaId, SeccionGuia seccion, String url, String titulo,
                                        int orden, Instant ahora) {
        Objects.requireNonNull(guiaId, "guiaId es obligatorio");
        Objects.requireNonNull(seccion, "seccion es obligatoria");
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("url es obligatoria para un adjunto ENLACE");
        }
        return new AdjuntoGuia(AdjuntoGuiaId.newId(), guiaId, seccion, TipoMedioGuia.ENLACE, url, null, null, null,
                null, titulo, orden, ahora, ahora);
    }

    public static AdjuntoGuia deArchivo(GuiaHabitoId guiaId, SeccionGuia seccion, TipoMedioGuia tipoMedio,
                                         String rutaStorage, String mime, Integer tamanoBytes, String nombreOriginal,
                                         String titulo, int orden, Instant ahora) {
        Objects.requireNonNull(guiaId, "guiaId es obligatorio");
        Objects.requireNonNull(seccion, "seccion es obligatoria");
        if (tipoMedio == TipoMedioGuia.ENLACE) {
            throw new IllegalArgumentException("Un adjunto de archivo no puede ser ENLACE");
        }
        if (rutaStorage == null || rutaStorage.isBlank()) {
            throw new IllegalArgumentException("rutaStorage es obligatoria para un adjunto " + tipoMedio);
        }
        return new AdjuntoGuia(AdjuntoGuiaId.newId(), guiaId, seccion, tipoMedio, null, rutaStorage, mime,
                tamanoBytes, nombreOriginal, titulo, orden, ahora, ahora);
    }

    /** Solo para el adaptador de persistencia. */
    public static AdjuntoGuia rehydrate(AdjuntoGuiaId id, GuiaHabitoId guiaId, SeccionGuia seccion,
                                         TipoMedioGuia tipoMedio, String url, String rutaStorage, String mime,
                                         Integer tamanoBytes, String nombreOriginal, String titulo, int orden,
                                         Instant creadoEn, Instant actualizadoEn) {
        return new AdjuntoGuia(id, guiaId, seccion, tipoMedio, url, rutaStorage, mime, tamanoBytes, nombreOriginal,
                titulo, orden, creadoEn, actualizadoEn);
    }

    @Override
    public String toString() {
        return "AdjuntoGuia[" + id + ", " + guiaId + ", " + seccion + ", " + tipoMedio + "]";
    }
}

package com.renaser.os.academy.domain.model.curso;

import java.time.Instant;
import java.util.Objects;

/**
 * Una leccion: video/audio + cuerpo en HTML/Markdown importado de Skool.
 * Hereda el gate de dia de su seccion (si tiene una) — la leccion en si no
 * agrega ningun gate propio (fiel al repo viejo: `LeccionLite.dia_desbloqueo`
 * siempre viene heredado de la seccion, nunca de la leccion, ver
 * `repository.ts:236-238`).
 */
public final class Leccion {

    private final LeccionId id;
    private final CursoId cursoId;
    private final SeccionCursoId seccionId;
    private final String titulo;
    private final int orden;
    private final String cuerpoHtml;
    private final String cuerpoMd;
    private final TipoVideoLeccion videoTipo;
    private final String videoUrl;
    private final String videoMiniaturaUrl;
    private final Long videoDuracionMs;
    private final Instant creadoEn;
    private final Instant actualizadoEn;

    public Leccion(LeccionId id, CursoId cursoId, SeccionCursoId seccionId, String titulo, int orden,
                    String cuerpoHtml, String cuerpoMd, TipoVideoLeccion videoTipo, String videoUrl,
                    String videoMiniaturaUrl, Long videoDuracionMs, Instant creadoEn, Instant actualizadoEn) {
        this.id = Objects.requireNonNull(id, "id es obligatorio");
        this.cursoId = Objects.requireNonNull(cursoId, "cursoId es obligatorio");
        this.seccionId = seccionId;
        this.titulo = Objects.requireNonNull(titulo, "titulo es obligatorio");
        this.orden = orden;
        this.cuerpoHtml = cuerpoHtml;
        this.cuerpoMd = cuerpoMd;
        this.videoTipo = videoTipo;
        this.videoUrl = videoUrl;
        this.videoMiniaturaUrl = videoMiniaturaUrl;
        this.videoDuracionMs = videoDuracionMs;
        this.creadoEn = Objects.requireNonNull(creadoEn, "creadoEn es obligatorio");
        this.actualizadoEn = Objects.requireNonNull(actualizadoEn, "actualizadoEn es obligatorio");
    }

    /** true si hay contenido de lectura (para el indicador `tiene_cuerpo` del arbol del curso). */
    public boolean tieneCuerpo() {
        return cuerpoMd != null && !cuerpoMd.isBlank();
    }

    public LeccionId id() {
        return id;
    }

    public CursoId cursoId() {
        return cursoId;
    }

    public SeccionCursoId seccionId() {
        return seccionId;
    }

    public String titulo() {
        return titulo;
    }

    public int orden() {
        return orden;
    }

    public String cuerpoHtml() {
        return cuerpoHtml;
    }

    public String cuerpoMd() {
        return cuerpoMd;
    }

    public TipoVideoLeccion videoTipo() {
        return videoTipo;
    }

    public String videoUrl() {
        return videoUrl;
    }

    public String videoMiniaturaUrl() {
        return videoMiniaturaUrl;
    }

    public Long videoDuracionMs() {
        return videoDuracionMs;
    }

    public Instant creadoEn() {
        return creadoEn;
    }

    public Instant actualizadoEn() {
        return actualizadoEn;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Leccion leccion)) {
            return false;
        }
        return id.equals(leccion.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return "Leccion[" + id + ", " + titulo + "]";
    }
}

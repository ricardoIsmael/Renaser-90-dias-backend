package com.renaser.os.rag.infrastructure.adapter.in.rest;

import jakarta.validation.constraints.NotBlank;

import java.util.Map;

/** {@code clase}, {@code documentoId} y {@code leccionId} son opcionales (ver {@code base_conocimiento}). */
public record IndexarConocimientoRequest(@NotBlank String tipoFuente, String clase, String documentoId,
                                          String leccionId, @NotBlank String contenido,
                                          Map<String, String> metadatos) {
}

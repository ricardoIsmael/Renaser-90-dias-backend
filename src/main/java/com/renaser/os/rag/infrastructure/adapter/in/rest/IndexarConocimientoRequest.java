package com.renaser.os.rag.infrastructure.adapter.in.rest;

import jakarta.validation.constraints.NotBlank;

import java.util.Map;

/**
 * {@code clase}, {@code documentoId} y {@code leccionId} son opcionales (ver {@code base_conocimiento}).
 *
 * <p><b>{@code leccionId} decide quién lo ve (issue #62, V164).</b> Con lección, el fragmento es de la formación de esa lección y se borra cuando se borra
 * la lección. Sin lección es conocimiento GENERAL: lo ven todas las formaciones. Omitirlo es decir eso a propósito.
 */
public record IndexarConocimientoRequest(@NotBlank String tipoFuente, String clase, String documentoId,
                                          String leccionId, @NotBlank String contenido,
                                          Map<String, String> metadatos) {
}

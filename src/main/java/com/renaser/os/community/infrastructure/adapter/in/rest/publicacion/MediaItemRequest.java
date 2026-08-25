package com.renaser.os.community.infrastructure.adapter.in.rest.publicacion;

import com.renaser.os.community.application.ports.in.publicacion.PublicarUseCase.ArchivoEntrada;
import com.renaser.os.community.domain.model.publicacion.MediaPublicacion;
import jakarta.validation.constraints.NotBlank;

/**
 * La app publicada manda una URL absoluta directa (sube el archivo por su cuenta antes de
 * publicar, wall/schema.ts:27-30) — no bucket+ruta. CM-06: se traduce aca, en la unica
 * frontera que puede saber de esta compatibilidad hacia atras; el dominio y el caso de uso
 * solo ven bucket+ruta (mismo criterio que {@code AbrirTicketSoporteRequest.bucketEfectivo}
 * en `support`).
 */
public record MediaItemRequest(@NotBlank String url, @NotBlank String mimeType) {

    public ArchivoEntrada aArchivoEntrada() {
        return new ArchivoEntrada(MediaPublicacion.BUCKET_DEFAULT, url, mimeType);
    }
}

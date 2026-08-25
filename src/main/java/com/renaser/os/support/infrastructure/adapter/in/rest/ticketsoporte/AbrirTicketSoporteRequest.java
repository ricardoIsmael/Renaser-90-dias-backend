package com.renaser.os.support.infrastructure.adapter.in.rest.ticketsoporte;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AbrirTicketSoporteRequest(
        String category,
        @NotBlank @Size(max = 200) String subject,
        @NotBlank @Size(min = 10, max = 4000) String message,
        @Size(max = 4000) String clientLog,
        String attachmentBucket,
        String attachmentPath,
        String attachmentUrl) {

    private static final String BUCKET_HEREDADO = "Evidence";

    /**
     * El cliente publicado sube el adjunto por su cuenta y manda la URL; el cliente nuevo
     * pide una URL prefirmada y manda bucket + ruta. Aceptar las dos formas evita que el
     * adjunto de quien todavia no actualizo la app se pierda sin aviso.
     */
    public String bucketEfectivo() {
        if (attachmentBucket != null && !attachmentBucket.isBlank()) {
            return attachmentBucket;
        }
        return rutaDesdeUrl() == null ? null : BUCKET_HEREDADO;
    }

    public String rutaEfectiva() {
        if (attachmentPath != null && !attachmentPath.isBlank()) {
            return attachmentPath;
        }
        return rutaDesdeUrl();
    }

    private String rutaDesdeUrl() {
        if (attachmentUrl == null || attachmentUrl.isBlank()) {
            return null;
        }
        int marca = attachmentUrl.indexOf("/" + BUCKET_HEREDADO + "/");
        if (marca < 0) {
            return attachmentUrl;
        }
        return attachmentUrl.substring(marca + BUCKET_HEREDADO.length() + 2);
    }
}

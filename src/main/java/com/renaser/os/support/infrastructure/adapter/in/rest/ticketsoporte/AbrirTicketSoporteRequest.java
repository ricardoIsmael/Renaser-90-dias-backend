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
            /* Corregido 2026-09-18. Aca decia `return attachmentUrl`: sin la marca se devolvia la
               URL ENTERA como clave de objeto. Es exactamente la forma de E-79 —una URL absoluta
               entrando donde va una clave—, el bug que el Muro ya habia corregido. Devolver null
               deja que el ticket se abra SIN adjunto en vez de persistir una clave inventada. */
            return null;
        }
        return attachmentUrl.substring(marca + BUCKET_HEREDADO.length() + 2);
    }
}

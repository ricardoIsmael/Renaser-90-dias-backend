package com.renaser.os.support.domain.model.ticketsoporte;

public record AdjuntoSoporte(String bucket, String ruta) {

    public AdjuntoSoporte {
        if (bucket == null || bucket.isBlank()) {
            throw new IllegalArgumentException("El bucket del adjunto no puede ser vacio");
        }
        if (ruta == null || ruta.isBlank()) {
            throw new IllegalArgumentException("La ruta del adjunto no puede ser vacia");
        }
    }
}

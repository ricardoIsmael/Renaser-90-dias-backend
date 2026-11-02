package com.renaser.os.chat.application.ports.out.bienvenida;

/**
 * Las imágenes que sube Administración para una formación (spec 035): abrirlas, revisarlas antes de que se usen y servirlas como JPEG cuadrado.
 * La que sale en cada formación la decide la bitácora ({@link CambiosDeBienvenidaPort}); acá solo están los píxeles.
 */
public interface ImagenDeLaFormacionPort {

    /**
     * Trae la imagen subida en {@code ruta}, la revisa (formato, peso y medidas; {@link com.renaser.os.chat.domain.model.bienvenida.PortadaDeBienvenida})
     * y la deja lista para servir. Abre la imagen: no se llama dentro de una transacción.
     *
     * @throws IllegalArgumentException si la imagen no sirve, con el motivo en palabras simples
     * @throws java.util.NoSuchElementException si no hay nada subido en esa ruta
     */
    void revisar(String ruta);

    /**
     * La imagen de {@code ruta} (ya revisada o vigente) como JPEG cuadrado de {@link com.renaser.os.chat.domain.model.bienvenida.ImagenDeLaFormacion#LADO_SERVIDO} px.
     *
     * @throws java.util.NoSuchElementException si ya no hay nada en esa ruta
     */
    Imagen jpegDe(String ruta);

    /**
     * @param jpeg   compartida entre pedidos: nadie la modifica
     * @param huella cambia si y solo si cambia la imagen (el ETag)
     */
    record Imagen(byte[] jpeg, String huella) {
    }
}

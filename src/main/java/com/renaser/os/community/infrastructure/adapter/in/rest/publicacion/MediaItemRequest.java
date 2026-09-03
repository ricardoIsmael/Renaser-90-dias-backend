package com.renaser.os.community.infrastructure.adapter.in.rest.publicacion;

import com.renaser.os.community.application.ports.in.publicacion.PublicarUseCase.ArchivoEntrada;
import com.renaser.os.community.domain.model.publicacion.MediaPublicacion;
import jakarta.validation.constraints.NotBlank;

import java.net.URI;
import java.net.URISyntaxException;

/**
 * La app publicada manda una URL absoluta directa (sube el archivo por su cuenta antes de
 * publicar, wall/schema.ts:27-30) — no bucket+ruta. CM-06: se traduce aca, en la unica
 * frontera que puede saber de esta compatibilidad hacia atras; el dominio y el caso de uso
 * solo ven bucket+ruta (mismo criterio que {@code AbrirTicketSoporteRequest.bucketEfectivo}
 * en `support`).
 *
 * <p><b>El defecto que cierra (E-79).</b> Hasta el 2026-09-02 esa traduccion estaba prometida
 * en este javadoc pero <b>no existia en el codigo</b>: {@code aArchivoEntrada()} metia la URL
 * absoluta tal cual en el campo {@code ruta}. Como {@code PublicacionMuroService.aVista()} pasa
 * esa ruta a {@code AlmacenamientoPort.firmarLectura}, que la trata como <b>clave de objeto de
 * S3</b>, la URL que recibia el celular quedaba anidada sobre si misma
 * ({@code .../https%3A//s3-renaser90dias.s3.amazonaws.com/muro/fotos/...}) y S3 respondia 404.
 * La foto se subia bien y quedaba en el bucket; lo que estaba roto era leerla. Es la misma
 * familia que E-57 (avatares) al reves: aquel guardaba una URL <i>firmada</i> donde iba una
 * clave, este guardaba una URL <i>absoluta</i> donde va una clave — por eso el barrido de E-57,
 * que buscaba URLs firmadas persistidas, no lo encontro.
 */
public record MediaItemRequest(@NotBlank String url, @NotBlank String mimeType) {

    /**
     * Prefijo con el que {@code PublicacionMuroService.rutaDeMedia} arma toda clave del Muro
     * ({@code muro/fotos/...}, {@code muro/videos/...}). Es la marca que permite recortar tanto
     * una URL virtual-hosted ({@code https://bucket.s3.amazonaws.com/muro/...}) como una
     * path-style ({@code https://s3.amazonaws.com/bucket/muro/...}) sin que este DTO tenga que
     * conocer el nombre del bucket — mismo truco que {@code rutaDesdeUrl()} en `support`.
     */
    private static final String PREFIJO_MURO = "muro/";

    public ArchivoEntrada aArchivoEntrada() {
        return new ArchivoEntrada(MediaPublicacion.BUCKET_DEFAULT, aClaveDeObjeto(url), mimeType);
    }

    /**
     * Devuelve la clave de objeto de S3 correspondiente a lo que haya mandado el cliente, acepte
     * la forma que acepte: una clave ya limpia pasa igual, una URL absoluta se recorta.
     *
     * <p>Lo que se guarda tiene que ser SIEMPRE una clave, nunca una URL: la URL de lectura se
     * firma en cada respuesta y por eso caduca; la clave es lo unico permanente (P-03 del
     * baseline, y la leccion de E-57).
     */
    static String aClaveDeObjeto(String valor) {
        // En SigV4 todo lo que caduca vive despues del '?'. Se corta primero para que una URL
        // prefirmada -que nunca deberia llegar, pero llego en E-57- no entre como parte de la clave.
        String sinFirma = valor.split("\\?", 2)[0];
        if (!sinFirma.regionMatches(true, 0, "http", 0, 4)) {
            return sinFirma; // ya es una clave: es lo que manda el cliente nuevo
        }
        String camino = caminoDe(sinFirma, valor);
        int marca = camino.indexOf(PREFIJO_MURO);
        // Sin la marca no se puede saber donde termina el bucket y empieza la clave, asi que se
        // devuelve el camino entero en vez de adivinar: es lo que hace `support` en el mismo caso.
        return marca < 0 ? camino : camino.substring(marca);
    }

    private static String caminoDe(String sinFirma, String original) {
        String camino;
        try {
            camino = new URI(sinFirma).getPath(); // getPath() ya viene decodificado (%2F -> /)
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("url de media invalida: " + original, e);
        }
        if (camino == null || camino.isBlank() || camino.equals("/")) {
            throw new IllegalArgumentException("url de media sin ruta de archivo: " + original);
        }
        return camino.startsWith("/") ? camino.substring(1) : camino;
    }
}

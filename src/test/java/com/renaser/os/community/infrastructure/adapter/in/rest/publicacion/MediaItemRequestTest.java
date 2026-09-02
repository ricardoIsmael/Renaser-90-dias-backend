package com.renaser.os.community.infrastructure.adapter.in.rest.publicacion;

import com.renaser.os.community.domain.model.publicacion.MediaPublicacion;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Fija E-79: lo que se guarda en {@code medias_publicacion.ruta_storage} tiene que ser una CLAVE
 * de objeto de S3, nunca una URL. Guardar una URL deja la foto en 404 para siempre aunque el
 * archivo exista, porque {@code PublicacionMuroService.aVista()} pasa ese valor a
 * {@code firmarLectura}, que lo trata como clave.
 *
 * <p>Igual que las pruebas que cerraron E-57, estas miran la <b>propiedad</b> ("lo guardado no es
 * una URL") y no un valor concreto: es lo que hace que la prueba siga matando el defecto si
 * manana cambia el nombre del bucket o la region.
 */
class MediaItemRequestTest {

    private static final String CLAVE = "muro/fotos/256090d6-3be1-4326-b8d0-4b6a11190175/0a709f46-73d0-4f19-b3a0-d5c3f4e033b4";

    @Test
    @DisplayName("la URL absoluta que manda la app publicada se guarda como clave, no como URL")
    void urlAbsolutaSeConvierteEnClave() {
        var request = new MediaItemRequest("https://s3-renaser90dias.s3.amazonaws.com/" + CLAVE, "image/jpeg");

        assertThat(request.aArchivoEntrada().ruta()).isEqualTo(CLAVE);
    }

    @Test
    @DisplayName("el cliente nuevo, que ya manda la clave, pasa sin que se le toque nada")
    void claveLimpiaPasaIgual() {
        var request = new MediaItemRequest(CLAVE, "image/jpeg");

        assertThat(request.aArchivoEntrada().ruta()).isEqualTo(CLAVE);
    }

    @Test
    @DisplayName("una URL prefirmada pierde la firma: lo que caduca nunca se persiste (leccion de E-57)")
    void urlPrefirmadaPierdeLaFirma() {
        String prefirmada = "https://s3-renaser90dias.s3.us-east-1.amazonaws.com/" + CLAVE
                + "?X-Amz-Algorithm=AWS4-HMAC-SHA256&X-Amz-Expires=900&X-Amz-Signature=deadbeef";

        var ruta = new MediaItemRequest(prefirmada, "image/jpeg").aArchivoEntrada().ruta();

        assertThat(ruta).isEqualTo(CLAVE);
        assertThat(ruta).doesNotContain("X-Amz-Signature");
    }

    @Test
    @DisplayName("la forma path-style deja el bucket adelante y tambien se recorta")
    void urlPathStyleSeRecortaEnElPrefijoDelMuro() {
        var request = new MediaItemRequest("https://s3.amazonaws.com/s3-renaser90dias/" + CLAVE, "image/jpeg");

        assertThat(request.aArchivoEntrada().ruta()).isEqualTo(CLAVE);
    }

    @Test
    @DisplayName("los caracteres escapados vuelven a su forma real: %2F es una barra, no texto")
    void elCaminoLlegaDecodificado() {
        var request = new MediaItemRequest("https://s3-renaser90dias.s3.amazonaws.com/muro%2Ffotos%2Fa%2Fb",
                "image/jpeg");

        assertThat(request.aArchivoEntrada().ruta()).isEqualTo("muro/fotos/a/b");
    }

    @Test
    @DisplayName("ninguna forma de entrada puede terminar guardando algo que empiece con http")
    void ningunaEntradaProduceUnaUrl() {
        for (String entrada : new String[] {
                "https://s3-renaser90dias.s3.amazonaws.com/" + CLAVE,
                "http://s3-renaser90dias.s3.amazonaws.com/" + CLAVE,
                "HTTPS://S3-RENASER90DIAS.S3.AMAZONAWS.COM/" + CLAVE,
                "https://s3.amazonaws.com/s3-renaser90dias/" + CLAVE,
                CLAVE }) {
            assertThat(new MediaItemRequest(entrada, "image/jpeg").aArchivoEntrada().ruta())
                    .as("entrada: %s", entrada)
                    .doesNotStartWithIgnoringCase("http");
        }
    }

    @Test
    @DisplayName("una URL sin ruta de archivo se rechaza en vez de guardar una clave vacia")
    void urlSinRutaSeRechaza() {
        assertThatThrownBy(() -> new MediaItemRequest("https://s3-renaser90dias.s3.amazonaws.com/", "image/jpeg")
                .aArchivoEntrada())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("sin ruta de archivo");
    }

    @Test
    @DisplayName("el bucket sigue siendo el default del Muro: solo cambia la ruta")
    void elBucketNoCambia() {
        var entrada = new MediaItemRequest("https://s3-renaser90dias.s3.amazonaws.com/" + CLAVE, "image/jpeg")
                .aArchivoEntrada();

        assertThat(entrada.bucket()).isEqualTo(MediaPublicacion.BUCKET_DEFAULT);
        assertThat(entrada.mime()).isEqualTo("image/jpeg");
    }
}

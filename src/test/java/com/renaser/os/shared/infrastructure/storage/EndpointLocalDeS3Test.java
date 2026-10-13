package com.renaser.os.shared.infrastructure.storage;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * El S3 local de desarrollo (`AWS_S3_ENDPOINT`, servicio `fotos-local` de docker-compose.yml): con endpoint las URL
 * van a ese host con la ruta del bucket; sin endpoint, al S3 de AWS como siempre (producción no cambia).
 */
class EndpointLocalDeS3Test {

    @Test
    @DisplayName("sin endpoint, la URL pública es la de AWS de siempre")
    void sinEndpointVaAAws() {
        var adaptador = new S3AlmacenamientoAdapter(null, null, "renaser90dias-prod", "us-east-1", "");

        assertThat(adaptador.urlPublica("evidencia/a.jpg").toString())
                .startsWith("https://renaser90dias-prod.s3")
                .endsWith("amazonaws.com/evidencia/a.jpg");
    }

    @Test
    @DisplayName("con endpoint local, la URL pública usa ese host y la ruta del bucket")
    void conEndpointUsaElHostLocalYLaRutaDelBucket() {
        var adaptador = new S3AlmacenamientoAdapter(null, null, "renaser-local", "us-east-1", "http://localhost:9002");

        assertThat(adaptador.urlPublica("evidencia/a.jpg").toString())
                .isEqualTo("http://localhost:9002/renaser-local/evidencia/a.jpg");
    }

    @Test
    @DisplayName("un endpoint vacío o en blanco cuenta como no configurado")
    void endpointEnBlancoNoCuenta() {
        assertThat(AlmacenamientoS3Config.endpointLocal("  ")).isEmpty();
        assertThat(AlmacenamientoS3Config.endpointLocal(null)).isEmpty();
        assertThat(AlmacenamientoS3Config.endpointLocal(" http://localhost:9002 ")).isPresent();
    }
}

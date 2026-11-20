package com.renaser.os.shared.infrastructure.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

/**
 * N-3 contra un S3 compatible LOCAL (MinIO), nunca producción: sube con una URL prefirmada mandando exactamente lo que manda la
 * app 1.8.0 (un PUT con solo {@code Content-Type}), mide el objeto con {@code pesoDe} y baja la lectura como descarga.
 *
 * <p>Solo corre si hay {@code TEST_S3_ENDPOINT} (p. ej. {@code http://localhost:9002}), {@code TEST_S3_BUCKET} y las credenciales
 * {@code AWS_ACCESS_KEY_ID}/{@code AWS_SECRET_ACCESS_KEY} en el entorno; sin ellos se omite, así el CI no lo necesita.
 */
class S3LocalSubidaPrefirmadaTest {

    @Test
    void laSubidaDeLaAppFuncionaSeMideYSeBajaComoDescarga() throws Exception {
        String endpoint = System.getenv("TEST_S3_ENDPOINT");
        String bucket = System.getenv("TEST_S3_BUCKET");
        assumeTrue(endpoint != null && bucket != null && System.getenv("AWS_ACCESS_KEY_ID") != null,
                "sin S3 local configurado");
        assumeTrue(endpoint.contains("localhost") || endpoint.contains("127.0.0.1"), "solo contra un S3 local");

        var local = java.util.Optional.of(URI.create(endpoint));
        S3Presigner presigner = S3Presigner.builder().region(Region.US_EAST_1)
                .serviceConfiguration(AlmacenamientoS3Config.configuracionDe(local))
                .credentialsProvider(DefaultCredentialsProvider.create()).endpointOverride(URI.create(endpoint)).build();
        S3Client cliente = S3Client.builder().region(Region.US_EAST_1)
                .serviceConfiguration(AlmacenamientoS3Config.configuracionDe(local))
                .credentialsProvider(DefaultCredentialsProvider.create()).endpointOverride(URI.create(endpoint)).build();
        var adaptador = new S3AlmacenamientoAdapter(presigner, cliente, bucket, "us-east-1", endpoint);
        String ruta = "prueba-n3/" + UUID.randomUUID();
        byte[] bytes = new byte[2048];

        try {
            URI subida = adaptador.firmarSubida(ruta, "image/jpeg", Duration.ofMinutes(2));
            HttpClient http = HttpClient.newHttpClient();
            // Lo mismo que manda la app: PUT con solo Content-Type.
            var puesta = http.send(HttpRequest.newBuilder(subida).header("Content-Type", "image/jpeg")
                    .PUT(HttpRequest.BodyPublishers.ofByteArray(bytes)).build(), HttpResponse.BodyHandlers.discarding());
            assertThat(puesta.statusCode()).isEqualTo(200);

            assertThat(adaptador.pesoDe(ruta)).hasValue(2048L);
            assertThat(adaptador.pesoDe(ruta + "-no-existe")).isEmpty();

            var lectura = http.send(HttpRequest.newBuilder(adaptador.firmarLecturaComoDescarga(ruta, Duration.ofMinutes(2))).GET().build(),
                    HttpResponse.BodyHandlers.discarding());
            assertThat(lectura.statusCode()).isEqualTo(200);
            assertThat(lectura.headers().firstValue("Content-Disposition")).hasValue("attachment");
        } finally {
            adaptador.borrar(ruta);
        }
    }
}

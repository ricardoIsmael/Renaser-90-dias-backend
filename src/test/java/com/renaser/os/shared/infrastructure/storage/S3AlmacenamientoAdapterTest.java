package com.renaser.os.shared.infrastructure.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.net.URI;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

/**
 * Prueba del adaptador real de S3 <b>sin red y sin credenciales de verdad</b>.
 *
 * <p>Se puede porque el {@code S3Presigner} del SDK v2 <b>firma localmente</b>: calcula la firma
 * SigV4 con HMAC sobre la clave secreta y arma la URL, sin hablar con AWS en ningun momento. Una
 * clave inventada produce una firma valida en forma (la rechazaria AWS al usarla, que es otra
 * cosa), asi que la URL sale igual y es inspeccionable — que es exactamente lo que hay que
 * verificar: que se firma contra el bucket configurado, que la ruta pedida esta en la URL y que
 * la validez que le pasa el caso de uso se respeta.
 *
 * <p><b>Lo que esta prueba NO puede cubrir, a proposito:</b> que AWS acepte la firma, que el
 * objeto exista, que el principal tenga los permisos IAM. Eso exige red y una cuenta real; se
 * verifica en el despliegue, no aca. {@code borrar} es el unico verbo que ejecuta el servidor, y
 * se prueba con un doble: lo que importa es que arme el {@code DeleteObjectRequest} contra el
 * bucket y la clave correctos, no que Amazon responda.
 */
@ExtendWith(MockitoExtension.class)
class S3AlmacenamientoAdapterTest {

    private static final String BUCKET = "s3-renaser90dias";
    private static final Region REGION = Region.US_EAST_1;

    @Mock
    private S3Client s3Client;

    /** Credenciales inventadas: el presigner solo las necesita para calcular el HMAC. */
    private static S3Presigner presignerConCredencialesDeMentira() {
        return S3Presigner.builder()
                .region(REGION)
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create("AKIAIOSFODNN7EXAMPLE",
                                "wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY")))
                .build();
    }

    private S3AlmacenamientoAdapter adapter() {
        return new S3AlmacenamientoAdapter(presignerConCredencialesDeMentira(), s3Client, BUCKET);
    }

    @Test
    void firmaLaLecturaContraElBucketConfiguradoYConservaLaRutaPedida() {
        URI url = adapter().firmarLectura("audioterapia/pista-01.mp3", Duration.ofMinutes(15));

        assertThat(url.getHost()).contains(BUCKET);
        assertThat(url.getPath()).isEqualTo("/audioterapia/pista-01.mp3");
        assertThat(url.getQuery()).contains("X-Amz-Algorithm=AWS4-HMAC-SHA256");
        assertThat(url.getQuery()).contains("X-Amz-Signature=");
        // La firma es la que convierte esto en una credencial: sin ella la URL no abre nada, y
        // era justamente lo que faltaba cuando el unico adaptador devolvia `about:blank`.
        assertThat(url.toString()).doesNotContain("about:blank");
    }

    @Test
    void respetaLaValidezQueLePasaElCasoDeUso() {
        S3AlmacenamientoAdapter adapter = adapter();

        URI corta = adapter.firmarLectura("muro/foto.jpg", Duration.ofMinutes(5));
        URI larga = adapter.firmarLectura("muro/foto.jpg", Duration.ofHours(2));

        // X-Amz-Expires va en segundos. Que cada caso de uso elija la suya no es un detalle: una
        // URL prefirmada es una credencial de acceso a ese objeto y vive hasta que vence.
        assertThat(corta.getQuery()).contains("X-Amz-Expires=300");
        assertThat(larga.getQuery()).contains("X-Amz-Expires=7200");
    }

    @Test
    void laSubidaFirmaTambienElTipoDeContenido() {
        URI url = adapter().firmarSubida("testimonios/video.mp4", "video/mp4", Duration.ofMinutes(10));

        assertThat(url.getHost()).contains(BUCKET);
        assertThat(url.getPath()).isEqualTo("/testimonios/video.mp4");
        // content-type entre los encabezados firmados: el cliente tiene que mandar EXACTAMENTE
        // ese Content-Type en el PUT o S3 rechaza la firma. Es lo que impide pedir una URL para
        // una imagen y subir otra cosa con ella.
        assertThat(url.getQuery()).contains("X-Amz-SignedHeaders=");
        assertThat(url.getQuery()).contains("content-type");
    }

    @Test
    void borrarPideElObjetoExactoContraElBucketConfigurado() {
        adapter().borrar("adjuntos-tickets/captura.png");

        ArgumentCaptor<DeleteObjectRequest> captor = ArgumentCaptor.forClass(DeleteObjectRequest.class);
        verify(s3Client).deleteObject(captor.capture());
        assertThat(captor.getValue().bucket()).isEqualTo(BUCKET);
        assertThat(captor.getValue().key()).isEqualTo("adjuntos-tickets/captura.png");
    }

    /**
     * El puerto promete borrado idempotente. Sale gratis: S3 responde 204 tambien cuando la clave
     * no existe, asi que el adaptador no consulta antes ni traga ninguna excepcion — no hay
     * ningun {@code catch} que pudiera ocultar un fallo real.
     */
    @Test
    void borrarNoConsultaAntesNiHaceUnaSegundaLlamada() {
        adapter().borrar("inexistente.txt");

        verify(s3Client).deleteObject(any(DeleteObjectRequest.class));
        verifyNoMoreInteractions(s3Client);
    }
}

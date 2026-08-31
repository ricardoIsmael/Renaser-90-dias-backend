package com.renaser.os.shared.infrastructure.storage;

import com.renaser.os.shared.application.ports.out.AlmacenamientoPort;
import java.net.URI;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

/**
 * Implementacion real de {@link AlmacenamientoPort} contra Amazon S3 (D-34).
 *
 * <p><b>Que cierra:</b> hasta el 2026-08-31 el unico adaptador era
 * {@link NoOpAlmacenamientoAdapter}, que devolvia {@code about:blank#pendiente-s3/<ruta>} para
 * toda URL de descarga. Los 9 servicios que ya llamaban a {@code firmarLectura} — audioterapia,
 * portadas de cursos y de eventos, media del muro, testimonios, firma de contratos, adjuntos de
 * tickets y avatares — entregaban al cliente movil una URL que no apunta a nada. Los archivos ya
 * estaban en S3 desde D-50; lo que faltaba era esto.
 *
 * <p><b>El backend nunca toca los bytes.</b> Subir y descargar van por URL prefirmada
 * directamente entre el cliente y S3: el archivo no pasa por la JVM, no ocupa un hilo mientras
 * dura la transferencia y no cuenta contra el presupuesto de latencia del §3. El unico verbo que
 * ejecuta el servidor es el borrado.
 *
 * <p><b>Sobre la validez de las URLs:</b> cada caso de uso decide la suya y este adaptador la
 * respeta tal cual — un audio de terapia y la firma de un contrato de fase no tienen por que
 * caducar al mismo tiempo. Una URL prefirmada es una credencial de acceso a ese objeto: quien la
 * tenga entra hasta que venza, asi que las validezas cortas no son una molestia sino la medida
 * de seguridad.
 */
@Component
@ConditionalOnProperty(name = "renaser.storage.proveedor", havingValue = "s3")
public class S3AlmacenamientoAdapter implements AlmacenamientoPort {

    private static final Logger log = LoggerFactory.getLogger(S3AlmacenamientoAdapter.class);

    private final S3Presigner presigner;
    private final S3Client cliente;
    private final String bucket;

    public S3AlmacenamientoAdapter(S3Presigner presigner, S3Client cliente,
                                    @Value("${renaser.storage.s3.bucket}") String bucket) {
        this.presigner = presigner;
        this.cliente = cliente;
        this.bucket = bucket;
    }

    @Override
    public URI firmarSubida(String ruta, String tipoContenido, Duration validez) {
        PutObjectRequest objeto = PutObjectRequest.builder()
                .bucket(bucket)
                .key(ruta)
                .contentType(tipoContenido)
                .build();
        // El contentType va firmado: el cliente tiene que mandar EXACTAMENTE ese Content-Type en
        // el PUT o S3 rechaza la firma. Es lo que impide que alguien pida una URL para una imagen
        // y suba un ejecutable con la misma URL.
        URI url = URI.create(presigner.presignPutObject(PutObjectPresignRequest.builder()
                .signatureDuration(validez)
                .putObjectRequest(objeto)
                .build()).url().toString());
        log.debug("URL de subida firmada para {} ({}), valida {}", ruta, tipoContenido, validez);
        return url;
    }

    @Override
    public URI firmarLectura(String ruta, Duration validez) {
        GetObjectRequest objeto = GetObjectRequest.builder()
                .bucket(bucket)
                .key(ruta)
                .build();
        return URI.create(presigner.presignGetObject(GetObjectPresignRequest.builder()
                .signatureDuration(validez)
                .getObjectRequest(objeto)
                .build()).url().toString());
    }

    @Override
    public void borrar(String ruta) {
        // S3 responde 204 tambien cuando la clave no existe, asi que la idempotencia que promete
        // el puerto sale gratis: no hace falta consultar antes de borrar.
        cliente.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(ruta).build());
        log.info("Objeto borrado de S3: {}", ruta);
    }
}

package com.renaser.os.shared.infrastructure.storage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

/**
 * Clientes de S3, creados SOLO cuando {@code renaser.storage.proveedor=s3}. Mismo patron que
 * {@code SmtpEnviarEmailAdapter}/{@code NoOpEnviarEmailAdapter}: sin la propiedad, ninguno de
 * estos beans existe y el sistema sigue con {@code NoOpAlmacenamientoAdapter}. Asi el entorno
 * local y los tests no necesitan credenciales de AWS para arrancar.
 *
 * <p><b>Las credenciales NUNCA se declaran aca.</b> {@link DefaultCredentialsProvider} las busca
 * en el orden estandar del SDK: variables de entorno
 * ({@code AWS_ACCESS_KEY_ID}/{@code AWS_SECRET_ACCESS_KEY}), el perfil del archivo
 * {@code ~/.aws/credentials}, y el rol de la instancia si corre en AWS. Es lo que permite usar
 * el perfil local para probar y un rol IAM en el despliegue sin cambiar una linea de codigo ni
 * poner un secreto en el yaml.
 *
 * <p>El permiso minimo que necesita ese principal es {@code GetObject}, {@code PutObject} y
 * {@code DeleteObject} sobre el bucket configurado, y nada mas.
 */
@Configuration
@ConditionalOnProperty(name = "renaser.storage.proveedor", havingValue = "s3")
class AlmacenamientoS3Config {

    private final Region region;

    AlmacenamientoS3Config(@Value("${renaser.storage.s3.region}") String region) {
        this.region = Region.of(region);
    }

    /**
     * Firma URLs sin llamar a AWS: el presigner calcula la firma localmente con la credencial,
     * asi que firmar una URL de lectura no cuesta una vuelta de red. Importante para el catalogo
     * de cursos, que firma una portada por curso en la misma respuesta.
     */
    @Bean
    S3Presigner s3Presigner() {
        return S3Presigner.builder()
                .region(region)
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build();
    }

    /** Solo para borrar: subir y leer van por URL prefirmada, sin pasar por el backend. */
    @Bean
    S3Client s3Client() {
        return S3Client.builder()
                .region(region)
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build();
    }
}

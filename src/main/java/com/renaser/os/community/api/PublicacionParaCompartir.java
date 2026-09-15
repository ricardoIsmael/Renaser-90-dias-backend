package com.renaser.os.community.api;

import com.renaser.os.shared.domain.UserId;

import java.util.UUID;

/**
 * Lo que otro modulo necesita saber de una publicacion del Muro para poder compartirla.
 *
 * <p><b>Por que lleva {@code mediaBucket}/{@code mediaRuta} y NO una URL.</b> Es la correccion de
 * un bug real: compartir una foto del Muro en el chat pegaba en el mensaje la URL firmada de S3,
 * que trae {@code X-Amz-Expires=900} — <b>quince minutos</b>. Quien abria esa conversacion al dia
 * siguiente encontraba un enlace muerto, y el mensaje quedaba ademas con 500 caracteres de firma
 * a la vista. Guardar una URL firmada en una fila que vive para siempre es exactamente lo que el
 * propio javadoc de {@code chat.MensajeEnriquecido} ya advertia: <i>"una URL firmada vence y
 * guardarla dejaria la foto en 403"</i>.
 *
 * <p>Con la referencia al objeto (bucket + ruta), {@code chat} guarda el mensaje como media de
 * verdad y <b>vuelve a firmar en cada lectura</b>, que es como funciona el resto del chat desde
 * siempre. La foto no caduca nunca y no se copia un solo byte.
 *
 * <p><b>Esto viaja entre modulos, NUNCA al cliente.</b> La API del Muro expone solo
 * {@code MediaItemResponse(url, mimeType)} a proposito: las claves de S3 no salen al telefono.
 * Este record es de servidor a servidor.
 *
 * @param publicacionId la publicacion compartida, para poder rastrear el origen
 * @param autorId       quien publico. El NOMBRE lo resuelve quien consume, contra `users.api`; no
 *                      se confia en el que mande el cliente para un texto que queda persistido
 * @param texto         el texto de la publicacion, puede venir vacio (una foto sin epigrafe)
 * @param mediaBucket   {@code null} si la publicacion no tiene imagen
 * @param mediaRuta     {@code null} si la publicacion no tiene imagen; va siempre junto al bucket
 * @param mediaMime     {@code null} si la publicacion no tiene imagen
 */
public record PublicacionParaCompartir(UUID publicacionId, UserId autorId, String texto,
                                        String mediaBucket, String mediaRuta, String mediaMime) {

    /** Hay imagen que adjuntar al mensaje. Bucket y ruta van siempre juntos (CHECK `media_completa`). */
    public boolean tieneImagen() {
        return mediaRuta != null && mediaBucket != null;
    }
}

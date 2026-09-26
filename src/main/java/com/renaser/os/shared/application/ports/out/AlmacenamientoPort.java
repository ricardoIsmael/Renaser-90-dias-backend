package com.renaser.os.shared.application.ports.out;

import java.net.URI;
import java.time.Duration;

public interface AlmacenamientoPort {

    /** URL prefirmada para que el cliente suba un objeto (PUT). */
    URI firmarSubida(String ruta, String tipoContenido, Duration validez);

    /** URL prefirmada de lectura (GET) para un objeto ya subido. */
    URI firmarLectura(String ruta, Duration validez);

    /**
     * URL permanente y SIN firmar del objeto. Solo sirve si el objeto es de lectura publica:
     * no lleva credencial, asi que el que decide si abre o devuelve 403 es la politica del
     * bucket, no este metodo.
     *
     * <p>Existe para los activos de baja sensibilidad que se muestran todo el tiempo — hoy solo
     * el avatar. Una URL prefirmada cambia en cada respuesta (lleva firma y vencimiento), y eso
     * invalida el cache de imagen del cliente: un muro con 20 avatares volveria a descargar las
     * 20 fotos en cada pantallazo. Para todo lo demas (evidencia, contratos, adjuntos, audios)
     * la respuesta correcta sigue siendo {@link #firmarLectura}, porque ahi el vencimiento es
     * justamente la medida de seguridad.
     */
    URI urlPublica(String ruta);

    /**
     * Sube un objeto que generó el propio servidor. Es la excepción a "el backend nunca toca los
     * bytes": todo archivo que viene del teléfono sigue yendo por {@link #firmarSubida}. Existe
     * para la imagen de bienvenida (D-174), que no la sube nadie porque la dibuja el servidor.
     */
    void subir(String ruta, byte[] contenido, String tipoContenido);

    /** Borra el objeto. Idempotente: borrar lo inexistente no falla. */
    void borrar(String ruta);
}

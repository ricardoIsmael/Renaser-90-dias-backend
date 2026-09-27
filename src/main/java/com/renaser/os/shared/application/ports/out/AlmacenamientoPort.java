package com.renaser.os.shared.application.ports.out;

import java.net.URI;
import java.time.Duration;
import java.util.Optional;

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
     *
     * <p><b>Corregido 2026-09-27 (D-212).</b> Decía que existía solo para la bienvenida. También sube
     * la foto propia de un grupo: llega del teléfono, pero el servidor la lee, la recorta y la
     * reescribe como JPEG de 512 px antes de guardarla, así que lo que se sube es lo que generó él.
     */
    void subir(String ruta, byte[] contenido, String tipoContenido);

    /**
     * Los bytes de un objeto, para lo poco que el servidor sirve él mismo en vez de dar una URL: hoy,
     * la foto propia de un grupo (D-212), que la app pide con la sesión a la API del chat. Vacío si el
     * objeto no existe.
     *
     * <p>Por defecto vacío, como {@link #guardaObjetos()}: el adaptador de marcador (local y pruebas)
     * no guarda nada que leer. El de S3 lo lee de verdad.
     */
    default Optional<byte[]> leer(String ruta) {
        return Optional.empty();
    }

    /** Borra el objeto. Idempotente: borrar lo inexistente no falla. */
    void borrar(String ruta);

    /**
     * Si {@link #subir} deja el objeto guardado de verdad. {@code false} en el adaptador de
     * marcador ({@code renaser.storage.proveedor=noop}, el de local y las pruebas): ahí subir no
     * guarda nada, y quien genera un objeto en el servidor no debe mandar un mensaje que apunte a
     * un objeto inexistente (G-5, 2026-09-26: la bienvenida dejaba una foto rota).
     */
    default boolean guardaObjetos() {
        return true;
    }
}

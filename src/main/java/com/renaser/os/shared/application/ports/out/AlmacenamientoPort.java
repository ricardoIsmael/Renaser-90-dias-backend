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

    /** Borra el objeto. Idempotente: borrar lo inexistente no falla. */
    void borrar(String ruta);

    /**
     * Baja un objeto que el propio servidor tiene que ABRIR. Es la otra excepción a "el backend nunca
     * toca los bytes", y tan acotada como {@link #subir}: existe para la portada de la tarjeta de
     * bienvenida (D-210), que el servidor revisa (que el nombre se lea encima) y sobre la que dibuja, y
     * para la foto propia de un grupo (D-212), que la app pide con la sesión a la API del chat. Hasta la
     * integración del 2026-09-27 D-212 tenía su propio {@code leer(ruta)} sin tope: se unificó en este.
     * Todo lo que solo se MUESTRA sigue bajando por {@link #firmarLectura}, directo al teléfono.
     *
     * @param pesoMaximo en bytes: un objeto más pesado no se baja entero
     * @return vacío si el objeto no existe, o si el almacenamiento es de marcador (no guarda nada)
     * @throws IllegalArgumentException si el objeto pesa más que {@code pesoMaximo}
     */
    Optional<byte[]> leer(String ruta, long pesoMaximo);

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

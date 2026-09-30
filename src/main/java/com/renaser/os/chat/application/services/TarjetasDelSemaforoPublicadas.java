package com.renaser.os.chat.application.services;

import com.renaser.os.chat.application.ports.out.semaforo.ImagenDeLaTarjetaPort;
import com.renaser.os.chat.domain.model.mensaje.ContenidoDelPrograma;
import com.renaser.os.chat.domain.model.semaforo.ColorDeTarjeta;
import com.renaser.os.shared.application.ports.out.AlmacenamientoPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Lleva cada tarjeta del semáforo al almacenamiento UNA vez y da el contenido del mensaje que la muestra
 * (D-223). Todos los mensajes de un color apuntan a la misma ruta ({@link ColorDeTarjeta#rutaEnAlmacenamiento}):
 * tres objetos para todo el padrón y todas las noches, en vez de uno por mensaje.
 *
 * <p><b>Subir una vez por proceso, sin mirar antes.</b> La primera vez que el proceso necesita un color, lo
 * sube (pisa el objeto con los mismos bytes: la ruta es fija y el contenido viene de los recursos) y lo
 * recuerda hasta el próximo reinicio: a lo sumo tres subidas de ~100 KB por despliegue.
 *
 * <p>> <b>Corregido 2026-09-30 (E-453).</b> Antes miraba primero si el objeto ya existía con
 * {@link AlmacenamientoPort#leer}. En producción el rol del servidor no tiene {@code s3:ListBucket}, y sin
 * ese permiso S3 responde a un objeto inexistente con 403 (AccessDenied), no con 404: la lectura fallaba, la
 * tarjeta nunca se subía y la noche del 29/09 salió solo el texto en los 11 soportes.
 *
 * <p><b>Sin almacenamiento de verdad no hay imagen</b> (G-5, como la bienvenida): con {@code noop} subir no
 * guarda nada y el mensaje apuntaría a una foto inexistente, así que sale solo el texto. Si S3 falla, lo
 * mismo: se registra y el aprendiz recibe igual su línea del día.
 *
 * <p>La ruta vive fuera del prefijo {@code chat/<conversación>/} a propósito: la imagen no es de ningún chat.
 * La purga de cuentas no la toca, porque solo borra claves con la forma de las de una cuenta
 * ({@code users.ClavesDeCuenta}), y leerla no exige prefijo ({@code firmarLectura}).
 */
@Component
class TarjetasDelSemaforoPublicadas {

    private static final Logger log = LoggerFactory.getLogger(TarjetasDelSemaforoPublicadas.class);

    private final AlmacenamientoPort almacenamiento;
    private final ImagenDeLaTarjetaPort imagenes;
    private final Map<ColorDeTarjeta, ContenidoDelPrograma> publicadas = new ConcurrentHashMap<>();

    TarjetasDelSemaforoPublicadas(AlmacenamientoPort almacenamiento, ImagenDeLaTarjetaPort imagenes) {
        this.almacenamiento = almacenamiento;
        this.imagenes = imagenes;
    }

    /** @return el contenido de la imagen, o vacío si no hay dónde guardarla o no se pudo */
    Optional<ContenidoDelPrograma> imagenDe(ColorDeTarjeta color) {
        if (!almacenamiento.guardaObjetos()) {
            return Optional.empty();
        }
        ContenidoDelPrograma yaPublicada = publicadas.get(color);
        if (yaPublicada != null) {
            return Optional.of(yaPublicada);
        }
        try {
            ContenidoDelPrograma contenido = publicar(color);
            publicadas.put(color, contenido);
            return Optional.of(contenido);
        } catch (RuntimeException e) {
            log.warn("[chat.semaforo] la tarjeta {} no se pudo llevar al almacenamiento ({}): sale solo el texto",
                    color, e.getMessage());
            return Optional.empty();
        }
    }

    private ContenidoDelPrograma publicar(ColorDeTarjeta color) {
        String ruta = color.rutaEnAlmacenamiento();
        byte[] imagen = imagenes.imagen(color);
        almacenamiento.subir(ruta, imagen, ImagenDeLaTarjetaPort.TIPO_CONTENIDO);
        log.info("[chat.semaforo] tarjeta {} subida al almacenamiento en {} ({} bytes)", color, ruta, imagen.length);
        return ContenidoDelPrograma.imagen(ruta, ImagenDeLaTarjetaPort.TIPO_CONTENIDO, imagen.length);
    }
}

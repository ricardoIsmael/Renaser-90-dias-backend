package com.renaser.os.chat.infrastructure.adapter.out.bienvenida;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.renaser.os.chat.application.ports.out.bienvenida.DibujarBienvenidaPort;
import com.renaser.os.chat.application.ports.out.bienvenida.TarjetaConNombrePort;
import org.springframework.stereotype.Component;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;

/**
 * Las tarjetas con nombre que sirve la foto del chat de soporte (D-205), guardadas en memoria.
 *
 * <p><b>Por nombre, no por persona.</b> La tarjeta depende solo del primer nombre (el dibujo lo pasa a
 * mayúsculas), así que «Ana» y «ANA» son la misma imagen y dos Anas comparten entrada.
 *
 * <p><b>Acotada por peso.</b> Cada tarjeta pesa ~110–125 KB y el contenedor de producción tiene un
 * tope de memoria (V-8): se guardan como mucho {@link #PESO_MAXIMO_EN_BYTES} (unas cincuenta) y, al
 * pasarse, sale la que menos se usa. Una que salió se vuelve a dibujar la próxima vez que se pida.
 *
 * <p><b>La huella es del contenido</b> (SHA-256 del JPEG), no del nombre: si mañana cambia el fondo
 * o la letra, cambia sola y los teléfonos que tengan la vieja la vuelven a bajar.
 */
@Component
class TarjetasConNombreEnMemoria implements TarjetaConNombrePort {

    static final long PESO_MAXIMO_EN_BYTES = 6L * 1024 * 1024;
    private static final Locale ESPANOL = Locale.forLanguageTag("es");
    private static final int LARGO_DE_LA_HUELLA = 32;

    private final DibujarBienvenidaPort dibujante;
    private final Cache<String, TarjetaConNombre> tarjetas = Caffeine.newBuilder()
            .maximumWeight(PESO_MAXIMO_EN_BYTES)
            .weigher((String nombre, TarjetaConNombre tarjeta) -> tarjeta.jpeg().length)
            .build();

    TarjetasConNombreEnMemoria(DibujarBienvenidaPort dibujante) {
        this.dibujante = dibujante;
    }

    /** Dos pedidos del mismo nombre a la vez dibujan una sola vez: el segundo espera al primero. */
    @Override
    public TarjetaConNombre tarjetaDe(String primerNombre) {
        return tarjetas.get(clave(primerNombre), this::dibujar);
    }

    private TarjetaConNombre dibujar(String nombre) {
        byte[] jpeg = dibujante.dibujar(nombre);
        return new TarjetaConNombre(jpeg, huella(jpeg));
    }

    /** La misma normalización que el dibujo: sin espacios en los bordes y en mayúsculas. */
    private static String clave(String primerNombre) {
        return primerNombre == null ? "" : primerNombre.strip().toUpperCase(ESPANOL);
    }

    static String huella(byte[] jpeg) {
        try {
            byte[] resumen = MessageDigest.getInstance("SHA-256").digest(jpeg);
            return HexFormat.of().formatHex(resumen).substring(0, LARGO_DE_LA_HUELLA);
        } catch (NoSuchAlgorithmException sinSha256) {
            throw new IllegalStateException("La JVM no trae SHA-256", sinSha256);
        }
    }

    /** Solo para las pruebas: cuántas tarjetas hay guardadas después de aplicar el tope. */
    long guardadas() {
        tarjetas.cleanUp();
        return tarjetas.estimatedSize();
    }
}

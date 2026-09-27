package com.renaser.os.chat.infrastructure.adapter.out.bienvenida;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.renaser.os.chat.application.ports.out.bienvenida.CambiosDeBienvenidaPort;
import com.renaser.os.chat.application.ports.out.bienvenida.DibujarBienvenidaPort;
import com.renaser.os.chat.application.ports.out.bienvenida.PortadaDeBienvenidaPort;
import com.renaser.os.chat.domain.model.bienvenida.EstadoDePieza;
import com.renaser.os.chat.domain.model.bienvenida.PiezaDeBienvenida;
import com.renaser.os.chat.domain.model.bienvenida.PortadaDeBienvenida;
import com.renaser.os.shared.application.ports.out.AlmacenamientoPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.NoSuchElementException;

/**
 * Las portadas de la tarjeta de bienvenida (D-210): cuál es la vigente y sus píxeles.
 *
 * <p><b>Cuál es la vigente</b> lo dice la bitácora ({@code cambios_bienvenida}, V73): la ruta de la última
 * portada que subió Administración, o {@link DibujarBienvenidaPort#PORTADA_ORIGINAL} si nunca cambió o
 * alguien volvió a la original. Se pregunta en cada dibujo (una lectura indexada de una fila): así un
 * cambio vale en el acto, sin avisos entre procesos.
 *
 * <p><b>Los píxeles.</b> La original ({@code bienvenida/fondo.png}) se carga al arrancar, como antes. Las
 * subidas viven en el mismo almacenamiento que las fotos de evidencia ({@link AlmacenamientoPort}) y se
 * guardan abiertas en memoria, como mucho {@link #ABIERTAS} (la vigente y una candidata; ~4 MB cada una,
 * V-8). Una ruta no se reescribe nunca (cada subida lleva un id nuevo), así que lo abierto no envejece.
 *
 * <p><b>Si una portada guardada ya no abre</b> (se borró del almacenamiento, o el proceso corre con el de
 * marcador), se dibuja sobre la original y queda un {@code WARN}: la bienvenida y la foto del soporte
 * siguen saliendo.
 */
@Component
class PortadasDeBienvenida implements PortadaDeBienvenidaPort {

    static final int ABIERTAS = 2;
    private static final Logger log = LoggerFactory.getLogger(PortadasDeBienvenida.class);

    private final CambiosDeBienvenidaPort cambios;
    private final AlmacenamientoPort almacenamiento;
    private final BufferedImage original;
    private final Cache<String, BufferedImage> abiertas = Caffeine.newBuilder().maximumSize(ABIERTAS).build();

    PortadasDeBienvenida(CambiosDeBienvenidaPort cambios, AlmacenamientoPort almacenamiento) {
        this.cambios = cambios;
        this.almacenamiento = almacenamiento;
        this.original = leerOriginal("/bienvenida/fondo.png");
    }

    /** La versión vigente: {@link DibujarBienvenidaPort#PORTADA_ORIGINAL} o la ruta de la subida. */
    String vigente() {
        return new EstadoDePieza(PiezaDeBienvenida.PORTADA, DibujarBienvenidaPort.PORTADA_ORIGINAL,
                cambios.ultimo(PiezaDeBienvenida.PORTADA).orElse(null)).vigente();
    }

    /** La imagen de esa versión, en el lienzo de 1200 × 1200. Nunca falla: si no abre, la original. */
    BufferedImage imagen(String version) {
        if (DibujarBienvenidaPort.PORTADA_ORIGINAL.equals(version)) {
            return original;
        }
        try {
            return abiertas.get(version, this::abrir);
        } catch (RuntimeException sinPortada) {
            log.warn("[chat.bienvenida] la portada {} no se pudo abrir ({}): se dibuja sobre la original",
                    version, sinPortada.getMessage());
            return original;
        }
    }

    @Override
    public void revisar(String ruta) {
        BufferedImage lienzo = abrir(PortadaDeBienvenida.exigirRutaPropia(ruta));
        PortadaDeBienvenida.exigirNombreLegible(ImagenDePortada.parteOscuraDondeVaElNombre(lienzo));
        abiertas.put(ruta, lienzo);
    }

    private BufferedImage abrir(String ruta) {
        byte[] contenido = almacenamiento.leer(ruta, PortadaDeBienvenida.PESO_MAXIMO_EN_BYTES)
                .orElseThrow(() -> new NoSuchElementException(
                        "No encontramos la imagen subida: vuelve a elegirla."));
        return ImagenDePortada.abrir(contenido);
    }

    private static BufferedImage leerOriginal(String recurso) {
        try (InputStream entrada = PortadasDeBienvenida.class.getResourceAsStream(recurso)) {
            if (entrada == null) {
                throw new IllegalStateException("Falta el recurso " + recurso + " en el jar");
            }
            return ImageIO.read(entrada);
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo leer el fondo de bienvenida " + recurso, e);
        }
    }
}

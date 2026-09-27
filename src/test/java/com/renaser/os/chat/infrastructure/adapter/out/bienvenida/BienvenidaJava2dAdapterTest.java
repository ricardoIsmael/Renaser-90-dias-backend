package com.renaser.os.chat.infrastructure.adapter.out.bienvenida;

import com.renaser.os.chat.application.ports.out.bienvenida.CambiosDeBienvenidaEnMemoria;
import com.renaser.os.chat.domain.model.bienvenida.CambioDeBienvenida;
import com.renaser.os.shared.application.ports.out.AlmacenamientoEnMemoria;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * La tarjeta tiene que verse como la de Canva (D-174). La referencia es la exportación que pasó
 * Operaciones con "FLOR DE MARÍA": si alguien mueve la línea base, cambia la fuente o el tamaño,
 * la cantidad de píxeles distintos se dispara (con Cinzel sin ajustar eran ~31 000; bien
 * calibrada queda en unos pocos miles, que son el suavizado de bordes y el JPEG).
 */
class BienvenidaJava2dAdapterTest {

    private static final int UMBRAL_DE_COLOR = 60;
    private static final int X0 = 60;
    private static final int X1 = 1140;
    private static final int Y0 = 730;
    private static final int Y1 = 890;

    private static final String NUEVA = "bienvenida/portadas/22222222-bbbb-4bbb-8bbb-222222222222";

    private final CambiosDeBienvenidaEnMemoria cambios = new CambiosDeBienvenidaEnMemoria();
    private final AlmacenamientoEnMemoria almacenamiento = new AlmacenamientoEnMemoria();
    private final BienvenidaJava2dAdapter adapter =
            new BienvenidaJava2dAdapter(new PortadasDeBienvenida(cambios, almacenamiento));

    @Test
    @DisplayName("con el nombre de la referencia, la tarjeta coincide con la exportación de Canva")
    void coincideConLaReferenciaDeCanva() throws IOException {
        BufferedImage dibujada = leer(adapter.dibujar("Flor de María"));
        BufferedImage referencia = ImageIO.read(recurso("/bienvenida/referencia-flor-de-maria.png"));

        assertThat(dibujada.getWidth()).isEqualTo(1200);
        assertThat(pixelesDistintos(dibujada, referencia)).isLessThan(9_000);
    }

    @Test
    @DisplayName("sin nombre sale el fondo solo, que NO coincide con la referencia (el test distingue)")
    void elTestDistingueUnaTarjetaSinNombre() throws IOException {
        BufferedImage vacia = leer(adapter.dibujar(""));
        BufferedImage referencia = ImageIO.read(recurso("/bienvenida/referencia-flor-de-maria.png"));

        assertThat(pixelesDistintos(vacia, referencia)).isGreaterThan(15_000);
    }

    @Test
    @DisplayName("un nombre muy largo se achica y no se sale del cuadro")
    void unNombreLargoNoSeSale() throws IOException {
        BufferedImage dibujada = leer(adapter.dibujar("Maximilianoooooooooo"));

        for (int y = Y0; y < Y1; y++) {
            for (int x = 0; x < 60; x++) {
                assertThat(esTinta(dibujada.getRGB(x, y))).as("tinta en el margen izquierdo x=%d", x).isFalse();
                assertThat(esTinta(dibujada.getRGB(1199 - x, y))).as("tinta en el margen derecho").isFalse();
            }
        }
    }

    @Test
    @DisplayName("sale en JPEG liviano, para que cargue con datos móviles")
    void saleLiviana() {
        byte[] jpeg = adapter.dibujar("Ana");

        assertThat(jpeg[0] & 0xFF).isEqualTo(0xFF);
        assertThat(jpeg[1] & 0xFF).isEqualTo(0xD8);
        assertThat(jpeg.length).isLessThan(400_000);
    }

    @Test
    @DisplayName("D-210: con la portada nueva, la tarjeta sale sobre la nueva y el nombre se sigue leyendo")
    void conLaPortadaNueva() throws IOException {
        almacenamiento.guardar(NUEVA, ImagenesDePrueba.lisa(1200, 1200, ImagenesDePrueba.CELESTE, "jpeg"));
        cambios.registrar(CambioDeBienvenida.portada(NUEVA, UserId.of(UUID.randomUUID()), Instant.parse("2026-09-27T04:30:00Z")));

        BufferedImage tarjeta = leer(adapter.dibujar("Flor de María"));

        assertThat(adapter.portadaVigente()).isEqualTo(NUEVA);
        assertThat(ImagenesDePrueba.parecido(tarjeta.getRGB(100, 100), ImagenesDePrueba.CELESTE))
                .as("fuera del nombre se ve la portada nueva").isTrue();
        BufferedImage referencia = ImageIO.read(recurso("/bienvenida/referencia-flor-de-maria.png"));
        assertThat(tintaDelNombre(tarjeta)).as("el nombre está en el mismo lugar y con la misma letra")
                .isBetween(tintaDelNombre(referencia) * 8 / 10, tintaDelNombre(referencia) * 12 / 10);
    }

    @Test
    @DisplayName("D-210: se puede dibujar sobre una portada dada aunque la vigente sea otra")
    void sobreUnaPortadaDada() throws IOException {
        almacenamiento.guardar(NUEVA, ImagenesDePrueba.lisa(1200, 1200, ImagenesDePrueba.CELESTE, "jpeg"));
        cambios.registrar(CambioDeBienvenida.portada(NUEVA, UserId.of(UUID.randomUUID()), Instant.parse("2026-09-27T04:30:00Z")));

        BufferedImage sobreLaOriginal = leer(adapter.dibujar("Flor de María", "original"));
        BufferedImage referencia = ImageIO.read(recurso("/bienvenida/referencia-flor-de-maria.png"));

        assertThat(pixelesDistintos(sobreLaOriginal, referencia)).isLessThan(9_000);
    }

    /** Cuántos puntos de la franja del nombre tienen la tinta verde oscura de la letra. */
    private static int tintaDelNombre(BufferedImage tarjeta) {
        int tinta = 0;
        for (int y = Y0; y < Y1; y++) {
            for (int x = X0; x < X1; x++) {
                if (diferencia(tarjeta.getRGB(x, y), 0x153832) < 40) {
                    tinta++;
                }
            }
        }
        return tinta;
    }

    private static int pixelesDistintos(BufferedImage a, BufferedImage b) {
        int distintos = 0;
        for (int y = Y0; y < Y1; y++) {
            for (int x = X0; x < X1; x++) {
                if (diferencia(a.getRGB(x, y), b.getRGB(x, y)) > UMBRAL_DE_COLOR) {
                    distintos++;
                }
            }
        }
        return distintos;
    }

    private static int diferencia(int p, int q) {
        int dr = Math.abs(((p >> 16) & 0xFF) - ((q >> 16) & 0xFF));
        int dg = Math.abs(((p >> 8) & 0xFF) - ((q >> 8) & 0xFF));
        int db = Math.abs((p & 0xFF) - (q & 0xFF));
        return Math.max(dr, Math.max(dg, db));
    }

    private static boolean esTinta(int p) {
        return ((p >> 16) & 0xFF) + ((p >> 8) & 0xFF) + (p & 0xFF) < 300;
    }

    private static BufferedImage leer(byte[] jpeg) throws IOException {
        return ImageIO.read(new ByteArrayInputStream(jpeg));
    }

    private static InputStream recurso(String ruta) {
        return BienvenidaJava2dAdapterTest.class.getResourceAsStream(ruta);
    }
}

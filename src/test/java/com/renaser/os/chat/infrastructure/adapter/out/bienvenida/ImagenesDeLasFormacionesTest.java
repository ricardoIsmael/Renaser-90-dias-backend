package com.renaser.os.chat.infrastructure.adapter.out.bienvenida;

import com.renaser.os.chat.application.ports.out.bienvenida.ImagenDeLaFormacionPort.Imagen;
import com.renaser.os.shared.application.ports.out.AlmacenamientoEnMemoria;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.util.NoSuchElementException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** La imagen de una formación: se abre, se revisa y se sirve como JPEG cuadrado de 512 px (spec 035). Sin Spring: el almacenamiento en memoria. */
class ImagenesDeLasFormacionesTest {

    private static final String RUTA = "formaciones/imagenes/11111111-aaaa-4aaa-8aaa-111111111111";

    private final AlmacenamientoEnMemoria almacenamiento = new AlmacenamientoEnMemoria();
    private final ImagenesDeLasFormaciones imagenes = new ImagenesDeLasFormaciones(almacenamiento);

    // spec 035 FR-010
    @Test
    @DisplayName("una imagen cualquiera sale como JPEG cuadrado de 512 px, con huella estable")
    void salenCuadradaYEn512() {
        almacenamiento.guardar(RUTA, ImagenesDePrueba.lisa(1500, 900, ImagenesDePrueba.CELESTE, "png"));

        Imagen imagen = imagenes.jpegDe(RUTA);

        assertThat(imagen.jpeg()).startsWith((byte) 0xFF, (byte) 0xD8);
        BufferedImage leida = ImagenesDePrueba.leer(imagen.jpeg());
        assertThat(leida.getWidth()).isEqualTo(512);
        assertThat(leida.getHeight()).isEqualTo(512);
        assertThat(ImagenesDePrueba.parecido(leida.getRGB(256, 256), ImagenesDePrueba.CELESTE)).as("el centro se conserva").isTrue();
        assertThat(imagenes.jpegDe(RUTA).huella()).isEqualTo(imagen.huella()).hasSize(32);
        assertThat(imagen.jpeg().length).isLessThan(120_000);
    }

    @Test
    @DisplayName("revisar rechaza lo que no sirve, con el motivo, y no deja nada guardado para servir")
    void revisarRechazaLoQueNoSirve() {
        almacenamiento.guardar(RUTA, ImagenesDePrueba.lisa(300, 300, ImagenesDePrueba.CELESTE, "jpeg"));
        almacenamiento.guardar(RUTA + "-gif", ImagenesDePrueba.lisa(1200, 1200, ImagenesDePrueba.CELESTE, "gif"));
        almacenamiento.guardar(RUTA + "-roto", new byte[] {1, 2, 3, 4});

        assertThatThrownBy(() -> imagenes.revisar(RUTA)).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("muy chica");
        assertThatThrownBy(() -> imagenes.revisar(RUTA + "-gif")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> imagenes.revisar(RUTA + "-roto")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> imagenes.revisar("formaciones/imagenes/no-subida")).isInstanceOf(NoSuchElementException.class);
    }

    @Test
    @DisplayName("solo se abren las rutas que emitió el servidor para las imágenes de formaciones, nunca la de otro objeto del depósito")
    void soloRutasPropias() {
        almacenamiento.guardar("firmas/ana/pacto.png", ImagenesDePrueba.lisa(1200, 1200, ImagenesDePrueba.CELESTE, "png"));

        assertThatThrownBy(() -> imagenes.jpegDe("firmas/ana/pacto.png")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> imagenes.revisar("bienvenida/portadas/x")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> imagenes.jpegDe("formaciones/imagenes/../firmas/ana/pacto.png")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("una imagen revisada queda abierta: servirla no vuelve a bajarla del almacenamiento")
    void revisadaQuedaAbierta() {
        almacenamiento.guardar(RUTA, ImagenesDePrueba.lisa(1200, 1200, ImagenesDePrueba.CELESTE, "jpeg"));
        imagenes.revisar(RUTA);
        almacenamiento.borrar(RUTA);

        assertThat(imagenes.jpegDe(RUTA).jpeg()).isNotEmpty();
    }

    @Test
    @DisplayName("un PNG transparente queda sobre blanco (el JPEG no tiene transparencia)")
    void transparenteSobreBlanco() {
        almacenamiento.guardar(RUTA, ImagenesDePrueba.transparente(1200));

        BufferedImage leida = ImagenesDePrueba.leer(imagenes.jpegDe(RUTA).jpeg());

        assertThat(new Color(leida.getRGB(10, 10)).getRed()).isGreaterThan(240);
    }
}

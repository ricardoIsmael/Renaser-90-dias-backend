package com.renaser.os.chat.infrastructure.adapter.out.bienvenida;

import com.renaser.os.chat.application.ports.out.bienvenida.CambiosDeBienvenidaEnMemoria;
import com.renaser.os.chat.domain.model.bienvenida.CambioDeBienvenida;
import com.renaser.os.chat.domain.model.bienvenida.PiezaDeBienvenida;
import com.renaser.os.shared.application.ports.out.AlmacenamientoEnMemoria;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.shared.domain.Formacion;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.time.Instant;
import java.util.NoSuchElementException;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Cuál es la portada vigente y cómo se abre una subida (D-210), sin base ni S3: todo en memoria. */
class PortadasDeBienvenidaTest {

    private static final UserId KELIN = UserId.of(UUID.fromString("22222222-2222-4222-8222-222222222222"));
    private static final Instant AHORA = Instant.parse("2026-09-27T04:30:00Z");
    private static final String NUEVA = "bienvenida/portadas/11111111-aaaa-4aaa-8aaa-111111111111";

    private final CambiosDeBienvenidaEnMemoria cambios = new CambiosDeBienvenidaEnMemoria();
    private final AlmacenamientoEnMemoria almacenamiento = new AlmacenamientoEnMemoria();
    private final PortadasDeBienvenida portadas = new PortadasDeBienvenida(cambios, almacenamiento);

    @Test
    @DisplayName("la vigente es la original, después la subida, y otra vez la original al volver")
    void laVigente() {
        assertThat(portadas.vigente(Formacion.ESCUELA)).isEqualTo("original");

        cambios.registrar(CambioDeBienvenida.portada(Formacion.ESCUELA, NUEVA, KELIN, AHORA));
        assertThat(portadas.vigente(Formacion.ESCUELA)).isEqualTo(NUEVA);

        cambios.registrar(CambioDeBienvenida.volverAlOriginal(Formacion.ESCUELA, PiezaDeBienvenida.PORTADA, KELIN, AHORA));
        assertThat(portadas.vigente(Formacion.ESCUELA)).isEqualTo("original");
    }

    @Test
    @DisplayName("Spec 035: sin portada propia una formación sigue a la Escuela; con la suya usa la suya; al volver, otra vez la de la Escuela")
    void laVigenteDeCadaFormacion() {
        assertThat(portadas.vigente(Formacion.DIPLOMADO)).isEqualTo("original");

        cambios.registrar(CambioDeBienvenida.portada(Formacion.ESCUELA, NUEVA, KELIN, AHORA));
        assertThat(portadas.vigente(Formacion.DIPLOMADO)).as("sigue a la Escuela").isEqualTo(NUEVA);
        assertThat(portadas.vigente(Formacion.CERTIFICACION)).isEqualTo(NUEVA);

        String delDiplomado = "bienvenida/portadas/22222222-bbbb-4bbb-8bbb-222222222222";
        cambios.registrar(CambioDeBienvenida.portada(Formacion.DIPLOMADO, delDiplomado, KELIN, AHORA));
        assertThat(portadas.vigente(Formacion.DIPLOMADO)).isEqualTo(delDiplomado);
        assertThat(portadas.vigente(Formacion.ESCUELA)).as("la Escuela no mira a las otras").isEqualTo(NUEVA);
        assertThat(portadas.vigente(Formacion.CERTIFICACION)).isEqualTo(NUEVA);

        cambios.registrar(CambioDeBienvenida.volverAlOriginal(Formacion.DIPLOMADO, PiezaDeBienvenida.PORTADA, KELIN, AHORA));
        assertThat(portadas.vigente(Formacion.DIPLOMADO)).isEqualTo(NUEVA);
    }

    @Test
    @DisplayName("revisada una vez, se dibuja sin volver a bajarla del almacenamiento")
    void revisadaQuedaAbierta() {
        almacenamiento.guardar(NUEVA, ImagenesDePrueba.lisa(1200, 1200, ImagenesDePrueba.CELESTE, "jpeg"));

        portadas.revisar(NUEVA);

        assertThat(ImagenesDePrueba.parecido(portadas.imagen(NUEVA).getRGB(100, 100), ImagenesDePrueba.CELESTE)).isTrue();
        assertThat(almacenamiento.leidas()).containsExactly(NUEVA);
    }

    @Test
    @DisplayName("una portada donde el nombre no se leería se rechaza con el motivo")
    void oscuraSeRechaza() {
        almacenamiento.guardar(NUEVA, ImagenesDePrueba.conFranja(Color.WHITE, new Color(0x15, 0x38, 0x32), 740, 890));

        assertThatThrownBy(() -> portadas.revisar(NUEVA))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("El nombre no se leería");
    }

    @Test
    @DisplayName("una ruta que no es de portadas no se abre, y sin nada subido es 404")
    void rutaAjenaOSinSubir() {
        almacenamiento.guardar("firmas/ana/pacto.png", ImagenesDePrueba.lisa(1200, 1200, Color.WHITE, "png"));

        assertThatThrownBy(() -> portadas.revisar("firmas/ana/pacto.png")).isInstanceOf(IllegalArgumentException.class);
        assertThat(almacenamiento.leidas()).isEmpty();
        assertThatThrownBy(() -> portadas.revisar(NUEVA)).isInstanceOf(NoSuchElementException.class);
    }

    @Test
    @DisplayName("si la portada vigente ya no está en el almacenamiento, se dibuja sobre la original")
    void sinLaSubidaSeUsaLaOriginal() {
        assertThat(portadas.imagen("bienvenida/portadas/perdida")).isSameAs(portadas.imagen("original"));
    }
}

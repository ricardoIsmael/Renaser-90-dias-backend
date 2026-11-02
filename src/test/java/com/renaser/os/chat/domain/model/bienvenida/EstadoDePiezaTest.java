package com.renaser.os.chat.domain.model.bienvenida;

import com.renaser.os.shared.domain.Formacion;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Lo que sale hoy de cada pieza: el último cambio o el original (D-210). Sin Spring. */
class EstadoDePiezaTest {

    private static final UserId KELIN = UserId.of(UUID.fromString("22222222-2222-4222-8222-222222222222"));
    private static final Instant AHORA = Instant.parse("2026-09-27T04:30:00Z");
    private static final String ORIGINAL = "Hola, {nombre}. Texto del repo.";

    @Test
    @DisplayName("sin cambios sale el original")
    void sinCambiosElOriginal() {
        EstadoDePieza estado = new EstadoDePieza(PiezaDeBienvenida.SOPORTE_FORMAL, ORIGINAL, null);

        assertThat(estado.vigente()).isEqualTo(ORIGINAL);
        assertThat(estado.cambiada()).isFalse();
        assertThat(estado.ultimo()).isEmpty();
    }

    @Test
    @DisplayName("con un texto guardado sale ese, y queda quién y cuándo")
    void conUnCambioSaleElGuardado() {
        CambioDeBienvenida cambio = CambioDeBienvenida.texto(Formacion.ESCUELA, PiezaDeBienvenida.SOPORTE_FORMAL,
                "  Hola, {nombre}. Texto nuevo. ", KELIN, AHORA);

        EstadoDePieza estado = new EstadoDePieza(PiezaDeBienvenida.SOPORTE_FORMAL, ORIGINAL, cambio);

        assertThat(estado.vigente()).isEqualTo("Hola, {nombre}. Texto nuevo.");
        assertThat(estado.cambiada()).isTrue();
        assertThat(cambio.quien()).contains(KELIN);
        assertThat(cambio.cambiadoEn()).isEqualTo(AHORA);
    }

    @Test
    @DisplayName("si el último cambio fue volver al original, sale el original")
    void volverAlOriginal() {
        EstadoDePieza estado = new EstadoDePieza(PiezaDeBienvenida.GRUPO, ORIGINAL,
                CambioDeBienvenida.volverAlOriginal(Formacion.ESCUELA, PiezaDeBienvenida.GRUPO, KELIN, AHORA));

        assertThat(estado.vigente()).isEqualTo(ORIGINAL);
        assertThat(estado.cambiada()).isFalse();
        assertThat(estado.ultimo()).hasValueSatisfying(c -> assertThat(c.esVueltaAlOriginal()).isTrue());
    }

    @Test
    @DisplayName("la portada vigente es la ruta de la última subida, o la original")
    void portada() {
        CambioDeBienvenida subida = CambioDeBienvenida.portada(Formacion.ESCUELA, "bienvenida/portadas/abc", KELIN, AHORA);

        assertThat(new EstadoDePieza(PiezaDeBienvenida.PORTADA, "original", subida).vigente())
                .isEqualTo("bienvenida/portadas/abc");
        assertThat(new EstadoDePieza(PiezaDeBienvenida.PORTADA, "original", null).vigente()).isEqualTo("original");
        assertThatThrownBy(() -> CambioDeBienvenida.portada(Formacion.ESCUELA, "firmas/ana/pacto.png", KELIN, AHORA))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("un cambio de otra pieza no se mezcla")
    void otraPiezaNo() {
        CambioDeBienvenida delGrupo = CambioDeBienvenida.volverAlOriginal(Formacion.ESCUELA, PiezaDeBienvenida.GRUPO, KELIN, AHORA);

        assertThatThrownBy(() -> new EstadoDePieza(PiezaDeBienvenida.SOPORTE_FORMAL, ORIGINAL, delGrupo))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("las claves de la API: solo las tres de texto; la portada se cambia aparte")
    void clavesDeTexto() {
        assertThat(PiezaDeBienvenida.textos()).containsExactly(PiezaDeBienvenida.SOPORTE_CON_LA_TARJETA,
                PiezaDeBienvenida.SOPORTE_FORMAL, PiezaDeBienvenida.GRUPO);
        assertThat(PiezaDeBienvenida.textoDe("GRUPO")).isEqualTo(PiezaDeBienvenida.GRUPO);
        assertThatThrownBy(() -> PiezaDeBienvenida.textoDe("PORTADA")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PiezaDeBienvenida.textoDe("grupo")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PiezaDeBienvenida.textoDe(null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("la portada de una formación sin la suya es la vigente de la Escuela, y con la suya, la suya")
    void laPortadaSeHeredaDeLaEscuela() {
        CambioDeBienvenida deLaEscuela = CambioDeBienvenida.portada(Formacion.ESCUELA, "bienvenida/portadas/escuela", KELIN, AHORA);
        CambioDeBienvenida delDiplomado = CambioDeBienvenida.portada(Formacion.DIPLOMADO, "bienvenida/portadas/diplomado", KELIN, AHORA);

        assertThat(EstadoDePieza.deImagen(Formacion.DIPLOMADO, PiezaDeBienvenida.PORTADA, "original", null, null).vigente())
                .as("ni la Escuela ni el Diplomado cambiaron: la original").isEqualTo("original");
        assertThat(EstadoDePieza.deImagen(Formacion.DIPLOMADO, PiezaDeBienvenida.PORTADA, "original", deLaEscuela, null).vigente())
                .as("el Diplomado sigue a la Escuela").isEqualTo("bienvenida/portadas/escuela");
        assertThat(EstadoDePieza.deImagen(Formacion.DIPLOMADO, PiezaDeBienvenida.PORTADA, "original", deLaEscuela, delDiplomado).vigente())
                .as("la suya manda").isEqualTo("bienvenida/portadas/diplomado");
        assertThat(EstadoDePieza.deImagen(Formacion.ESCUELA, PiezaDeBienvenida.PORTADA, "original", deLaEscuela, delDiplomado).vigente())
                .as("la Escuela nunca mira a otra formación").isEqualTo("bienvenida/portadas/escuela");
    }

    @Test
    @DisplayName("volver al original en el Diplomado es volver a la de la Escuela, no a la de fábrica")
    void volverEnElDiplomadoEsVolverALaEscuela() {
        CambioDeBienvenida deLaEscuela = CambioDeBienvenida.portada(Formacion.ESCUELA, "bienvenida/portadas/escuela", KELIN, AHORA);
        CambioDeBienvenida volvio = CambioDeBienvenida.volverAlOriginal(Formacion.DIPLOMADO, PiezaDeBienvenida.PORTADA, KELIN, AHORA);

        EstadoDePieza estado = EstadoDePieza.deImagen(Formacion.DIPLOMADO, PiezaDeBienvenida.PORTADA, "original", deLaEscuela, volvio);

        assertThat(estado.vigente()).isEqualTo("bienvenida/portadas/escuela");
        assertThat(estado.cambiada()).isFalse();
    }

    @Test
    @DisplayName("la imagen de una formación: la suya, la de la Escuela o ninguna")
    void laImagenDeLaFormacion() {
        CambioDeBienvenida deLaEscuela = CambioDeBienvenida.imagenDeLaFormacion(Formacion.ESCUELA, "formaciones/imagenes/e", KELIN, AHORA);

        assertThat(EstadoDePieza.deImagen(Formacion.CERTIFICACION, PiezaDeBienvenida.IMAGEN_DE_LA_FORMACION,
                ImagenDeLaFormacion.SIN_IMAGEN, null, null).vigente()).isEqualTo(ImagenDeLaFormacion.SIN_IMAGEN);
        assertThat(EstadoDePieza.deImagen(Formacion.CERTIFICACION, PiezaDeBienvenida.IMAGEN_DE_LA_FORMACION,
                ImagenDeLaFormacion.SIN_IMAGEN, deLaEscuela, null).vigente()).isEqualTo("formaciones/imagenes/e");
    }

    @Test
    @DisplayName("las rutas de imágenes de las formaciones son solo las que emitió el servidor")
    void rutasDeLaImagen() {
        assertThatThrownBy(() -> CambioDeBienvenida.imagenDeLaFormacion(Formacion.DIPLOMADO, "bienvenida/portadas/abc", KELIN, AHORA))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CambioDeBienvenida.imagenDeLaFormacion(Formacion.DIPLOMADO, "formaciones/imagenes/../firmas/x", KELIN, AHORA))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ImagenDeLaFormacion.exigirTipoDeContenido("image/gif")).isInstanceOf(IllegalArgumentException.class);
        assertThat(PiezaDeBienvenida.textos()).as("la imagen no es un texto").doesNotContain(PiezaDeBienvenida.IMAGEN_DE_LA_FORMACION);
        assertThat(PiezaDeBienvenida.IMAGEN_DE_LA_FORMACION.esImagen()).isTrue();
    }

    @Test
    @DisplayName("la carta de la caja es única: solo de la Escuela")
    void laCartaEsDeLaEscuela() {
        assertThatThrownBy(() -> new CambioDeBienvenida(Formacion.DIPLOMADO, PiezaDeBienvenida.CARTA_CAJA, "caja/cartas/x", KELIN, AHORA))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(CambioDeBienvenida.fondoDeCarta("caja/cartas/x", KELIN, AHORA).formacion()).isEqualTo(Formacion.ESCUELA);
    }
}

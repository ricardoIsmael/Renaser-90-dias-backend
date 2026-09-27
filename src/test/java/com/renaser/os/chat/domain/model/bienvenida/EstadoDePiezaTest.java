package com.renaser.os.chat.domain.model.bienvenida;

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
        CambioDeBienvenida cambio = CambioDeBienvenida.texto(PiezaDeBienvenida.SOPORTE_FORMAL,
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
                CambioDeBienvenida.volverAlOriginal(PiezaDeBienvenida.GRUPO, KELIN, AHORA));

        assertThat(estado.vigente()).isEqualTo(ORIGINAL);
        assertThat(estado.cambiada()).isFalse();
        assertThat(estado.ultimo()).hasValueSatisfying(c -> assertThat(c.esVueltaAlOriginal()).isTrue());
    }

    @Test
    @DisplayName("la portada vigente es la ruta de la última subida, o la original")
    void portada() {
        CambioDeBienvenida subida = CambioDeBienvenida.portada("bienvenida/portadas/abc", KELIN, AHORA);

        assertThat(new EstadoDePieza(PiezaDeBienvenida.PORTADA, "original", subida).vigente())
                .isEqualTo("bienvenida/portadas/abc");
        assertThat(new EstadoDePieza(PiezaDeBienvenida.PORTADA, "original", null).vigente()).isEqualTo("original");
        assertThatThrownBy(() -> CambioDeBienvenida.portada("firmas/ana/pacto.png", KELIN, AHORA))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("un cambio de otra pieza no se mezcla")
    void otraPiezaNo() {
        CambioDeBienvenida delGrupo = CambioDeBienvenida.volverAlOriginal(PiezaDeBienvenida.GRUPO, KELIN, AHORA);

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
}

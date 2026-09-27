package com.renaser.os.chat.infrastructure.adapter.out.bienvenida;

import com.renaser.os.chat.application.ports.out.bienvenida.CambiosDeBienvenidaEnMemoria;
import com.renaser.os.chat.domain.model.bienvenida.CambioDeBienvenida;
import com.renaser.os.chat.domain.model.bienvenida.PiezaDeBienvenida;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Los textos que salen: el guardado desde la app o el original del repo (D-210). */
class TextosDeBienvenidaVigentesTest {

    private static final UserId KELIN = UserId.of(UUID.fromString("22222222-2222-4222-8222-222222222222"));
    private static final Instant AHORA = Instant.parse("2026-09-27T04:30:00Z");

    private final CambiosDeBienvenidaEnMemoria cambios = new CambiosDeBienvenidaEnMemoria();
    private final TextosDeBienvenidaVigentes textos =
            new TextosDeBienvenidaVigentes(pieza -> "original de " + pieza.name(), cambios);

    @Test
    @DisplayName("sin cambios salen los del repo")
    void sinCambiosLosDelRepo() {
        assertThat(textos.soporteConLaTarjeta()).isEqualTo("original de SOPORTE_CON_LA_TARJETA");
        assertThat(textos.soporteFormal()).isEqualTo("original de SOPORTE_FORMAL");
        assertThat(textos.grupo()).isEqualTo("original de GRUPO");
    }

    @Test
    @DisplayName("el guardado reemplaza solo a su mensaje, y volver al original lo devuelve")
    void elGuardadoYLaVuelta() {
        cambios.registrar(CambioDeBienvenida.texto(PiezaDeBienvenida.SOPORTE_FORMAL, "Hola, {nombre}. Nuevo.", KELIN, AHORA));

        assertThat(textos.soporteFormal()).isEqualTo("Hola, {nombre}. Nuevo.");
        assertThat(textos.soporteConLaTarjeta()).isEqualTo("original de SOPORTE_CON_LA_TARJETA");
        assertThat(textos.grupo()).isEqualTo("original de GRUPO");

        cambios.registrar(CambioDeBienvenida.volverAlOriginal(PiezaDeBienvenida.SOPORTE_FORMAL, KELIN, AHORA));
        assertThat(textos.soporteFormal()).isEqualTo("original de SOPORTE_FORMAL");
    }
}

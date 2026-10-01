package com.renaser.os.leadership.domain.model.observacion;

import com.renaser.os.leadership.domain.model.observacion.ObservacionDeMentor.Contenido;
import com.renaser.os.leadership.domain.model.observacion.ObservacionDeMentor.Envio;
import com.renaser.os.shared.domain.FixedClock;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Arrays;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ObservacionDeMentorTest {

    private static final FixedClock CLOCK = FixedClock.at(Instant.parse("2026-10-01T15:00:00Z"));
    private static final UserId MENTOR = UserId.of(UUID.fromString("00000000-0000-0000-0000-0000000000a1"));
    private static final UserId LIDER = UserId.of(UUID.fromString("00000000-0000-0000-0000-0000000000c1"));
    private static final UUID ID = UUID.fromString("00000000-0000-0000-0000-0000000000f1");

    private static Contenido contenido(String texto, Envio envio) {
        return new Contenido(MENTOR, LIDER, TipoObservacion.RECONOCIMIENTO, texto, envio, "clave-1");
    }

    @Test
    @DisplayName("registra tipo, autor, texto limpio, envio y fecha del reloj")
    void registra() {
        UUID mensaje = UUID.randomUUID();
        ObservacionDeMentor o = ObservacionDeMentor.registrar(ID, contenido("  Muy buena respuesta  ",
                new Envio(true, mensaje)), CLOCK);

        assertThat(o.texto()).isEqualTo("Muy buena respuesta");
        assertThat(o.enviadaPorChat()).isTrue();
        assertThat(o.mensajeId()).isEqualTo(mensaje);
        assertThat(o.creadoEn()).isEqualTo(CLOCK.now());
        assertThat(o.autorId()).isEqualTo(LIDER);
    }

    @Test
    @DisplayName("texto vacio o de mas de 1000 caracteres: rechazado")
    void textoInvalido() {
        char[] largo = new char[1001];
        Arrays.fill(largo, 'a');

        assertThatThrownBy(() -> ObservacionDeMentor.registrar(ID, contenido("   ", Envio.SIN_ENVIAR), CLOCK))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ObservacionDeMentor.registrar(ID, contenido(new String(largo), Envio.SIN_ENVIAR), CLOCK))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("1000");
    }

    @Test
    @DisplayName("sin tipo, o un tipo que no es de los tres: rechazado")
    void tipoInvalido() {
        assertThatThrownBy(() -> new Contenido(MENTOR, LIDER, null, "x", Envio.SIN_ENVIAR, "c"))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> TipoObservacion.de("FELICITACION")).isInstanceOf(IllegalArgumentException.class);
        assertThat(TipoObservacion.de("alerta")).isEqualTo(TipoObservacion.ALERTA);
    }

    @Test
    @DisplayName("un mensaje del chat sin envio no tiene sentido; observarse a uno mismo tampoco")
    void coherencias() {
        assertThatThrownBy(() -> new Envio(false, UUID.randomUUID())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Contenido(LIDER, LIDER, TipoObservacion.ALERTA, "x", Envio.SIN_ENVIAR, "c"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("append-only: la clase no tiene un solo metodo que la cambie")
    void sinMutadores() {
        assertThat(Arrays.stream(ObservacionDeMentor.class.getDeclaredMethods())
                .filter(m -> java.lang.reflect.Modifier.isPublic(m.getModifiers()))
                .map(java.lang.reflect.Method::getName))
                .noneMatch(nombre -> nombre.startsWith("set") || nombre.startsWith("cambiar")
                        || nombre.startsWith("editar") || nombre.startsWith("borrar"));
    }
}

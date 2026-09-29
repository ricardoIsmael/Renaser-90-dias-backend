package com.renaser.os.chat.domain.model.semaforo;

import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TarjetaDelSemaforoTest {

    private static final UserId ANA = UserId.of(UUID.fromString("11111111-2222-3333-4444-555555555555"));
    private static final LocalDate DIA = LocalDate.of(2026, 9, 28);

    @Test
    @DisplayName("D-223: el texto que pidió el dueño, con el entero del día")
    void texto() {
        assertThat(new TarjetaDelSemaforo(ANA, DIA, ColorDeTarjeta.VERDE, 85).texto())
                .isEqualTo("Hoy llevas 85 % de tus hábitos.");
    }

    @Test
    @DisplayName("D-223: los ids se calculan de aprendiz + fecha: iguales en cada corrida, distintos por pieza, día y persona")
    void idsDeterministas() {
        TarjetaDelSemaforo tarjeta = new TarjetaDelSemaforo(ANA, DIA, ColorDeTarjeta.VERDE, 85);
        TarjetaDelSemaforo otraCorrida = new TarjetaDelSemaforo(ANA, DIA, ColorDeTarjeta.AMARILLO, 70);

        assertThat(otraCorrida.idDelTexto()).as("el porcentaje cambió en 5 minutos, el mensaje del día es el mismo")
                .isEqualTo(tarjeta.idDelTexto());
        assertThat(tarjeta.idDeLaImagen()).isNotEqualTo(tarjeta.idDelTexto());
        assertThat(new TarjetaDelSemaforo(ANA, DIA.plusDays(1), ColorDeTarjeta.VERDE, 85).idDelTexto())
                .isNotEqualTo(tarjeta.idDelTexto());
        assertThat(new TarjetaDelSemaforo(UserId.of(UUID.randomUUID()), DIA, ColorDeTarjeta.VERDE, 85).idDelTexto())
                .isNotEqualTo(tarjeta.idDelTexto());
    }

    @Test
    @DisplayName("D-223: una ruta por color y versión, la misma para todos")
    void rutas() {
        assertThat(ColorDeTarjeta.VERDE.rutaEnAlmacenamiento()).isEqualTo("semaforo/tarjetas/verde-v1.jpg");
        assertThat(ColorDeTarjeta.ROJO.recurso()).isEqualTo("semaforo/tarjetas/rojo.jpg");
    }

    @Test
    void porcentajeFueraDeRango() {
        assertThatThrownBy(() -> new TarjetaDelSemaforo(ANA, DIA, ColorDeTarjeta.ROJO, 101))
                .isInstanceOf(IllegalArgumentException.class);
    }
}

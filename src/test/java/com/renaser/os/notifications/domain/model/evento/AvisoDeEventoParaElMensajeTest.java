package com.renaser.os.notifications.domain.model.evento;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.ZoneId;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Lo que la spec 041 agrego a {@link AvisoDeEvento}: la frase del mensaje. Lo que la app ya decia no cambia. */
class AvisoDeEventoParaElMensajeTest {

    private static final Instant INICIO = Instant.parse("2026-11-07T00:00:00Z");

    private static AvisoDeEvento aviso() {
        return new AvisoDeEvento(1L, UUID.randomUUID(), "Mentoría", INICIO, ZoneId.of("America/Lima"), false);
    }

    // spec 041 FR-017
    @Test
    @DisplayName("el aviso de la app a los 60 minutos sigue diciendo «60 min»: solo el MENSAJE dice «1 hora»")
    void laAppNoCambia() {
        Instant aLaHora = INICIO.minusSeconds(3600);

        assertThat(aviso().cuerpo(aLaHora)).isEqualTo("Empieza en 60 min, a las 19:00.");
        assertThat(aviso().cuandoEnElMensaje(aLaHora)).isEqualTo("empieza en 1 hora, a las 19:00");
    }

    // spec 041 FR-017
    @Test
    @DisplayName("fuera de la hora justa el mensaje dice igual que la app")
    void igualQueLaApp() {
        for (int minutos : new int[] {10, 30, 45, 59}) {
            Instant ahora = INICIO.minusSeconds(minutos * 60L);
            assertThat(aviso().cuandoEnElMensaje(ahora)).isEqualTo(aviso().cuerpo(ahora).toLowerCase().replaceFirst("\\.$", ""));
        }
    }
}

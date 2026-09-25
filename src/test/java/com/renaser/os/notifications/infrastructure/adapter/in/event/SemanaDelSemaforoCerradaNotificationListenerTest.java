package com.renaser.os.notifications.infrastructure.adapter.in.event;

import com.renaser.os.notifications.application.ports.in.notificacion.EmitirNotificacionUseCase;
import com.renaser.os.notifications.application.ports.in.notificacion.EmitirNotificacionUseCase.EmitirNotificacionCommand;
import com.renaser.os.notifications.domain.model.notificacion.TipoNotificacion;
import com.renaser.os.points.api.ColorSemaforo;
import com.renaser.os.points.api.SemanaDelSemaforoCerradaEvent;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class SemanaDelSemaforoCerradaNotificationListenerTest {

    private static final LocalDate VIERNES = LocalDate.of(2026, 9, 25);

    @Mock
    private EmitirNotificacionUseCase emitirNotificacionUseCase;

    private final UUID persona = UUID.randomUUID();

    private EmitirNotificacionCommand emitidoPara(ColorSemaforo color, BigDecimal porcentaje) {
        SemanaDelSemaforoCerradaEvent evento = new SemanaDelSemaforoCerradaEvent(
                SemanaDelSemaforoCerradaEvent.claveDe(persona, VIERNES), persona, VIERNES.minusDays(6), VIERNES,
                porcentaje, color, 7, Instant.parse("2026-09-26T05:25:03Z"));

        new SemanaDelSemaforoCerradaNotificationListener(emitirNotificacionUseCase).on(evento);

        ArgumentCaptor<EmitirNotificacionCommand> captor = ArgumentCaptor.forClass(EmitirNotificacionCommand.class);
        verify(emitirNotificacionUseCase).emitir(captor.capture());
        return captor.getValue();
    }

    @Test
    @DisplayName("le llega a la persona medida, como RESUMEN_SEMANAL, con la clave del evento y la ruta /semaforo")
    void leLlegaALaPersona() {
        EmitirNotificacionCommand comando = emitidoPara(ColorSemaforo.VERDE, new BigDecimal("86.0"));

        assertThat(comando.usuarioId()).isEqualTo(UserId.of(persona));
        assertThat(comando.tipo()).isEqualTo(TipoNotificacion.RESUMEN_SEMANAL);
        assertThat(comando.origenEventoId()).isEqualTo(SemanaDelSemaforoCerradaEvent.claveDe(persona, VIERNES));
        assertThat(comando.rutaApp()).isEqualTo("/semaforo");
        assertThat(comando.titulo()).isEqualTo("Tu semana ya cerró");
        assertThat(comando.cuerpo()).isEqualTo("Mira tu semáforo de la semana en la app.");
    }

    /** El título y el cuerpo son también el texto del push, y el push no lleva métricas. */
    @Test
    @DisplayName("ni el titulo ni el cuerpo llevan cifras, color ni la palabra del color")
    void sinCifrasNiColores() {
        EmitirNotificacionCommand comando = emitidoPara(ColorSemaforo.ROJO, new BigDecimal("41.5"));

        for (String texto : new String[]{comando.titulo(), comando.cuerpo()}) {
            assertThat(texto).doesNotContainPattern("\\d");
            assertThat(texto.toLowerCase()).doesNotContain("verde", "amarillo", "rojo", "%");
            for (ColorSemaforo color : ColorSemaforo.values()) {
                assertThat(texto).doesNotContain(color.etiqueta());
            }
        }
    }
}

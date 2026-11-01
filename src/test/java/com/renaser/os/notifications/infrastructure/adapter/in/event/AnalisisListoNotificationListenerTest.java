package com.renaser.os.notifications.infrastructure.adapter.in.event;

import com.renaser.os.notifications.application.ports.in.notificacion.EmitirNotificacionUseCase;
import com.renaser.os.notifications.application.ports.in.notificacion.EmitirNotificacionUseCase.EmitirNotificacionCommand;
import com.renaser.os.notifications.domain.model.notificacion.TipoNotificacion;
import com.renaser.os.rag.api.AnalisisListoEvent;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

/** «Tu análisis está listo» (spec 031, FR-024): le llega a la persona, con un texto fijo que nunca lleva el análisis, y se puede apagar aparte. */
@ExtendWith(MockitoExtension.class)
class AnalisisListoNotificationListenerTest {

    @Mock
    private EmitirNotificacionUseCase emitir;

    private final UUID clave = UUID.randomUUID();
    private final UUID persona = UUID.randomUUID();

    private EmitirNotificacionCommand emitidoPara(String periodo) {
        new AnalisisListoNotificationListener(emitir).on(new AnalisisListoEvent(clave, persona, periodo, LocalDate.of(2026, 10, 19),
                Instant.parse("2026-10-26T06:00:00Z")));
        ArgumentCaptor<EmitirNotificacionCommand> captor = ArgumentCaptor.forClass(EmitirNotificacionCommand.class);
        verify(emitir).emitir(captor.capture());
        return captor.getValue();
    }

    @Test
    @DisplayName("la semana: le llega a la persona como ANALISIS_80_20, con la clave del análisis y la ruta de la hoja en ese periodo")
    void laSemana() {
        EmitirNotificacionCommand comando = emitidoPara("SEMANA");

        assertThat(comando.usuarioId()).isEqualTo(UserId.of(persona));
        assertThat(comando.tipo()).isEqualTo(TipoNotificacion.ANALISIS_80_20);
        assertThat(comando.titulo()).isEqualTo("Tu análisis de la semana está listo");
        assertThat(comando.cuerpo()).isEqualTo("Entra a Plan y míralo.");
        assertThat(comando.rutaApp()).isEqualTo("/plan/80-20?periodo=SEMANA&inicio=2026-10-19");
        assertThat(comando.origenEventoId()).isEqualTo(clave);
    }

    @Test
    @DisplayName("el mes tiene su propio título")
    void elMes() {
        assertThat(emitidoPara("MES").titulo()).isEqualTo("Tu análisis del mes está listo");
    }

    @Test
    @DisplayName("es un tipo propio, se ve en la campana y se silencia sin silenciar el aviso del semáforo")
    void tipoPropio() {
        assertThat(TipoNotificacion.ANALISIS_80_20.seVeEnLaCampana()).isTrue();
        assertThat(TipoNotificacion.ANALISIS_80_20.seEntregaConLasNotificacionesApagadas()).isFalse();
        assertThat(TipoNotificacion.ANALISIS_80_20).isNotIn(TipoNotificacion.RESUMEN_SEMANAL, TipoNotificacion.REVISION_DEL_LIDER);
    }
}

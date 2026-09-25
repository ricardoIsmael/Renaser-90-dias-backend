package com.renaser.os.notifications.infrastructure.adapter.in.event;

import com.renaser.os.mentoring.api.ResumenSemanalGeneralEvent;
import com.renaser.os.notifications.application.ports.in.notificacion.EmitirNotificacionUseCase;
import com.renaser.os.notifications.application.ports.in.notificacion.EmitirNotificacionUseCase.EmitirNotificacionCommand;
import com.renaser.os.notifications.domain.model.notificacion.TipoNotificacion;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.ParticipacionProgramaFinder;
import com.renaser.os.users.api.UserRole;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ResumenSemanalGeneralNotificationListenerTest {

    private static final LocalDate VIERNES = LocalDate.of(2026, 9, 25);

    @Mock
    private EmitirNotificacionUseCase emitirNotificacionUseCase;
    @Mock
    private ParticipacionProgramaFinder participacionFinder;

    private final UserId lider = UserId.of(UUID.randomUUID());
    private final UserId admin = UserId.of(UUID.randomUUID());
    private final UserId alquimista = UserId.of(UUID.randomUUID());
    private final UUID clave = UUID.randomUUID();

    private ResumenSemanalGeneralEvent evento() {
        return new ResumenSemanalGeneralEvent(clave, VIERNES.minusDays(6), VIERNES, 4, 40, 12, 8, 2, 62,
                Instant.parse("2026-09-26T05:40:00Z"));
    }

    private List<EmitirNotificacionCommand> comandosEmitidos(int veces) {
        ArgumentCaptor<EmitirNotificacionCommand> captor = ArgumentCaptor.forClass(EmitirNotificacionCommand.class);
        verify(emitirNotificacionUseCase, times(veces)).emitir(captor.capture());
        return captor.getAllValues();
    }

    @Test
    @DisplayName("le llega a cada lider, admin y alquimista activo, con la MISMA clave de la semana")
    void leLlegaALaConduccion() {
        when(participacionFinder.usuariosActivosConRol(
                Set.of(UserRole.MENTOR_LEAD, UserRole.ADMIN, UserRole.ALCHEMIST)))
                .thenReturn(List.of(lider, admin, alquimista));

        new ResumenSemanalGeneralNotificationListener(emitirNotificacionUseCase, participacionFinder).on(evento());

        List<EmitirNotificacionCommand> comandos = comandosEmitidos(3);
        assertThat(comandos).extracting(EmitirNotificacionCommand::usuarioId)
                .containsExactly(lider, admin, alquimista);
        assertThat(comandos).allSatisfy(comando -> {
            assertThat(comando.tipo()).isEqualTo(TipoNotificacion.RESUMEN_SEMANAL);
            assertThat(comando.origenEventoId()).isEqualTo(clave);
            assertThat(comando.rutaApp()).isEqualTo("/semaforo/grupos");
            assertThat(comando.titulo()).isEqualTo("Semana cerrada");
            assertThat(comando.cuerpo()).isEqualTo("El semáforo de los grupos ya está listo.");
        });
    }

    /** Sale igual por push: ni cuántos grupos ni cuántos aprendices en cada color. */
    @Test
    @DisplayName("el texto no lleva cifras ni colores")
    void sinCifrasNiColores() {
        when(participacionFinder.usuariosActivosConRol(any())).thenReturn(List.of(admin));

        new ResumenSemanalGeneralNotificationListener(emitirNotificacionUseCase, participacionFinder).on(evento());

        EmitirNotificacionCommand comando = comandosEmitidos(1).getFirst();
        for (String texto : new String[]{comando.titulo(), comando.cuerpo()}) {
            assertThat(texto).doesNotContainPattern("\\d");
            assertThat(texto.toLowerCase()).doesNotContain("verde", "amarillo", "rojo");
        }
    }

    @Test
    @DisplayName("sin nadie activo con esos roles no explota: no hay a quien avisarle")
    void sinDestinatariosNoEmite() {
        when(participacionFinder.usuariosActivosConRol(any())).thenReturn(List.of());

        new ResumenSemanalGeneralNotificationListener(emitirNotificacionUseCase, participacionFinder).on(evento());

        verify(emitirNotificacionUseCase, never()).emitir(any());
    }
}

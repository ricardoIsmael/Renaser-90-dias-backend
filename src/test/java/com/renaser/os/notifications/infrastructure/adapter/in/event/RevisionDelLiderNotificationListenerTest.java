package com.renaser.os.notifications.infrastructure.adapter.in.event;

import com.renaser.os.leadership.api.RevisionDelLiderEvent;
import com.renaser.os.notifications.application.ports.in.notificacion.EmitirNotificacionUseCase;
import com.renaser.os.notifications.application.ports.in.notificacion.EmitirNotificacionUseCase.EmitirNotificacionCommand;
import com.renaser.os.notifications.domain.model.notificacion.TipoNotificacion;
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

/** «Tu líder revisó a tu grupo» (spec 005, FR-017 y FR-018): a quién le llega, con qué clave y a dónde lleva. */
@ExtendWith(MockitoExtension.class)
class RevisionDelLiderNotificationListenerTest {

    @Mock
    private EmitirNotificacionUseCase emitir;

    private final UUID clave = UUID.randomUUID();
    private final UUID mentor = UUID.randomUUID();
    private final UUID grupo = UUID.randomUUID();

    @Test
    @DisplayName("le llega al mentor como REVISION_DEL_LIDER, con la clave del dia y la ruta de Mi grupo")
    void leLlegaAlMentor() {
        RevisionDelLiderEvent evento = new RevisionDelLiderEvent(clave, mentor, grupo, "Hoy revisó a Ana y Luis", 2,
                LocalDate.of(2026, 10, 18), Instant.parse("2026-10-19T01:10:00Z"));

        new RevisionDelLiderNotificationListener(emitir).on(evento);

        ArgumentCaptor<EmitirNotificacionCommand> captor = ArgumentCaptor.forClass(EmitirNotificacionCommand.class);
        verify(emitir).emitir(captor.capture());
        EmitirNotificacionCommand comando = captor.getValue();
        assertThat(comando.usuarioId()).isEqualTo(UserId.of(mentor));
        assertThat(comando.tipo()).isEqualTo(TipoNotificacion.REVISION_DEL_LIDER);
        assertThat(comando.titulo()).isEqualTo("Tu líder revisó a tu grupo");
        assertThat(comando.cuerpo()).isEqualTo("Hoy revisó a Ana y Luis");
        assertThat(comando.rutaApp()).isEqualTo("/mentor/groups/" + grupo + "/semaforo");
        assertThat(comando.origenEventoId()).isEqualTo(clave);
    }

    @Test
    @DisplayName("es un tipo propio y se ve en la campana: el mentor puede silenciarlo sin silenciar otra cosa")
    void tipoPropio() {
        assertThat(TipoNotificacion.REVISION_DEL_LIDER.seVeEnLaCampana()).isTrue();
        assertThat(TipoNotificacion.REVISION_DEL_LIDER.seEntregaConLasNotificacionesApagadas()).isFalse();
        assertThat(TipoNotificacion.REVISION_DEL_LIDER).isNotIn(TipoNotificacion.RESUMEN_SEMANAL,
                TipoNotificacion.ACOMPANAMIENTO_ALUMNO);
    }
}

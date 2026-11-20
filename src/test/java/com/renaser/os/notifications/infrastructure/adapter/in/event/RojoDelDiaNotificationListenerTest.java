package com.renaser.os.notifications.infrastructure.adapter.in.event;

import com.renaser.os.notifications.application.ports.in.notificacion.SuperarNotificacionesUseCase;
import com.renaser.os.mentoring.api.RojoDelDiaEvent;
import com.renaser.os.notifications.application.ports.in.notificacion.EmitirNotificacionUseCase;
import com.renaser.os.notifications.application.ports.in.notificacion.EmitirNotificacionUseCase.EmitirNotificacionCommand;
import com.renaser.os.notifications.application.ports.in.notificacion.EmitirNotificacionUseCase.ExtrasDelPush;
import com.renaser.os.notifications.domain.model.notificacion.EntregaPush;
import com.renaser.os.notifications.domain.model.notificacion.Notificacion;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.notifications.domain.model.notificacion.TipoNotificacion;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** El texto, la ruta, los botones y la clave del aviso de rojo (spec 057, FR-032, FR-033 y FR-035). */
class RojoDelDiaNotificationListenerTest {

    private final EmitirNotificacionUseCase emitir = mock(EmitirNotificacionUseCase.class);
    private final SuperarNotificacionesUseCase superar = mock(SuperarNotificacionesUseCase.class);
    private final RojoDelDiaNotificationListener listener = new RojoDelDiaNotificationListener(emitir, superar);

    @Test
    @DisplayName("spec 057 FR-033 y FR-035: «<Nombre> está en rojo hoy», con el porcentaje, la ruta de la ficha, la categoría rojo-del-dia y el grupo y el aprendiz en data")
    void textoRutaYBotones() { // spec 057 FR-033, FR-035
        UUID clave = UUID.randomUUID();
        UUID mentor = UUID.randomUUID();
        UUID aprendiz = UUID.randomUUID();
        UUID grupo = UUID.randomUUID();

        listener.on(new RojoDelDiaEvent(clave, mentor, aprendiz, grupo, "Ana Pérez", 33, Instant.parse("2026-11-21T00:05:00Z")));

        ArgumentCaptor<EmitirNotificacionCommand> comando = ArgumentCaptor.forClass(EmitirNotificacionCommand.class);
        ArgumentCaptor<ExtrasDelPush> extras = ArgumentCaptor.forClass(ExtrasDelPush.class);
        verify(emitir).emitir(comando.capture(), eq(EntregaPush.TODOS), isNull(), isNull(), extras.capture());
        assertThat(comando.getValue().usuarioId().value()).isEqualTo(mentor);
        assertThat(comando.getValue().tipo()).isEqualTo(TipoNotificacion.ROJO_DEL_DIA);
        assertThat(comando.getValue().titulo()).isEqualTo("Ana Pérez está en rojo hoy");
        assertThat(comando.getValue().cuerpo()).isEqualTo("Lleva 33 % de sus hábitos. Toca para ver su ficha y escribirle.");
        assertThat(comando.getValue().rutaApp()).isEqualTo("/mentor/groups/" + grupo + "/learners/" + aprendiz);
        assertThat(comando.getValue().origenEventoId()).as("FR-032: la clave viaja como origenEventoId").isEqualTo(clave);
        assertThat(extras.getValue().categoriaId()).isEqualTo("rojo-del-dia");
        assertThat(extras.getValue().datos()).containsEntry("grupoId", grupo.toString()).containsEntry("aprendizId", aprendiz.toString());
    }

    @Test
    @DisplayName("spec 057 FR-032: el aviso de una semana nueva supera (deja leído) el de la semana anterior del mismo aprendiz, y el patrón calza con el cuerpo")
    void superaElAvisoDeLaSemanaAnterior() { // spec 057 FR-032
        UUID mentor = UUID.randomUUID();
        UUID aprendiz = UUID.randomUUID();
        UUID grupo = UUID.randomUUID();
        Notificacion nueva = mock(Notificacion.class);
        when(nueva.id()).thenReturn(7L);
        when(emitir.emitir(any(), any(), any(), any(), any())).thenReturn(Optional.of(nueva));

        RojoDelDiaEvent evento = new RojoDelDiaEvent(UUID.randomUUID(), mentor, aprendiz, grupo, "Ana Pérez", 12, Instant.parse("2026-11-21T00:05:00Z"));
        listener.on(evento);

        verify(superar).superarAnteriores(UserId.of(mentor), TipoNotificacion.ROJO_DEL_DIA,
                "/mentor/groups/" + grupo + "/learners/" + aprendiz, RojoDelDiaNotificationListener.PATRON_DEL_CUERPO, 7L);
        String comoRegex = RojoDelDiaNotificationListener.PATRON_DEL_CUERPO.replace(".", "\\.").replace("%", ".*");
        assertThat(RojoDelDiaNotificationListener.cuerpo(evento)).as("el LIKE de superar tiene que calzar con el texto real").matches(comoRegex);
    }
}

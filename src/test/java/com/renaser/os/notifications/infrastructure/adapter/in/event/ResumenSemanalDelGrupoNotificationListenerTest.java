package com.renaser.os.notifications.infrastructure.adapter.in.event;

import com.renaser.os.mentoring.api.ResumenSemanalDelGrupoEvent;
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

@ExtendWith(MockitoExtension.class)
class ResumenSemanalDelGrupoNotificationListenerTest {

    private static final LocalDate VIERNES = LocalDate.of(2026, 9, 25);

    @Mock
    private EmitirNotificacionUseCase emitirNotificacionUseCase;

    private final UUID clave = UUID.randomUUID();
    private final UUID mentor = UUID.randomUUID();
    private final UUID grupo = UUID.randomUUID();

    private EmitirNotificacionCommand emitidoPara(String nombreDelGrupo) {
        ResumenSemanalDelGrupoEvent evento = new ResumenSemanalDelGrupoEvent(clave, mentor, grupo, nombreDelGrupo,
                VIERNES.minusDays(6), VIERNES, 5, 2, 1, 0, 8, Instant.parse("2026-09-26T05:40:00Z"));

        new ResumenSemanalDelGrupoNotificationListener(emitirNotificacionUseCase).on(evento);

        ArgumentCaptor<EmitirNotificacionCommand> captor = ArgumentCaptor.forClass(EmitirNotificacionCommand.class);
        verify(emitirNotificacionUseCase).emitir(captor.capture());
        return captor.getValue();
    }

    @Test
    @DisplayName("le llega al mentor del grupo, como RESUMEN_SEMANAL, con la clave del grupo y la ruta a su tabla")
    void leLlegaAlMentor() {
        EmitirNotificacionCommand comando = emitidoPara("Grupo Fénix");

        assertThat(comando.usuarioId()).isEqualTo(UserId.of(mentor));
        assertThat(comando.tipo()).isEqualTo(TipoNotificacion.RESUMEN_SEMANAL);
        assertThat(comando.origenEventoId()).isEqualTo(clave);
        assertThat(comando.rutaApp()).isEqualTo("/mentor/groups/" + grupo + "/semaforo");
        assertThat(comando.titulo()).isEqualTo("Tu grupo cerró la semana");
        assertThat(comando.cuerpo()).isEqualTo("El semáforo de Grupo Fénix ya está listo.");
    }

    /** Sale igual por push: nada de cuántos quedaron en cada color. */
    @Test
    @DisplayName("el texto nombra al grupo pero no lleva cifras ni colores")
    void sinCifrasNiColores() {
        EmitirNotificacionCommand comando = emitidoPara("Fénix");

        assertThat(comando.cuerpo()).contains("Fénix");
        for (String texto : new String[]{comando.titulo(), comando.cuerpo()}) {
            assertThat(texto).doesNotContainPattern("\\d");
            assertThat(texto.toLowerCase()).doesNotContain("verde", "amarillo", "rojo", "atención", "problemas");
        }
    }

    @Test
    @DisplayName("un grupo sin nombre igual avisa, sin dejar un hueco en el texto")
    void grupoSinNombre() {
        assertThat(emitidoPara("  ").cuerpo()).isEqualTo("El semáforo de tu grupo ya está listo.");
    }
}

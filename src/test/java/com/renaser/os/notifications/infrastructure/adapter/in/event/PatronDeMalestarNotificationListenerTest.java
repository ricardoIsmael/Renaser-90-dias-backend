package com.renaser.os.notifications.infrastructure.adapter.in.event;

import com.renaser.os.notifications.application.ports.in.notificacion.EmitirNotificacionUseCase;
import com.renaser.os.notifications.application.ports.in.notificacion.EmitirNotificacionUseCase.EmitirNotificacionCommand;
import com.renaser.os.notifications.domain.model.notificacion.TipoNotificacion;
import com.renaser.os.rag.api.PatronDeMalestarRepetidoEvent;
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
class PatronDeMalestarNotificationListenerTest {

    @Mock
    private EmitirNotificacionUseCase emitirNotificacionUseCase;
    @Mock
    private ParticipacionProgramaFinder participacionFinder;

    private final UserId admin = UserId.of(UUID.randomUUID());
    private final UserId alquimista = UserId.of(UUID.randomUUID());
    private final UUID clave = UUID.randomUUID();
    private final UUID aprendiz = UUID.randomUUID();

    private PatronDeMalestarRepetidoEvent evento() {
        return new PatronDeMalestarRepetidoEvent(clave, aprendiz, "Ana Quispe", 3, 7, Instant.now());
    }

    private List<EmitirNotificacionCommand> comandosEmitidos(int veces) {
        ArgumentCaptor<EmitirNotificacionCommand> captor = ArgumentCaptor.forClass(EmitirNotificacionCommand.class);
        verify(emitirNotificacionUseCase, times(veces)).emitir(captor.capture());
        return captor.getAllValues();
    }

    @Test
    @DisplayName("le llega a ADMIN y a ALCHEMIST, con la clave del episodio como origen")
    void avisaATodaLaAdministracion() {
        when(participacionFinder.usuariosActivosConRol(Set.of(UserRole.ADMIN, UserRole.ALCHEMIST)))
                .thenReturn(List.of(admin, alquimista));

        new PatronDeMalestarNotificationListener(emitirNotificacionUseCase, participacionFinder).on(evento());

        List<EmitirNotificacionCommand> comandos = comandosEmitidos(2);
        assertThat(comandos).extracting(EmitirNotificacionCommand::usuarioId).containsExactly(admin, alquimista);
        assertThat(comandos).allSatisfy(comando -> {
            assertThat(comando.tipo()).isEqualTo(TipoNotificacion.PATRON_DE_MALESTAR_REPETIDO);
            // La clave identifica el EPISODIO: la misma para los dos, y el indice unico de
            // notificaciones le entrega UNA a cada uno por mas veces que se revise el patron.
            assertThat(comando.origenEventoId()).isEqualTo(clave);
            assertThat(comando.rutaApp()).isEqualTo("/admin/trainees/" + aprendiz);
        });
    }

    /**
     * El texto tiene que decir que se repitio un patron, y no afirmar nada sobre la persona.
     * Llamarlo "crisis", "riesgo" o "depresion" convertiria un {@code contains} sobre una lista de
     * frases en un diagnostico escrito por el backend.
     */
    @Test
    @DisplayName("el texto no diagnostica nada")
    void elTextoNoAfirmaNadaSobreLaPersona() {
        when(participacionFinder.usuariosActivosConRol(any())).thenReturn(List.of(admin));

        new PatronDeMalestarNotificationListener(emitirNotificacionUseCase, participacionFinder).on(evento());

        EmitirNotificacionCommand comando = comandosEmitidos(1).getFirst();
        assertThat(comando.cuerpo()).contains("Ana Quispe").contains("No es un diagnostico");
        assertThat(comando.titulo().toLowerCase()).doesNotContain("crisis").doesNotContain("riesgo");
        assertThat(comando.cuerpo().toLowerCase()).doesNotContain("crisis").doesNotContain("depres");
    }

    @Test
    @DisplayName("sin administradores activos no explota: no hay a quien avisarle")
    void sinAdministradoresNoEmiteNada() {
        when(participacionFinder.usuariosActivosConRol(any())).thenReturn(List.of());

        new PatronDeMalestarNotificationListener(emitirNotificacionUseCase, participacionFinder).on(evento());

        verify(emitirNotificacionUseCase, never()).emitir(any());
    }
}

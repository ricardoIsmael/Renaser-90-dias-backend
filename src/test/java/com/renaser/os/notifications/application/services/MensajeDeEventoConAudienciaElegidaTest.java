package com.renaser.os.notifications.application.services;

import com.renaser.os.notifications.application.ports.in.evento.AvisoDeMensajeDelEvento;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.UserRole;
import com.renaser.os.users.api.UserStatus;
import com.renaser.os.users.api.UserSummary;
import com.renaser.os.users.api.UserSummaryFinder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Spec 044: el chat y el WhatsApp de un evento con audiencia elegida le llegan a todo invitado (haya dicho «Voy» o no) y de cualquier rol; los
 * eventos de siempre siguen con la regla de la 041 (solo «Voy», solo aprendices).
 */
class MensajeDeEventoConAudienciaElegidaTest {

    private static final Instant INICIO = Instant.parse("2026-11-07T00:00:00Z");

    private final UserSummaryFinder usuarios = mock(UserSummaryFinder.class);
    private final DestinatarioDelMensajeDeEvento destinatario = new DestinatarioDelMensajeDeEvento(usuarios,
            new ReglasDelMensajeDeEvento(new MockEnvironment()));

    private UserId persona(UserRole rol) {
        UserId id = UserId.of(UUID.randomUUID());
        when(usuarios.findById(id)).thenReturn(Optional.of(new UserSummary(id, "Luisa Quispe", null, rol, UserStatus.ACTIVE, null, false)));
        return id;
    }

    private AvisoDeMensajeDelEvento aviso(UserId quien, boolean dijoVoy, boolean elegida) {
        return new AvisoDeMensajeDelEvento(5L, UUID.randomUUID(), quien, INICIO, "America/Lima", "Sesión de rescate", 10, dijoVoy, "ESCUELA",
                "https://meet.google.com/abc-defg-hij", null, elegida);
    }

    // spec 044 FR-021
    @Test
    @DisplayName("FR-021: un invitado que todavía no respondió recibe el mensaje; en un evento para todos, no (regla «Voy» de la 041)")
    void todosLosInvitados() {
        UserId aprendiz = persona(UserRole.TRAINEE);

        assertThat(destinatario.siLeCorresponde(aviso(aprendiz, false, true))).isPresent();
        assertThat(destinatario.siLeCorresponde(aviso(aprendiz, false, false))).isEmpty();
    }

    // spec 044 FR-024
    @Test
    @DisplayName("FR-024 (P3): un mentor, el líder o la Alquimista invitados reciben el mensaje aunque la 041 diga «solo aprendices»")
    void tambienElPersonal() {
        for (UserRole rol : new UserRole[]{UserRole.MENTOR, UserRole.MENTOR_LEAD, UserRole.ALCHEMIST}) {
            UserId quien = persona(rol);
            assertThat(destinatario.siLeCorresponde(aviso(quien, true, true))).as(rol.name()).isPresent();
            assertThat(destinatario.siLeCorresponde(aviso(quien, true, false))).as(rol.name()).isEmpty();
        }
    }

    // spec 044 FR-017, SC-004
    @Test
    @DisplayName("FR-017: el texto del mensaje es el mismo con o sin audiencia elegida: nunca dice el color ni por qué se lo invitó")
    void elTextoNoDiceElMotivo() {
        UserId aprendiz = persona(UserRole.TRAINEE);
        Instant ahora = INICIO.minusSeconds(600);
        String conAudiencia = aviso(aprendiz, true, true).mensaje().textoParaElChat(ahora)
                + String.join("|", aviso(aprendiz, true, true).mensaje().huecosDeWhatsApp("Rosa", "Escuela Renaser", ahora));

        assertThat(conAudiencia).isEqualTo(aviso(aprendiz, true, false).mensaje().textoParaElChat(ahora)
                + String.join("|", aviso(aprendiz, true, false).mensaje().huecosDeWhatsApp("Rosa", "Escuela Renaser", ahora)));
        assertThat(conAudiencia.toLowerCase()).doesNotContain("rojo", "semáforo", "semaforo", "amarillo", "invit");
    }
}

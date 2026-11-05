package com.renaser.os.notifications.application.services;

import com.renaser.os.chat.api.RecordatorioDeEventoEnElChat;
import com.renaser.os.notifications.application.ports.in.evento.AvisoDeMensajeDelEvento;
import com.renaser.os.notifications.application.ports.out.preferencia.LoadPreferenciasPort;
import com.renaser.os.notifications.domain.model.notificacion.TipoNotificacion;
import com.renaser.os.shared.domain.FixedClock;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.UserRole;
import com.renaser.os.users.api.UserStatus;
import com.renaser.os.users.api.UserSummary;
import com.renaser.os.users.api.UserSummaryFinder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.env.MockEnvironment;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * El recordatorio de un evento en el chat de soporte (spec 041, FR-001 a FR-006). {@code chat} es un doble: lo que se prueba es A QUIEN se le escribe y CUANDO,
 * y que el texto sea el del aviso de la app con el enlace. Que escribir dos veces no duplique lo prueba {@code RecordatorioDeEventoEnSoporteServiceTest}.
 */
class RecordatorioDeEventoEnElChatServiceTest {

    private static final Instant INICIO = Instant.parse("2026-11-07T00:00:00Z");
    private static final Instant A_LOS_DIEZ = INICIO.minus(Duration.ofMinutes(10));

    private final UserSummaryFinder usuarios = mock(UserSummaryFinder.class);
    private final LoadPreferenciasPort preferencias = mock(LoadPreferenciasPort.class);
    private final RecordatorioDeEventoEnElChat chat = mock(RecordatorioDeEventoEnElChat.class);
    private final MockEnvironment entorno = new MockEnvironment();
    private final InterruptoresDePrueba interruptores = InterruptoresDePrueba.todoPrendido();
    private final UserId aprendiz = UserId.of(UUID.randomUUID());
    private RecordatorioDeEventoEnElChatService servicio;

    @BeforeEach
    void armar() {
        persona(aprendiz, UserRole.TRAINEE, UserStatus.ACTIVE, false);
        when(preferencias.habilitadaPara(any(), any())).thenReturn(Optional.empty());
        when(chat.escribir(any(), anyLong(), anyString())).thenReturn(RecordatorioDeEventoEnElChat.Resultado.ESCRITO);
        ReglasDelMensajeDeEvento reglas = new ReglasDelMensajeDeEvento(entorno);
        servicio = new RecordatorioDeEventoEnElChatService(new DestinatarioDelMensajeDeEvento(usuarios, reglas), preferencias, chat, interruptores,
                FixedClock.at(A_LOS_DIEZ));
    }

    private void persona(UserId id, UserRole rol, UserStatus estado, boolean dePrueba) {
        when(usuarios.findById(id)).thenReturn(Optional.of(new UserSummary(id, "Rosa Quispe", null, rol, estado, null, dePrueba)));
    }

    private AvisoDeMensajeDelEvento aviso(UserId quien, boolean dijoVoy) {
        return new AvisoDeMensajeDelEvento(5L, UUID.randomUUID(), quien, INICIO, "America/Lima", "Clase de manifestación", 10, dijoVoy, "DIPLOMADO",
                "https://meet.google.com/abc-defg-hij", null);
    }

    // spec 041 FR-001
    @Test
    @DisplayName("quien dijo «Voy» recibe en su chat el titulo, cuando empieza y el enlace, con la fila de la cola como clave")
    void escribeElMensaje() {
        servicio.recordar(aviso(aprendiz, true));

        ArgumentCaptor<String> texto = ArgumentCaptor.forClass(String.class);
        verify(chat).escribir(eq(aprendiz), eq(5L), texto.capture());
        assertThat(texto.getValue()).isEqualTo("Clase de manifestación\nEmpieza en 10 min, a las 19:00.\nEntra aquí: https://meet.google.com/abc-defg-hij");
    }

    // spec 041 FR-003, SC-002
    @Test
    @DisplayName("quien apago «Recordatorios de eventos» no recibe el mensaje")
    void laPreferenciaManda() {
        when(preferencias.habilitadaPara(aprendiz, TipoNotificacion.RECORDATORIO_EVENTO)).thenReturn(Optional.of(false));

        servicio.recordar(aviso(aprendiz, true));

        verify(chat, never()).escribir(any(), anyLong(), anyString());
    }

    // spec 041 FR-003, SC-002
    @Test
    @DisplayName("la baja del WhatsApp no apaga el chat: son preferencias distintas")
    void laBajaDelWhatsAppNoApagaElChat() {
        when(preferencias.habilitadaPara(aprendiz, TipoNotificacion.RECORDATORIO_EVENTO_WHATSAPP)).thenReturn(Optional.of(false));

        servicio.recordar(aviso(aprendiz, true));

        verify(chat).escribir(eq(aprendiz), eq(5L), anyString());
    }

    // spec 041 FR-003, SC-002
    @Test
    @DisplayName("una cuenta suspendida o de prueba tampoco")
    void suspendidasYDePrueba() {
        UserId suspendida = UserId.of(UUID.randomUUID());
        UserId dePrueba = UserId.of(UUID.randomUUID());
        persona(suspendida, UserRole.TRAINEE, UserStatus.SUSPENDED, false);
        persona(dePrueba, UserRole.TRAINEE, UserStatus.ACTIVE, true);

        servicio.recordar(aviso(suspendida, true));
        servicio.recordar(aviso(dePrueba, true));

        verify(chat, never()).escribir(any(), anyLong(), anyString());
    }

    // spec 041 FR-015, SC-016 — Escenario 3 y 5 de la historia 1
    @Test
    @DisplayName("quien no dijo «Voy» (con VOY) y un rol que no recibe (por defecto, todo el personal) no tienen mensaje en el chat")
    void audienciaYRoles() {
        UserId mentor = UserId.of(UUID.randomUUID());
        persona(mentor, UserRole.MENTOR, UserStatus.ACTIVE, false);

        servicio.recordar(aviso(aprendiz, false));
        servicio.recordar(aviso(mentor, true));

        verify(chat, never()).escribir(any(), anyLong(), anyString());
    }

    // spec 041 FR-015, SC-016
    @Test
    @DisplayName("con TODOS, el aprendiz que no dijo «Voy» tambien lo recibe")
    void conTodos() {
        entorno.setProperty(ReglasDelMensajeDeEvento.PROPIEDAD_AUDIENCIA, "TODOS");

        servicio.recordar(aviso(aprendiz, false));

        verify(chat).escribir(eq(aprendiz), eq(5L), anyString());
    }

    // spec 041 FR-016, SC-007
    @Test
    @DisplayName("con el evento ya empezado, o con mas de 15 minutos de atraso, no se escribe nada en el chat")
    void tardeOEmpezado() {
        var servicioTardio = new RecordatorioDeEventoEnElChatService(new DestinatarioDelMensajeDeEvento(usuarios, new ReglasDelMensajeDeEvento(entorno)),
                preferencias, chat, interruptores, FixedClock.at(INICIO.plusSeconds(5)));
        var servicioAtrasado = new RecordatorioDeEventoEnElChatService(new DestinatarioDelMensajeDeEvento(usuarios, new ReglasDelMensajeDeEvento(entorno)),
                preferencias, chat, interruptores, FixedClock.at(INICIO.minus(Duration.ofMinutes(9))));

        servicioTardio.recordar(aviso(aprendiz, true));
        // El aviso es el de 30 minutos y llega 21 minutos tarde.
        servicioAtrasado.recordar(new AvisoDeMensajeDelEvento(6L, UUID.randomUUID(), aprendiz, INICIO, "America/Lima", "Clase", 30, true, "ESCUELA", null, null));

        verify(chat, never()).escribir(any(), anyLong(), anyString());
    }

    // spec 041 FR-016
    @Test
    @DisplayName("sale a cualquier hora: un aviso de las 03:00 se escribe")
    void aCualquierHora() {
        Instant inicio = Instant.parse("2026-11-07T08:30:00Z");   // 03:30 en Lima
        var deMadrugada = new RecordatorioDeEventoEnElChatService(new DestinatarioDelMensajeDeEvento(usuarios, new ReglasDelMensajeDeEvento(entorno)),
                preferencias, chat, interruptores, FixedClock.at(inicio.minus(Duration.ofMinutes(30))));

        deMadrugada.recordar(new AvisoDeMensajeDelEvento(7L, UUID.randomUUID(), aprendiz, inicio, "America/Lima", "Meditación", 30, true, "ESCUELA", null, null));

        verify(chat).escribir(eq(aprendiz), eq(7L), anyString());
    }

    // spec 041 FR-006
    @Test
    @DisplayName("con el interruptor de eventos apagado no se escribe nada")
    void eventosApagado() {
        interruptores.apagar("eventos");

        servicio.recordar(aviso(aprendiz, true));

        verify(chat, never()).escribir(any(), anyLong(), anyString());
    }

    // spec 041 FR-005
    @Test
    @DisplayName("quien no tiene chat de soporte (el personal, si luego se le incluye) no rompe nada: chat responde que no hay y no se crea uno")
    void sinChatDeSoporte() {
        entorno.setProperty(ReglasDelMensajeDeEvento.PROPIEDAD_ROLES, "TRAINEE,MENTOR");
        UserId mentor = UserId.of(UUID.randomUUID());
        persona(mentor, UserRole.MENTOR, UserStatus.ACTIVE, false);
        when(chat.escribir(eq(mentor), anyLong(), anyString())).thenReturn(RecordatorioDeEventoEnElChat.Resultado.SIN_CHAT_DE_SOPORTE);

        servicio.recordar(aviso(mentor, true));

        verify(chat).escribir(eq(mentor), eq(5L), anyString());
    }
}

package com.renaser.os.users.application.services;

import com.renaser.os.shared.domain.FixedClock;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.EmergenciaPedidaEvent;
import com.renaser.os.users.api.EmergenciaResueltaEvent;
import com.renaser.os.users.api.FasePrograma;
import com.renaser.os.users.api.UserRole;
import com.renaser.os.users.api.UserStatus;
import com.renaser.os.users.application.ports.in.emergencia.PedirAyudaPorEmergenciaUseCase.PedirAyudaCommand;
import com.renaser.os.users.application.ports.out.emergencia.LoadSolicitudDeEmergenciaPort;
import com.renaser.os.users.application.ports.out.emergencia.SaveSolicitudDeEmergenciaPort;
import com.renaser.os.users.application.ports.out.participante.LoadParticipacionProgramaPort;
import com.renaser.os.users.application.ports.out.user.LoadUserPort;
import com.renaser.os.users.domain.model.emergencia.EstadoDeEmergencia;
import com.renaser.os.users.domain.model.emergencia.SolicitudDeEmergencia;
import com.renaser.os.users.domain.model.participante.ParticipacionPrograma;
import com.renaser.os.users.domain.model.user.Email;
import com.renaser.os.users.domain.model.user.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** D-244: el caso de uso del botón de emergencia y su atención. */
@ExtendWith(MockitoExtension.class)
class EmergenciaServiceTest {

    private static final ZoneId LIMA = ZoneId.of("America/Lima");
    /** 15:00 UTC = 10:00 en Lima: el mismo día calendario en las dos zonas. */
    private static final FixedClock MEDIODIA = FixedClock.at(Instant.parse("2026-10-02T15:00:00Z"));
    /** 03:00 UTC del 2 = 22:00 del 1 en Lima: en su zona todavía es AYER (regla 02). */
    private static final FixedClock MADRUGADA_UTC = FixedClock.at(Instant.parse("2026-10-02T03:00:00Z"));
    /** Empezó el 13/09 en Lima: el 02/10 vive su día 20; el 01/10, su día 19. */
    private static final LocalDate INICIO = LocalDate.of(2026, 9, 13);

    @Mock
    private LoadUserPort loadUserPort;
    @Mock
    private LoadParticipacionProgramaPort loadParticipacionPort;
    @Mock
    private LoadSolicitudDeEmergenciaPort loadSolicitudPort;
    @Mock
    private SaveSolicitudDeEmergenciaPort saveSolicitudPort;
    @Mock
    private ApplicationEventPublisher eventos;

    private final UserId aprendiz = UserId.of(UUID.randomUUID());
    private final UserId admin = UserId.of(UUID.randomUUID());

    private EmergenciaService servicio(FixedClock reloj) {
        return new EmergenciaService(new RequireActiveUserGuard(loadUserPort), new RequireAdminGuard(loadUserPort),
                loadUserPort, loadParticipacionPort, loadSolicitudPort, saveSolicitudPort, eventos, UUID::randomUUID,
                reloj);
    }

    private void existe(UserId id, UserRole rol, UserStatus estado) {
        when(loadUserPort.byId(id)).thenReturn(Optional.of(User.rehydrate(id, new Email(id + "@renaser.com"), rol,
                estado, "Ana Pérez", null, null, null, null)));
    }

    private void enCurso() {
        when(loadParticipacionPort.byParticipanteId(aprendiz)).thenReturn(Optional.of(ParticipacionPrograma.rehydrate(
                aprendiz, null, null, 19, FasePrograma.paraDiaPrograma(19), INICIO, Instant.parse("2026-09-10T15:00:00Z"),
                LIMA, false, 0, Instant.parse("2026-09-10T15:00:00Z"), Instant.parse("2026-10-01T05:05:00Z"), null, null,
                null, LocalDate.of(2026, 10, 1), 0)));
    }

    @Test
    @DisplayName("pedir guarda la solicitud abierta y publica el evento con el día que vive en su zona")
    void pedirGuardaYAvisa() {
        existe(aprendiz, UserRole.TRAINEE, UserStatus.ACTIVE);
        enCurso();
        when(loadSolicitudPort.abiertaDe(aprendiz)).thenReturn(Optional.empty());
        when(saveSolicitudPort.save(any())).thenAnswer(inv -> inv.getArgument(0));

        SolicitudDeEmergencia s = servicio(MEDIODIA).pedir(new PedirAyudaCommand(aprendiz, "Me caí de la moto", 12));

        assertThat(s.diaAlPedir()).isEqualTo(20);
        assertThat(s.estado()).isEqualTo(EstadoDeEmergencia.ABIERTA);
        ArgumentCaptor<EmergenciaPedidaEvent> evento = ArgumentCaptor.forClass(EmergenciaPedidaEvent.class);
        verify(eventos).publishEvent(evento.capture());
        assertThat(evento.getValue()).isEqualTo(new EmergenciaPedidaEvent(s.id(), aprendiz, "Me caí de la moto", 12, 20));
    }

    /**
     * Regla 02: a las 03:00 UTC del 2, en Lima todavía es el 1 y vive el día 19. Con la fecha del servidor
     * (el 2) el día actual daría 20 y aceptaría pedir el 20, un día que todavía no vivió.
     */
    @Test
    @DisplayName("en la madrugada UTC el día actual es el de SU zona: el 20 todavía no se puede pedir")
    void madrugadaUtcUsaElDiaDeSuZona() {
        existe(aprendiz, UserRole.TRAINEE, UserStatus.ACTIVE);
        enCurso();

        var mia = servicio(MADRUGADA_UTC).consultar(aprendiz);
        assertThat(mia.diaActual()).isEqualTo(19);
        assertThat(mia.diaMaximo()).isEqualTo(19);

        when(loadSolicitudPort.abiertaDe(aprendiz)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> servicio(MADRUGADA_UTC).pedir(new PedirAyudaCommand(aprendiz, "Accidente", 20)))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("Elige un día entre 1 y 19.");
        verify(saveSolicitudPort, never()).save(any());
    }

    @Test
    @DisplayName("con un pedido abierto, el segundo es 409 y no guarda ni avisa (una abierta por persona)")
    void unaAbiertaPorPersona() {
        existe(aprendiz, UserRole.TRAINEE, UserStatus.ACTIVE);
        enCurso();
        when(loadSolicitudPort.abiertaDe(aprendiz)).thenReturn(Optional.of(
                SolicitudDeEmergencia.pedir(UUID.randomUUID(), aprendiz, "Antes", 10, 18, MEDIODIA)));

        assertThatThrownBy(() -> servicio(MEDIODIA).pedir(new PedirAyudaCommand(aprendiz, "Otra vez", 12)))
                .isInstanceOf(IllegalStateException.class).hasMessage(EmergenciaService.YA_TIENE_UNA_ABIERTA);
        verify(saveSolicitudPort, never()).save(any());
        verifyNoInteractions(eventos);
    }

    @Test
    @DisplayName("un MENTOR (que el interceptor deja pasar) y una cuenta suspendida reciben 403")
    void soloAprendicesActivos() {
        existe(admin, UserRole.MENTOR, UserStatus.ACTIVE);
        existe(aprendiz, UserRole.TRAINEE, UserStatus.SUSPENDED);

        assertThatThrownBy(() -> servicio(MEDIODIA).pedir(new PedirAyudaCommand(admin, "x", 1)))
                .isInstanceOf(NotAuthorizedException.class);
        assertThatThrownBy(() -> servicio(MEDIODIA).pedir(new PedirAyudaCommand(aprendiz, "x", 1)))
                .isInstanceOf(NotAuthorizedException.class);
        verifyNoInteractions(saveSolicitudPort, eventos);
    }

    @Test
    @DisplayName("en el Día 0 (o sin fila de programa) pide ayuda sin día y avisa igual (respuesta del dueño, 02/10)")
    void enElDiaCeroPideAyudaSinDia() {
        existe(aprendiz, UserRole.TRAINEE, UserStatus.ACTIVE);
        when(loadParticipacionPort.byParticipanteId(aprendiz)).thenReturn(Optional.empty());
        when(loadSolicitudPort.abiertaDe(aprendiz)).thenReturn(Optional.empty());
        when(saveSolicitudPort.save(any())).thenAnswer(inv -> inv.getArgument(0));

        assertThat(servicio(MEDIODIA).consultar(aprendiz).diaMaximo()).isZero();
        SolicitudDeEmergencia s = servicio(MEDIODIA).pedir(new PedirAyudaCommand(aprendiz, "Me enfermé", null));

        assertThat(s.diaPedido()).isNull();
        assertThat(s.diaAlPedir()).isZero();
        verify(eventos).publishEvent(new EmergenciaPedidaEvent(s.id(), aprendiz, "Me enfermé", null, 0));
        assertThatThrownBy(() -> servicio(MEDIODIA).pedir(new PedirAyudaCommand(aprendiz, "x", 1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("quien atiende ve el pedido abierto con el nombre y el día que vive hoy")
    void soporteVeElPedido() {
        existe(admin, UserRole.ADMIN, UserStatus.ACTIVE);
        existe(aprendiz, UserRole.TRAINEE, UserStatus.ACTIVE);
        enCurso();
        var abierta = SolicitudDeEmergencia.pedir(UUID.randomUUID(), aprendiz, "Accidente", 12, 18, MEDIODIA);
        when(loadSolicitudPort.abiertaDe(aprendiz)).thenReturn(Optional.of(abierta));

        var vista = servicio(MEDIODIA).abiertaDe(admin, aprendiz).orElseThrow();

        assertThat(vista.solicitud()).isEqualTo(abierta);
        assertThat(vista.nombre()).isEqualTo("Ana Pérez");
        assertThat(vista.diaActual()).isEqualTo(20);
    }

    @Test
    @DisplayName("un MENTOR no ve ni cierra pedidos (403); el pedido inexistente es 404 antes del guard")
    void soporteSoloAdmin() {
        existe(admin, UserRole.MENTOR, UserStatus.ACTIVE);
        assertThatThrownBy(() -> servicio(MEDIODIA).abiertaDe(admin, aprendiz)).isInstanceOf(NotAuthorizedException.class);

        UUID id = UUID.randomUUID();
        when(loadSolicitudPort.porId(id)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> servicio(MEDIODIA).cerrarSinCambio(admin, id))
                .isInstanceOf(java.util.NoSuchElementException.class);

        var abierta = SolicitudDeEmergencia.pedir(id, aprendiz, "Accidente", 12, 18, MEDIODIA);
        when(loadSolicitudPort.porId(id)).thenReturn(Optional.of(abierta));
        assertThatThrownBy(() -> servicio(MEDIODIA).cerrarSinCambio(admin, id)).isInstanceOf(NotAuthorizedException.class);
        assertThat(abierta.abierta()).isTrue();
    }

    @Test
    @DisplayName("cerrar sin cambio la deja resuelta sin día aplicado y avisa con el día que vive hoy")
    void cerrarSinCambio() {
        existe(admin, UserRole.ALCHEMIST, UserStatus.ACTIVE);
        enCurso();
        UUID id = UUID.randomUUID();
        when(loadSolicitudPort.porId(id)).thenReturn(Optional.of(
                SolicitudDeEmergencia.pedir(id, aprendiz, "Accidente", 12, 18, MEDIODIA)));
        when(saveSolicitudPort.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var cerrada = servicio(MEDIODIA).cerrarSinCambio(admin, id);

        assertThat(cerrada.estado()).isEqualTo(EstadoDeEmergencia.RESUELTA);
        assertThat(cerrada.resueltaPor()).isEqualTo(admin);
        assertThat(cerrada.diaAplicado()).isNull();
        verify(eventos).publishEvent(new EmergenciaResueltaEvent(id, aprendiz, null, 20));
    }

    @Test
    @DisplayName("al cambiar el día, el pedido abierto queda resuelto con ese día; sin pedido no hace nada")
    void alCambiarDia() {
        var abierta = SolicitudDeEmergencia.pedir(UUID.randomUUID(), aprendiz, "Accidente", 12, 18, MEDIODIA);
        when(loadSolicitudPort.abiertaDe(aprendiz)).thenReturn(Optional.of(abierta));

        servicio(MEDIODIA).alCambiarDia(aprendiz, admin, 12);

        verify(saveSolicitudPort).save(abierta);
        assertThat(abierta.diaAplicado()).isEqualTo(12);
        assertThat(abierta.resueltaPor()).isEqualTo(admin);
        // Pedido del dueño (02/10): al aplicar el cambio, el programa le escribe a la persona.
        verify(eventos).publishEvent(new EmergenciaResueltaEvent(abierta.id(), aprendiz, 12, 12));

        UserId otro = UserId.of(UUID.randomUUID());
        when(loadSolicitudPort.abiertaDe(otro)).thenReturn(Optional.empty());
        servicio(MEDIODIA).alCambiarDia(otro, admin, 5);
        verify(saveSolicitudPort).save(any());
    }
}

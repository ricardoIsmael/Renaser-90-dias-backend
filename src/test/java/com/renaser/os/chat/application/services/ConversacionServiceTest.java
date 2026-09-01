package com.renaser.os.chat.application.services;

import com.renaser.os.chat.application.ports.in.conversacion.CrearConversacionDirectaUseCase.CrearConversacionDirectaCommand;
import com.renaser.os.chat.application.ports.in.conversacion.ListarConversacionesUseCase.ConversacionResumen;
import com.renaser.os.chat.application.ports.in.conversacion.MarcarLeidoUseCase.MarcarLeidoCommand;
import com.renaser.os.chat.application.ports.in.conversacion.RenombrarConversacionGlobalUseCase.RenombrarConversacionGlobalCommand;
import com.renaser.os.chat.application.ports.out.conversacion.LoadConversacionPort;
import com.renaser.os.chat.application.ports.out.conversacion.SaveConversacionPort;
import com.renaser.os.chat.application.ports.out.mensaje.LoadMensajePort;
import com.renaser.os.chat.application.ports.out.participante.AgregarParticipantePort;
import com.renaser.os.chat.application.ports.out.participante.ContarNoLeidosPort;
import com.renaser.os.chat.application.ports.out.participante.EsParticipantePort;
import com.renaser.os.chat.application.ports.out.participante.MarcarLeidoPort;
import com.renaser.os.chat.domain.model.conversacion.Conversacion;
import com.renaser.os.chat.domain.model.conversacion.ConversacionId;
import com.renaser.os.shared.domain.FixedClock;
import com.renaser.os.shared.domain.IdGenerator;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.UserRole;
import com.renaser.os.users.api.UserStatus;
import com.renaser.os.users.api.UserSummary;
import com.renaser.os.users.api.UserSummaryFinder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConversacionServiceTest {

    private static final FixedClock CLOCK = FixedClock.at(Instant.parse("2026-08-25T10:00:00Z"));
    /** Identidad fija: con el id entrando por el puerto IdGenerator, la factoria del agregado ya no lo sortea. */
    private static final UUID ID_GENERADO = UUID.fromString("00000000-0000-4000-8000-000000000001");

    @Mock
    private LoadConversacionPort loadConversacionPort;
    @Mock
    private SaveConversacionPort saveConversacionPort;
    @Mock
    private AgregarParticipantePort agregarParticipantePort;
    @Mock
    private EsParticipantePort esParticipantePort;
    @Mock
    private MarcarLeidoPort marcarLeidoPort;
    @Mock
    private ContarNoLeidosPort contarNoLeidosPort;
    @Mock
    private LoadMensajePort loadMensajePort;
    @Mock
    private UserSummaryFinder userSummaryFinder;
    @Mock
    private IdGenerator idGenerator;

    private ConversacionService service;

    private final UserId activo = UserId.of(UUID.randomUUID());
    private final UserId otroActivo = UserId.of(UUID.randomUUID());
    private final UserId suspendido = UserId.of(UUID.randomUUID());
    private final UserId admin = UserId.of(UUID.randomUUID());

    @BeforeEach
    void setUp() {
        service = new ConversacionService(loadConversacionPort, saveConversacionPort, agregarParticipantePort,
                esParticipantePort, marcarLeidoPort, contarNoLeidosPort, loadMensajePort, userSummaryFinder,
                CLOCK, idGenerator);
        lenient().when(idGenerator.newId()).thenReturn(ID_GENERADO);
        lenient().when(userSummaryFinder.findById(activo)).thenReturn(
                Optional.of(new UserSummary(activo, "Activo", null, UserRole.TRAINEE, UserStatus.ACTIVE)));
        lenient().when(userSummaryFinder.findById(otroActivo)).thenReturn(
                Optional.of(new UserSummary(otroActivo, "Otro", null, UserRole.TRAINEE, UserStatus.ACTIVE)));
        lenient().when(userSummaryFinder.findById(suspendido)).thenReturn(
                Optional.of(new UserSummary(suspendido, "Suspendido", null, UserRole.TRAINEE, UserStatus.SUSPENDED)));
        lenient().when(userSummaryFinder.findById(admin)).thenReturn(
                Optional.of(new UserSummary(admin, "Admin", null, UserRole.ADMIN, UserStatus.ACTIVE)));
        lenient().when(saveConversacionPort.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void unActorSuspendidoNoPuedeCrearUnaConversacionDirecta() {
        assertThatThrownBy(() -> service.obtenerOCrear(new CrearConversacionDirectaCommand(suspendido, otroActivo)))
                .isInstanceOf(NotAuthorizedException.class);

        verify(saveConversacionPort, never()).save(any());
    }

    @Test
    void noSePuedeCrearUnaConversacionDirectaConUnoMismo() {
        assertThatThrownBy(() -> service.obtenerOCrear(new CrearConversacionDirectaCommand(activo, activo)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void obtenerOCrearEsIdempotenteSiYaExisteLaClaveDirecta() {
        String clave = Conversacion.claveDirectaDe(activo, otroActivo);
        Conversacion existente = Conversacion.crearDirecta(ConversacionId.of(UUID.randomUUID()), clave,
                CLOCK.now());
        when(loadConversacionPort.porClaveDirecta(clave)).thenReturn(Optional.of(existente));

        Conversacion resultado = service.obtenerOCrear(new CrearConversacionDirectaCommand(activo, otroActivo));

        assertThat(resultado).isEqualTo(existente);
        verify(saveConversacionPort, never()).save(any());
    }

    @Test
    void obtenerOCrearAgregaAAmbosParticipantesCuandoLaConversacionEsNueva() {
        String clave = Conversacion.claveDirectaDe(activo, otroActivo);
        when(loadConversacionPort.porClaveDirecta(clave)).thenReturn(Optional.empty());

        service.obtenerOCrear(new CrearConversacionDirectaCommand(activo, otroActivo));

        verify(saveConversacionPort).save(any());
        verify(agregarParticipantePort, times(2)).agregar(any());
    }

    @Test
    void listarResuelveElUltimoMensajeYElConteoDeNoLeidosEnUnaSolaLlamadaCadaUno() {
        Conversacion c1 = Conversacion.crearGlobal(ConversacionId.of(UUID.randomUUID()), CLOCK.now());
        when(loadConversacionPort.misConversaciones(activo)).thenReturn(List.of(c1));
        when(loadMensajePort.ultimosPorConversacion(any())).thenReturn(Map.of());
        when(contarNoLeidosPort.contarNoLeidos(eq(activo), any())).thenReturn(Map.of(c1.id(), 3L));

        List<ConversacionResumen> resumenes = service.listar(activo);

        assertThat(resumenes).hasSize(1);
        assertThat(resumenes.get(0).noLeidos()).isEqualTo(3L);
        // Nunca N+1: una sola llamada al puerto de conteo en lote, no una por conversacion.
        verify(contarNoLeidosPort, times(1)).contarNoLeidos(any(), any());
        verify(loadMensajePort, times(1)).ultimosPorConversacion(any());
    }

    @Test
    void unActorSuspendidoNoPuedeListarSusConversaciones() {
        assertThatThrownBy(() -> service.listar(suspendido)).isInstanceOf(NotAuthorizedException.class);
    }

    @Test
    void marcarLeidoRechazaAQuienNoEsParticipante() {
        ConversacionId conversacionId = ConversacionId.of(UUID.randomUUID());
        when(loadConversacionPort.porId(conversacionId))
                .thenReturn(Optional.of(Conversacion.crearGlobal(conversacionId, CLOCK.now())));
        when(esParticipantePort.esParticipante(conversacionId, activo)).thenReturn(false);

        assertThatThrownBy(() -> service.marcarLeido(new MarcarLeidoCommand(activo, conversacionId)))
                .isInstanceOf(NotAuthorizedException.class);

        verify(marcarLeidoPort, never()).marcarLeido(any(), any(), any());
    }

    @Test
    void marcarLeidoFuncionaParaUnParticipante() {
        ConversacionId conversacionId = ConversacionId.of(UUID.randomUUID());
        when(loadConversacionPort.porId(conversacionId))
                .thenReturn(Optional.of(Conversacion.crearGlobal(conversacionId, CLOCK.now())));
        when(esParticipantePort.esParticipante(conversacionId, activo)).thenReturn(true);

        service.marcarLeido(new MarcarLeidoCommand(activo, conversacionId));

        verify(marcarLeidoPort).marcarLeido(conversacionId, activo, CLOCK.now());
    }

    @Test
    void unirseCreaLaGlobalSiTodaviaNoExisteYAgregaAlUsuario() {
        when(loadConversacionPort.global()).thenReturn(Optional.empty());
        when(esParticipantePort.esParticipante(any(), eq(activo))).thenReturn(false);

        service.unirse(activo);

        verify(saveConversacionPort).save(any());
        verify(agregarParticipantePort).agregar(any());
    }

    @Test
    void unirseEsIdempotenteSiYaEsParticipante() {
        Conversacion global = Conversacion.crearGlobal(ConversacionId.of(UUID.randomUUID()), CLOCK.now());
        when(loadConversacionPort.global()).thenReturn(Optional.of(global));
        when(esParticipantePort.esParticipante(global.id(), activo)).thenReturn(true);

        service.unirse(activo);

        verify(agregarParticipantePort, never()).agregar(any());
    }

    @Test
    void crearParaCelulaEsIdempotenteSiYaExisteLaConversacion() {
        UUID celulaId = UUID.randomUUID();
        when(loadConversacionPort.porCelulaId(celulaId))
                .thenReturn(Optional.of(Conversacion.crearCelula(ConversacionId.of(UUID.randomUUID()), celulaId,
                        CLOCK.now())));

        service.crearParaCelula(celulaId);

        verify(saveConversacionPort, never()).save(any());
    }

    @Test
    void crearParaCelulaCreaLaConversacionSiNoExiste() {
        UUID celulaId = UUID.randomUUID();
        when(loadConversacionPort.porCelulaId(celulaId)).thenReturn(Optional.empty());

        service.crearParaCelula(celulaId);

        verify(saveConversacionPort).save(any());
    }

    @Test
    void renombrarFuncionaParaUnAdmin() {
        Conversacion global = Conversacion.crearGlobal(ConversacionId.of(UUID.randomUUID()), CLOCK.now());
        when(loadConversacionPort.global()).thenReturn(Optional.of(global));

        Conversacion renombrada = service.renombrar(new RenombrarConversacionGlobalCommand(admin, "Comunidad Renaser"));

        assertThat(renombrada.nombre()).isEqualTo("Comunidad Renaser");
        verify(saveConversacionPort).save(any());
    }

    @Test
    void renombrarRechazaAQuienNoEsAdminNiAlquimista() {
        Conversacion global = Conversacion.crearGlobal(ConversacionId.of(UUID.randomUUID()), CLOCK.now());
        lenient().when(loadConversacionPort.global()).thenReturn(Optional.of(global));

        assertThatThrownBy(() -> service.renombrar(new RenombrarConversacionGlobalCommand(activo, "Otro nombre")))
                .isInstanceOf(NotAuthorizedException.class);

        verify(saveConversacionPort, never()).save(any());
    }

    @Test
    void renombrarRechazaAUnActorSuspendidoAunqueFueraAdmin() {
        assertThatThrownBy(() -> service.renombrar(new RenombrarConversacionGlobalCommand(suspendido, "Otro nombre")))
                .isInstanceOf(NotAuthorizedException.class);

        verify(saveConversacionPort, never()).save(any());
    }
}

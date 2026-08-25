package com.renaser.os.habits.application.services;

import com.renaser.os.evidence.api.DestinoEvidencia;
import com.renaser.os.evidence.api.EstadoValidacion;
import com.renaser.os.evidence.api.RegistrarEvidenciaPort;
import com.renaser.os.evidence.api.RegistrarEvidenciaPort.EvidenciaRegistrada;
import com.renaser.os.evidence.api.RegistrarEvidenciaPort.RegistrarEvidenciaComando;
import com.renaser.os.evidence.api.TipoEvidencia;
import com.renaser.os.habits.application.ports.in.registro.SubirEvidenciaRegistroUseCase.SubirEvidenciaRegistroCommand;
import com.renaser.os.habits.application.ports.out.participante.ConsultarProgresoParticipanteHabitsPort;
import com.renaser.os.habits.application.ports.out.participante.ConsultarProgresoParticipanteHabitsPort.ProgresoParticipanteHabits;
import com.renaser.os.habits.application.ports.out.participante.ConsultarProgresoParticipanteHabitsPort.RolParticipante;
import com.renaser.os.habits.application.ports.out.registro.LoadRegistroHabitoPort;
import com.renaser.os.habits.domain.model.habito.HabitoId;
import com.renaser.os.habits.domain.model.habito.TipoDia;
import com.renaser.os.habits.domain.model.registro.RegistroHabito;
import com.renaser.os.shared.domain.FixedClock;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EvidenciaRegistroServiceTest {

    private static final FixedClock CLOCK = FixedClock.at(Instant.parse("2026-08-25T10:00:00Z"));

    @Mock
    private LoadRegistroHabitoPort loadRegistroPort;
    @Mock
    private ConsultarProgresoParticipanteHabitsPort progresoPort;
    @Mock
    private RegistrarEvidenciaPort registrarEvidenciaPort;

    private EvidenciaRegistroService service;
    private UserId actorId;

    @BeforeEach
    void setUp() {
        service = new EvidenciaRegistroService(loadRegistroPort, progresoPort, registrarEvidenciaPort, CLOCK);
        actorId = UserId.of(UUID.randomUUID());
        lenient().when(registrarEvidenciaPort.registrar(any()))
                .thenReturn(new EvidenciaRegistrada(UUID.randomUUID(), EstadoValidacion.PENDIENTE));
    }

    private RegistroHabito registroDe(UserId participanteId) {
        return RegistroHabito.generar(participanteId, HabitoId.newId(), LocalDate.of(2026, 8, 25), 5,
                TipoDia.DISCIPLINA, false, CLOCK.now());
    }

    private static ProgresoParticipanteHabits progreso(boolean suspendido) {
        return new ProgresoParticipanteHabits(5, "America/Lima", RolParticipante.TRAINEE, suspendido);
    }

    private SubirEvidenciaRegistroCommand comandoTexto(UserId actor, RegistroHabito registro) {
        return new SubirEvidenciaRegistroCommand(actor, registro.id(), TipoEvidencia.TEXTO, null, null, "listo",
                null, null, null);
    }

    @Test
    @DisplayName("CLAUDE.MD §0.3: actor distinto del dueno del registro -> NotAuthorizedException")
    void actorDistintoRechazado() {
        RegistroHabito registro = registroDe(UserId.of(UUID.randomUUID()));
        when(loadRegistroPort.byId(registro.id())).thenReturn(Optional.of(registro));

        assertThatThrownBy(() -> service.subir(comandoTexto(actorId, registro)))
                .isInstanceOf(NotAuthorizedException.class);
    }

    @Test
    @DisplayName("CLAUDE.MD §0.3: actor SUSPENDIDO -> NotAuthorizedException")
    void actorSuspendidoRechazado() {
        RegistroHabito registro = registroDe(actorId);
        when(loadRegistroPort.byId(registro.id())).thenReturn(Optional.of(registro));
        when(progresoPort.deParticipante(actorId)).thenReturn(Optional.of(progreso(true)));

        assertThatThrownBy(() -> service.subir(comandoTexto(actorId, registro)))
                .isInstanceOf(NotAuthorizedException.class);
    }

    @Test
    void registroInexistenteLanzaNoSuchElement() {
        var registroId = com.renaser.os.habits.domain.model.registro.RegistroHabitoId.newId();
        when(loadRegistroPort.byId(registroId)).thenReturn(Optional.empty());

        var command = new SubirEvidenciaRegistroCommand(actorId, registroId, TipoEvidencia.TEXTO, null, null,
                "listo", null, null, null);

        assertThatThrownBy(() -> service.subir(command)).isInstanceOf(NoSuchElementException.class);
    }

    @Test
    @DisplayName("delega en RegistrarEvidenciaPort con el registro como destino, esPrincipal=false")
    void delegaEnEvidencePortConDestinoCorrecto() {
        RegistroHabito registro = registroDe(actorId);
        when(loadRegistroPort.byId(registro.id())).thenReturn(Optional.of(registro));
        when(progresoPort.deParticipante(actorId)).thenReturn(Optional.of(progreso(false)));

        var resultado = service.subir(comandoTexto(actorId, registro));

        assertThat(resultado.estadoValidacion()).isEqualTo(EstadoValidacion.PENDIENTE);
        ArgumentCaptor<RegistrarEvidenciaComando> captor = ArgumentCaptor.forClass(RegistrarEvidenciaComando.class);
        verify(registrarEvidenciaPort).registrar(captor.capture());
        RegistrarEvidenciaComando comando = captor.getValue();
        assertThat(comando.participanteId()).isEqualTo(actorId);
        assertThat(comando.destino()).isInstanceOf(DestinoEvidencia.RegistroHabito.class);
        assertThat(((DestinoEvidencia.RegistroHabito) comando.destino()).registroHabitoId())
                .isEqualTo(registro.id().value());
        assertThat(comando.esPrincipal()).isFalse();
        assertThat(comando.tipo()).isEqualTo(TipoEvidencia.TEXTO);
        assertThat(comando.contenidoTexto()).isEqualTo("listo");
    }
}

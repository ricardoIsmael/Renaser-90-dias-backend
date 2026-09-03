package com.renaser.os.phasecontracts.application.services;

import com.renaser.os.phasecontracts.application.ports.in.contrato.ConsultarContratosPendientesUseCase.ContratoPendiente;
import com.renaser.os.phasecontracts.application.ports.in.contrato.ConsultarContratosUseCase.ContratoConUrlLectura;
import com.renaser.os.phasecontracts.application.ports.in.contrato.FirmarContratoUseCase.FirmarContratoCommand;
import com.renaser.os.phasecontracts.application.ports.in.contrato.ObtenerUrlFirmaContratoUseCase.ObtenerUrlFirmaContratoCommand;
import com.renaser.os.phasecontracts.application.ports.out.contrato.ConsultarProgresoParticipantePort;
import com.renaser.os.phasecontracts.application.ports.out.contrato.ConsultarProgresoParticipantePort.ProgresoParticipante;
import com.renaser.os.phasecontracts.application.ports.out.contrato.ConsultarProgresoParticipantePort.RolParticipante;
import com.renaser.os.phasecontracts.application.ports.out.contrato.LoadContratoPort;
import com.renaser.os.phasecontracts.application.ports.out.contrato.SaveContratoPort;
import com.renaser.os.phasecontracts.domain.model.contrato.ContratoFase;
import com.renaser.os.phasecontracts.domain.model.contrato.ContratoFaseId;
import com.renaser.os.phasecontracts.domain.model.contrato.FasePrograma;
import com.renaser.os.shared.application.ports.out.AlmacenamientoPort;
import com.renaser.os.shared.domain.FixedClock;
import com.renaser.os.shared.domain.IdGenerator;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ContratoServiceTest {

    private static final FixedClock CLOCK = FixedClock.at(Instant.parse("2026-08-24T10:00:00Z"));
    /** Identidad fija: con el id entrando por el puerto IdGenerator, firmar() ya no sortea el ContratoFaseId. */
    private static final UUID ID_GENERADO = UUID.fromString("00000000-0000-4000-8000-000000000001");

    @Mock
    private LoadContratoPort loadContratoPort;
    @Mock
    private SaveContratoPort saveContratoPort;
    @Mock
    private ConsultarProgresoParticipantePort progresoPort;
    @Mock
    private AlmacenamientoPort almacenamientoPort;
    @Mock
    private IdGenerator idGenerator;

    private ContratoService service;
    private UserId participanteId;

    @BeforeEach
    void setUp() {
        service = new ContratoService(loadContratoPort, saveContratoPort, progresoPort, almacenamientoPort,
                CLOCK, idGenerator);
        participanteId = UserId.of(UUID.randomUUID());
        lenient().when(idGenerator.newId()).thenReturn(ID_GENERADO);
    }

    private void progreso(int diaPrograma, RolParticipante rol, boolean suspendido) {
        when(progresoPort.deParticipante(participanteId))
                .thenReturn(Optional.of(new ProgresoParticipante(diaPrograma, rol, suspendido)));
    }

    // ── firmar ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("firmar(): TRAINEE en dia desbloqueado, sin firma previa -> crea y guarda")
    void firmarCreaYGuardaCuandoNoHayFirmaPrevia() {
        progreso(20, RolParticipante.TRAINEE, false);
        when(loadContratoPort.porParticipanteYFase(participanteId, FasePrograma.FASE_2_DESARROLLO))
                .thenReturn(Optional.empty());
        when(saveContratoPort.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ContratoFase resultado = service.firmar(new FirmarContratoCommand(participanteId));

        assertThat(resultado.fase()).isEqualTo(FasePrograma.FASE_2_DESARROLLO);
        verify(saveContratoPort).save(any());
    }

    @Test
    @DisplayName("firmar(): idempotente -- si ya existe, devuelve el original y NUNCA sobreescribe")
    void firmarEsIdempotente() {
        progreso(20, RolParticipante.TRAINEE, false);
        ContratoFase existente = ContratoFase.firmar(ContratoFaseId.of(UUID.randomUUID()), participanteId, 20, CLOCK);
        when(loadContratoPort.porParticipanteYFase(participanteId, FasePrograma.FASE_2_DESARROLLO))
                .thenReturn(Optional.of(existente));

        ContratoFase resultado = service.firmar(new FirmarContratoCommand(participanteId));

        assertThat(resultado).isEqualTo(existente);
        verify(saveContratoPort, never()).save(any());
    }

    @Test
    @DisplayName("firmar(): rol sin permiso (MENTOR) -> NotAuthorizedException, nunca guarda")
    void firmarConRolSinPermiso() {
        progreso(20, RolParticipante.MENTOR, false);

        assertThatThrownBy(() -> service.firmar(new FirmarContratoCommand(participanteId)))
                .isInstanceOf(NotAuthorizedException.class);
        verify(saveContratoPort, never()).save(any());
    }

    @Test
    @DisplayName("firmar(): cuenta SUSPENDIDO -> NotAuthorizedException aunque el rol sea TRAINEE")
    void firmarConCuentaSuspendida() {
        progreso(20, RolParticipante.TRAINEE, true);

        assertThatThrownBy(() -> service.firmar(new FirmarContratoCommand(participanteId)))
                .isInstanceOf(NotAuthorizedException.class);
        verify(saveContratoPort, never()).save(any());
    }

    @Test
    @DisplayName("firmar(): participante inexistente -> NoSuchElementException (404)")
    void firmarSinParticipante() {
        when(progresoPort.deParticipante(participanteId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.firmar(new FirmarContratoCommand(participanteId)))
                .isInstanceOf(NoSuchElementException.class);
    }

    @Test
    @DisplayName("firmar(): en Fase I -> IllegalArgumentException (400), propagada del dominio")
    void firmarEnFaseUno() {
        progreso(5, RolParticipante.TRAINEE, false);

        assertThatThrownBy(() -> service.firmar(new FirmarContratoCommand(participanteId)))
                .isInstanceOf(IllegalArgumentException.class);
        verify(saveContratoPort, never()).save(any());
    }

    // ── consultarPendiente ─────────────────────────────────────────────────

    @Test
    @DisplayName("consultarPendiente(): Fase I -> nunca pendiente (la cubre el onboarding)")
    void consultarPendienteEnFaseUno() {
        progreso(5, RolParticipante.TRAINEE, false);

        ContratoPendiente resultado = service.consultarPendiente(participanteId);

        assertThat(resultado.pendiente()).isFalse();
    }

    @Test
    @DisplayName("consultarPendiente(): desbloqueada y sin firmar -> pendiente=true con la fase")
    void consultarPendienteDesbloqueadaSinFirmar() {
        progreso(20, RolParticipante.MENTOR, false); // MENTOR SI puede consultar (no firmar)
        when(loadContratoPort.porParticipanteYFase(participanteId, FasePrograma.FASE_2_DESARROLLO))
                .thenReturn(Optional.empty());

        ContratoPendiente resultado = service.consultarPendiente(participanteId);

        assertThat(resultado.pendiente()).isTrue();
        assertThat(resultado.fase()).isEqualTo(FasePrograma.FASE_2_DESARROLLO);
        assertThat(resultado.etiqueta()).isEqualTo(FasePrograma.FASE_2_DESARROLLO.etiqueta());
    }

    @Test
    @DisplayName("consultarPendiente(): ya firmada -> pendiente=false")
    void consultarPendienteYaFirmada() {
        progreso(20, RolParticipante.TRAINEE, false);
        when(loadContratoPort.porParticipanteYFase(participanteId, FasePrograma.FASE_2_DESARROLLO))
                .thenReturn(Optional.of(ContratoFase.firmar(ContratoFaseId.of(UUID.randomUUID()),
                        participanteId, 20, CLOCK)));

        assertThat(service.consultarPendiente(participanteId).pendiente()).isFalse();
    }

    @Test
    @DisplayName("consultarPendiente(): rol sin permiso (ADMIN) -> NotAuthorizedException")
    void consultarPendienteConRolSinPermiso() {
        progreso(20, RolParticipante.ADMIN, false);

        assertThatThrownBy(() -> service.consultarPendiente(participanteId))
                .isInstanceOf(NotAuthorizedException.class);
    }

    // ── consultarDeParticipante ────────────────────────────────────────────

    @Test
    @DisplayName("consultarDeParticipante(): mapea cada contrato con su URL de lectura prefirmada")
    void consultarDeParticipanteMapeaUrlDeLectura() {
        progreso(70, RolParticipante.TRAINEE, false);
        ContratoFase c1 = ContratoFase.firmar(ContratoFaseId.of(UUID.randomUUID()), participanteId, 20, CLOCK);
        when(loadContratoPort.todosDeParticipante(participanteId)).thenReturn(List.of(c1));
        URI url = URI.create("https://s3.example/firmas/x/fase_2.svg?sig=abc");
        when(almacenamientoPort.firmarLectura(anyString(), any(Duration.class))).thenReturn(url);

        List<ContratoConUrlLectura> resultado = service.consultarDeParticipante(participanteId);

        assertThat(resultado).hasSize(1);
        assertThat(resultado.get(0).contrato()).isEqualTo(c1);
        assertThat(resultado.get(0).urlLectura()).isEqualTo(url);
    }

    @Test
    @DisplayName("consultarDeParticipante(): rol sin permiso -> NotAuthorizedException")
    void consultarDeParticipanteConRolSinPermiso() {
        progreso(70, RolParticipante.ALCHEMIST, false);

        assertThatThrownBy(() -> service.consultarDeParticipante(participanteId))
                .isInstanceOf(NotAuthorizedException.class);
    }

    // ── obtenerUrlSubida ───────────────────────────────────────────────────

    @Test
    @DisplayName("obtenerUrlSubida(): devuelve la URL prefirmada de PUT para la ruta deterministica")
    void obtenerUrlSubidaDevuelveUrlPrefirmada() {
        progreso(20, RolParticipante.TRAINEE, false);
        URI url = URI.create("https://s3.example/firmas/x/fase_2.svg?sig=upload");
        when(almacenamientoPort.firmarSubida(anyString(), anyString(), any(Duration.class))).thenReturn(url);

        var resultado = service.obtenerUrlSubida(new ObtenerUrlFirmaContratoCommand(participanteId));

        assertThat(resultado.urlSubida()).isEqualTo(url);
        assertThat(resultado.bucket()).isEqualTo(ContratoFase.BUCKET_DEFAULT);
        assertThat(resultado.ruta()).isEqualTo(ContratoFase.rutaFirma(participanteId, FasePrograma.FASE_2_DESARROLLO));
    }

    @Test
    @DisplayName("obtenerUrlSubida(): todavia no desbloqueada -> IllegalArgumentException")
    void obtenerUrlSubidaAntesDeDesbloqueo() {
        progreso(10, RolParticipante.TRAINEE, false);

        assertThatThrownBy(() -> service.obtenerUrlSubida(new ObtenerUrlFirmaContratoCommand(participanteId)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("obtenerUrlSubida(): rol sin permiso -> NotAuthorizedException")
    void obtenerUrlSubidaConRolSinPermiso() {
        progreso(20, RolParticipante.MENTOR, false);

        assertThatThrownBy(() -> service.obtenerUrlSubida(new ObtenerUrlFirmaContratoCommand(participanteId)))
                .isInstanceOf(NotAuthorizedException.class);
    }

    // ── estaFirmado (api, sin chequeo de actor: uso interno entre modulos) ──

    @Test
    @DisplayName("estaFirmado(): pasa directo al puerto de lectura, sin exigir rol/estado del actor")
    void estaFirmadoConsultaElPuertoDirecto() {
        ContratoFase firmado = ContratoFase.firmar(ContratoFaseId.of(UUID.randomUUID()), participanteId, 35, CLOCK);
        when(loadContratoPort.porParticipanteYFase(participanteId, FasePrograma.FASE_3_GUERRERO_ALQUIMISTA))
                .thenReturn(Optional.of(firmado));

        assertThat(service.estaFirmado(participanteId, 3)).isTrue();
    }
}

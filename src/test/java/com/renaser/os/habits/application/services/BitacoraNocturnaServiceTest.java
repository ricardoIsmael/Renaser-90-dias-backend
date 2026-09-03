package com.renaser.os.habits.application.services;

import com.renaser.os.habits.application.ports.in.diario.EscribirBitacoraNocturnaUseCase.EscribirBitacoraNocturnaCommand;
import com.renaser.os.habits.application.ports.out.diario.LoadEntradaDiarioPort;
import com.renaser.os.habits.application.ports.out.diario.SaveEntradaDiarioPort;
import com.renaser.os.habits.application.ports.out.participante.ConsultarProgresoParticipanteHabitsPort;
import com.renaser.os.habits.application.ports.out.participante.ConsultarProgresoParticipanteHabitsPort.ProgresoParticipanteHabits;
import com.renaser.os.habits.application.ports.out.participante.ConsultarProgresoParticipanteHabitsPort.RolParticipante;
import com.renaser.os.habits.domain.model.diario.EntradaDiario;
import com.renaser.os.habits.domain.model.diario.EntradaDiarioId;
import com.renaser.os.habits.domain.model.diario.TipoEntradaDiario;
import com.renaser.os.shared.domain.FixedClock;
import com.renaser.os.shared.domain.IdGenerator;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BitacoraNocturnaServiceTest {

    private static final FixedClock CLOCK = FixedClock.at(Instant.parse("2026-08-26T21:00:00Z"));
    /** Identidad fija: con el id entrando por el puerto IdGenerator, escribir() ya no lo sortea. */
    private static final UUID ID_GENERADO = UUID.fromString("00000000-0000-4000-8000-000000000001");

    @Mock
    private ConsultarProgresoParticipanteHabitsPort progresoPort;
    @Mock
    private LoadEntradaDiarioPort loadPort;
    @Mock
    private SaveEntradaDiarioPort savePort;
    @Mock
    private IdGenerator idGenerator;

    private BitacoraNocturnaService service;

    @BeforeEach
    void setUp() {
        service = new BitacoraNocturnaService(progresoPort, loadPort, savePort, CLOCK, idGenerator);
        lenient().when(idGenerator.newId()).thenReturn(ID_GENERADO);
        lenient().when(savePort.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void suspendidoRechazado() {
        UserId actor = UserId.of(UUID.randomUUID());
        when(progresoPort.deParticipante(actor)).thenReturn(
                Optional.of(new ProgresoParticipanteHabits(10, "UTC", RolParticipante.TRAINEE, true)));

        assertThatThrownBy(() -> service.escribir(new EscribirBitacoraNocturnaCommand(actor, "hoy fue un buen dia",
                null, null))).isInstanceOf(NotAuthorizedException.class);
    }

    @Test
    void sinTextoNiAudioRechazadoEnElComando() {
        UserId actor = UserId.of(UUID.randomUUID());

        assertThatThrownBy(() -> new EscribirBitacoraNocturnaCommand(actor, null, null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void escribePorPrimeraVezCreaLaEntrada() {
        UserId actor = UserId.of(UUID.randomUUID());
        when(progresoPort.deParticipante(actor)).thenReturn(
                Optional.of(new ProgresoParticipanteHabits(10, "UTC", RolParticipante.TRAINEE, false)));
        when(loadPort.porParticipanteFechaYTipo(actor, LocalDate.of(2026, 8, 26), TipoEntradaDiario.BITACORA_NOCTURNA))
                .thenReturn(Optional.empty());

        EntradaDiario entrada = service.escribir(new EscribirBitacoraNocturnaCommand(actor, "hoy fue un buen dia",
                null, null));

        assertThat(entrada.contenidoTexto()).isEqualTo("hoy fue un buen dia");
        assertThat(entrada.tipo()).isEqualTo(TipoEntradaDiario.BITACORA_NOCTURNA);
    }

    @Test
    void escribirDeNuevoElMismoDiaPisaElContenidoAnterior() {
        UserId actor = UserId.of(UUID.randomUUID());
        when(progresoPort.deParticipante(actor)).thenReturn(
                Optional.of(new ProgresoParticipanteHabits(10, "UTC", RolParticipante.TRAINEE, false)));
        EntradaDiario existente = EntradaDiario.escribir(EntradaDiarioId.of(UUID.randomUUID()), actor,
                LocalDate.of(2026, 8, 26), TipoEntradaDiario.BITACORA_NOCTURNA, "primer intento", CLOCK.now());
        when(loadPort.porParticipanteFechaYTipo(actor, LocalDate.of(2026, 8, 26), TipoEntradaDiario.BITACORA_NOCTURNA))
                .thenReturn(Optional.of(existente));

        EntradaDiario resultado = service.escribir(new EscribirBitacoraNocturnaCommand(actor, "version final", null,
                null));

        assertThat(resultado.contenidoTexto()).isEqualTo("version final");
    }
}

package com.renaser.os.habits.application.services;

import com.renaser.os.habits.application.ports.in.espiritu.CompletarPastillaRenacerUseCase;
import com.renaser.os.habits.application.ports.in.espiritu.ConsultarEstadoEspirituUseCase.EstadoEspiritu;
import com.renaser.os.habits.application.ports.in.espiritu.EntregarResumenEspirituUseCase.EntregarResumenEspirituCommand;
import com.renaser.os.habits.application.ports.out.espiritu.AudioCatalogPort;
import com.renaser.os.habits.application.ports.out.espiritu.AudioCatalogPort.AudioEspiritu;
import com.renaser.os.habits.application.ports.out.espiritu.LoadRegistroEspirituPort;
import com.renaser.os.habits.application.ports.out.espiritu.SaveRegistroEspirituPort;
import com.renaser.os.habits.application.ports.out.participante.ConsultarProgresoParticipanteHabitsPort;
import com.renaser.os.habits.application.ports.out.participante.ConsultarProgresoParticipanteHabitsPort.ProgresoParticipanteHabits;
import com.renaser.os.habits.application.ports.out.participante.ConsultarProgresoParticipanteHabitsPort.RolParticipante;
import com.renaser.os.habits.domain.model.espiritu.RegistroEspiritu;
import com.renaser.os.habits.domain.model.espiritu.RegistroEspirituId;
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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.PlatformTransactionManager;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EspirituServiceTest {

    /** 09:00 UTC — despues de las 07:00 de desbloqueo. */
    private static final FixedClock CLOCK = FixedClock.at(Instant.parse("2026-08-24T09:00:00Z"));
    /** Identidad fija: con el id entrando por el puerto IdGenerator, desbloquear() ya no lo sortea. */
    private static final UUID ID_GENERADO = UUID.fromString("00000000-0000-4000-8000-000000000001");
    /** Ruta de objeto en el bucket: lo que V25 permite guardar y lo que se firma para reproducir. */
    private static final String RUTA_AUDIO = "espiritu/dia-1.mp3";
    private static final URI URL_FIRMADA = URI.create("https://bucket.example/espiritu/dia-1.mp3?firma=abc");

    @Mock
    private LoadRegistroEspirituPort loadPort;
    @Mock
    private SaveRegistroEspirituPort savePort;
    @Mock
    private AudioCatalogPort audioCatalogPort;
    @Mock
    private ConsultarProgresoParticipanteHabitsPort progresoPort;
    @Mock
    private AlmacenamientoPort almacenamientoPort;
    @Mock
    private CompletarPastillaRenacerUseCase completarPastillaRenacer;
    @Mock
    private IdGenerator idGenerator;
    /** No necesita stubbing: TransactionTemplate.execute con getTransaction()==null solo
     * corre el callback directo, mismo criterio que PromocionCambioHorarioServiceTest. */
    @Mock
    private PlatformTransactionManager transactionManager;

    private EspirituService service;

    @BeforeEach
    void setUp() {
        service = new EspirituService(loadPort, savePort, audioCatalogPort, progresoPort, almacenamientoPort,
                completarPastillaRenacer, CLOCK, idGenerator, transactionManager);
        lenient().when(idGenerator.newId()).thenReturn(ID_GENERADO);
        lenient().when(savePort.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    private static UserId trainee() {
        return UserId.of(UUID.randomUUID());
    }

    @Test
    void consultarRechazaSuspendido() {
        UserId actor = trainee();
        when(progresoPort.deParticipante(actor)).thenReturn(
                Optional.of(new ProgresoParticipanteHabits(10, "UTC", RolParticipante.TRAINEE, true)));

        assertThatThrownBy(() -> service.consultar(actor)).isInstanceOf(NotAuthorizedException.class);
    }

    @Test
    void consultarRechazaRolDistintoDeTrainee() {
        UserId actor = trainee();
        when(progresoPort.deParticipante(actor)).thenReturn(
                Optional.of(new ProgresoParticipanteHabits(10, "UTC", RolParticipante.MENTOR, false)));

        assertThatThrownBy(() -> service.consultar(actor)).isInstanceOf(NotAuthorizedException.class);
    }

    @Test
    void entregarRechazaSuspendido() {
        UserId actor = trainee();
        when(progresoPort.deParticipante(actor)).thenReturn(
                Optional.of(new ProgresoParticipanteHabits(10, "UTC", RolParticipante.TRAINEE, true)));

        assertThatThrownBy(() -> service.entregar(new EntregarResumenEspirituCommand(actor, 1, "resumen")))
                .isInstanceOf(NotAuthorizedException.class);
    }

    @Test
    void entregarRechazaRolDistintoDeTrainee() {
        UserId actor = trainee();
        when(progresoPort.deParticipante(actor)).thenReturn(
                Optional.of(new ProgresoParticipanteHabits(10, "UTC", RolParticipante.ADMIN, false)));

        assertThatThrownBy(() -> service.entregar(new EntregarResumenEspirituCommand(actor, 1, "resumen")))
                .isInstanceOf(NotAuthorizedException.class);
    }

    @Test
    void desbloqueaElPrimerAudioCuandoNoHayTrackYElDiaDeProgramaAlcanza() {
        UserId actor = trainee();
        // diaPrograma 8 -> audioDay 1 (AUDIO_UNLOCK_START_DAY = 7)
        when(progresoPort.deParticipante(actor)).thenReturn(
                Optional.of(new ProgresoParticipanteHabits(8, "UTC", RolParticipante.TRAINEE, false)));
        when(loadPort.ultimoDe(actor)).thenReturn(Optional.empty());
        when(audioCatalogPort.porDia(1)).thenReturn(
                Optional.of(new AudioEspiritu(1, "Dia 1", "drive-1", "audio/mpeg", 1000, RUTA_AUDIO)));
        when(loadPort.todosDe(actor)).thenReturn(List.of());
        when(audioCatalogPort.todos()).thenReturn(List.of());

        service.consultar(actor);

        verify(savePort).save(any(RegistroEspiritu.class));
    }

    @Test
    void noDesbloqueaNadaSiElDiaDeProgramaNoAlcanzaTodavia() {
        UserId actor = trainee();
        // diaPrograma 5 -> audioDay -2, todavia no arranca Espiritu
        when(progresoPort.deParticipante(actor)).thenReturn(
                Optional.of(new ProgresoParticipanteHabits(5, "UTC", RolParticipante.TRAINEE, false)));
        when(loadPort.ultimoDe(actor)).thenReturn(Optional.empty());
        when(loadPort.todosDe(actor)).thenReturn(List.of());
        when(audioCatalogPort.todos()).thenReturn(List.of());

        service.consultar(actor);

        verify(savePort, never()).save(any());
    }

    @Test
    void entregaATiempoDevuelveOnTimeVerdadero() {
        UserId actor = trainee();
        when(progresoPort.deParticipante(actor)).thenReturn(
                Optional.of(new ProgresoParticipanteHabits(8, "UTC", RolParticipante.TRAINEE, false)));
        RegistroEspiritu registro = RegistroEspiritu.desbloquear(RegistroEspirituId.of(UUID.randomUUID()), actor, 1,
                CLOCK.now(), CLOCK.now().plusSeconds(3600), CLOCK.now());
        when(loadPort.ultimoDe(actor)).thenReturn(Optional.of(registro));
        when(loadPort.porParticipanteYDia(actor, 1)).thenReturn(Optional.of(registro));

        var resultado = service.entregar(new EntregarResumenEspirituCommand(actor, 1, "mi resumen"));

        assertThat(resultado.aTiempo()).isTrue();
        verify(savePort).save(registro);
    }

    @Test
    @DisplayName("Entregar el resumen completa ademas el habito Pastilla Renacer de hoy (espejo del repo viejo)")
    void entregarReflejaEnElHabitoPastillaRenacer() {
        UserId actor = trainee();
        when(progresoPort.deParticipante(actor)).thenReturn(
                Optional.of(new ProgresoParticipanteHabits(8, "UTC", RolParticipante.TRAINEE, false)));
        RegistroEspiritu registro = RegistroEspiritu.desbloquear(RegistroEspirituId.of(UUID.randomUUID()), actor, 1,
                CLOCK.now(), CLOCK.now().plusSeconds(3600), CLOCK.now());
        when(loadPort.ultimoDe(actor)).thenReturn(Optional.of(registro));
        when(loadPort.porParticipanteYDia(actor, 1)).thenReturn(Optional.of(registro));

        service.entregar(new EntregarResumenEspirituCommand(actor, 1, "mi resumen"));

        verify(completarPastillaRenacer).completarDeHoy(actor, "mi resumen");
    }

    @Test
    @DisplayName("El espejo es best-effort: si falla el habito ajeno, el resumen igual queda guardado")
    void unaFallaAlReflejarEnPastillaRenacerNoTumbaLaEntrega() {
        UserId actor = trainee();
        when(progresoPort.deParticipante(actor)).thenReturn(
                Optional.of(new ProgresoParticipanteHabits(8, "UTC", RolParticipante.TRAINEE, false)));
        RegistroEspiritu registro = RegistroEspiritu.desbloquear(RegistroEspirituId.of(UUID.randomUUID()), actor, 1,
                CLOCK.now(), CLOCK.now().plusSeconds(3600), CLOCK.now());
        when(loadPort.ultimoDe(actor)).thenReturn(Optional.of(registro));
        when(loadPort.porParticipanteYDia(actor, 1)).thenReturn(Optional.of(registro));
        when(completarPastillaRenacer.completarDeHoy(actor, "mi resumen"))
                .thenThrow(new IllegalStateException("el track de hoy ya expiro"));

        var resultado = service.entregar(new EntregarResumenEspirituCommand(actor, 1, "mi resumen"));

        assertThat(resultado.aTiempo()).isTrue();
        verify(savePort).save(registro);
    }

    @Test
    @DisplayName("El dia en curso trae la URL firmada del audio; los demas no la traen")
    void firmaLaUrlDelAudioSoloParaElDiaEnCurso() {
        UserId actor = trainee();
        when(progresoPort.deParticipante(actor)).thenReturn(
                Optional.of(new ProgresoParticipanteHabits(9, "UTC", RolParticipante.TRAINEE, false)));
        RegistroEspiritu enCurso = RegistroEspiritu.desbloquear(RegistroEspirituId.of(UUID.randomUUID()), actor, 1,
                CLOCK.now(), CLOCK.now().plusSeconds(3600), CLOCK.now());
        when(loadPort.ultimoDe(actor)).thenReturn(Optional.of(enCurso));
        when(loadPort.todosDe(actor)).thenReturn(List.of(enCurso));
        when(audioCatalogPort.todos()).thenReturn(List.of(
                new AudioEspiritu(1, "Dia 1", "drive-1", "audio/mpeg", 1000, RUTA_AUDIO),
                new AudioEspiritu(2, "Dia 2", "drive-2", "audio/mpeg", 2000, "espiritu/dia-2.mp3")));
        when(almacenamientoPort.firmarLectura(RUTA_AUDIO, EspirituService.TTL_AUDIO)).thenReturn(URL_FIRMADA);

        EstadoEspiritu estado = service.consultar(actor);

        var dia1 = estado.dias().stream().filter(d -> d.dia() == 1).findFirst().orElseThrow();
        var dia2 = estado.dias().stream().filter(d -> d.dia() == 2).findFirst().orElseThrow();
        assertThat(dia1.estado()).isEqualTo("CURRENT");
        assertThat(dia1.audioUrl()).isEqualTo(URL_FIRMADA.toString());
        assertThat(dia1.mimeAudio()).isEqualTo("audio/mpeg");
        assertThat(dia1.tamanoBytes()).isEqualTo(1000);
        assertThat(dia2.estado()).isEqualTo("LOCKED");
        assertThat(dia2.audioUrl()).isNull();
    }

    @Test
    @DisplayName("Sin archivo migrado al bucket (ruta_storage NULL) el dia se sirve sin audio, no rompe")
    void sinRutaDeAlmacenamientoElDiaEnCursoVaSinAudio() {
        UserId actor = trainee();
        when(progresoPort.deParticipante(actor)).thenReturn(
                Optional.of(new ProgresoParticipanteHabits(9, "UTC", RolParticipante.TRAINEE, false)));
        RegistroEspiritu enCurso = RegistroEspiritu.desbloquear(RegistroEspirituId.of(UUID.randomUUID()), actor, 1,
                CLOCK.now(), CLOCK.now().plusSeconds(3600), CLOCK.now());
        when(loadPort.ultimoDe(actor)).thenReturn(Optional.of(enCurso));
        when(loadPort.todosDe(actor)).thenReturn(List.of(enCurso));
        when(audioCatalogPort.todos()).thenReturn(
                List.of(new AudioEspiritu(1, "Dia 1", "drive-1", "audio/mpeg", 1000, null)));

        EstadoEspiritu estado = service.consultar(actor);

        assertThat(estado.dias()).hasSize(1);
        assertThat(estado.dias().getFirst().audioUrl()).isNull();
        assertThat(estado.dias().getFirst().titulo()).isEqualTo("Dia 1");
    }

    @Test
    void entregarUnDiaNoDesbloqueadoLanza() {
        UserId actor = trainee();
        when(progresoPort.deParticipante(actor)).thenReturn(
                Optional.of(new ProgresoParticipanteHabits(8, "UTC", RolParticipante.TRAINEE, false)));
        when(loadPort.ultimoDe(actor)).thenReturn(Optional.empty());
        when(loadPort.porParticipanteYDia(actor, 5)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.entregar(new EntregarResumenEspirituCommand(actor, 5, "resumen")))
                .isInstanceOf(java.util.NoSuchElementException.class);
    }

    @Test
    @DisplayName("C-10: si el INSERT choca con el UNIQUE(participante_id, dia) por una carrera, consultar() no explota")
    void consultarNoPropagaLaViolacionDeUnicidadDeUnaCreacionConcurrente() {
        UserId actor = trainee();
        when(progresoPort.deParticipante(actor)).thenReturn(
                Optional.of(new ProgresoParticipanteHabits(8, "UTC", RolParticipante.TRAINEE, false)));
        when(loadPort.ultimoDe(actor)).thenReturn(Optional.empty());
        when(audioCatalogPort.porDia(1)).thenReturn(
                Optional.of(new AudioEspiritu(1, "Dia 1", "drive-1", "audio/mpeg", 1000, RUTA_AUDIO)));
        // Simula que otra lectura concurrente ya gano la carrera y creo la fila primero.
        when(savePort.save(any())).thenThrow(new DataIntegrityViolationException("unique_violation simulado"));
        when(loadPort.todosDe(actor)).thenReturn(List.of());
        when(audioCatalogPort.todos()).thenReturn(List.of());

        assertThatCode(() -> service.consultar(actor)).doesNotThrowAnyException();
    }

    @Test
    void vistaMarcaComoLockedUnDiaDelCatalogoSinTrack() {
        UserId actor = trainee();
        when(progresoPort.deParticipante(actor)).thenReturn(
                Optional.of(new ProgresoParticipanteHabits(3, "UTC", RolParticipante.TRAINEE, false)));
        when(loadPort.ultimoDe(actor)).thenReturn(Optional.empty());
        when(loadPort.todosDe(actor)).thenReturn(List.of());
        when(audioCatalogPort.todos()).thenReturn(
                List.of(new AudioEspiritu(1, "Dia 1", "drive-1", "audio/mpeg", 1000, RUTA_AUDIO)));

        EstadoEspiritu estado = service.consultar(actor);

        assertThat(estado.dias()).hasSize(1);
        assertThat(estado.dias().get(0).estado()).isEqualTo("LOCKED");
        assertThat(estado.diaActual()).isNull();
    }
}

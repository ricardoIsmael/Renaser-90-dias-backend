package com.renaser.os.habits.application.services;

import com.renaser.os.shared.GuardDeRol;
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
import com.renaser.os.users.api.UserSummary;
import com.renaser.os.users.api.UserSummaryFinder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
import static org.mockito.Mockito.verifyNoInteractions;
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
    @Mock
    private UserSummaryFinder usuarios;

    private EspirituService service;

    @BeforeEach
    void setUp() {
        service = new EspirituService(loadPort, savePort, audioCatalogPort, progresoPort, almacenamientoPort,
                completarPastillaRenacer, new CuentasDeRevisionDeTiendas("", usuarios, CLOCK), CLOCK, idGenerator,
                transactionManager);
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
                Optional.of(new ProgresoParticipanteHabits(10, "UTC", RolParticipante.TRAINEE, true, false)));

        assertThatThrownBy(() -> service.consultar(actor)).isInstanceOf(NotAuthorizedException.class);
    }

    @Test
    void consultarRechazaRolDistintoDeTrainee() {
        UserId actor = trainee();
        when(progresoPort.deParticipante(actor)).thenReturn(
                Optional.of(new ProgresoParticipanteHabits(10, "UTC", RolParticipante.MENTOR, false, false)));

        assertThatThrownBy(() -> service.consultar(actor)).isInstanceOf(NotAuthorizedException.class);
    }

    @Test
    void entregarRechazaSuspendido() {
        UserId actor = trainee();
        when(progresoPort.deParticipante(actor)).thenReturn(
                Optional.of(new ProgresoParticipanteHabits(10, "UTC", RolParticipante.TRAINEE, true, false)));

        assertThatThrownBy(() -> service.entregar(new EntregarResumenEspirituCommand(actor, 1, "resumen")))
                .isInstanceOf(NotAuthorizedException.class);
    }

    @Test
    void entregarRechazaRolDistintoDeTrainee() {
        UserId actor = trainee();
        when(progresoPort.deParticipante(actor)).thenReturn(
                Optional.of(new ProgresoParticipanteHabits(10, "UTC", RolParticipante.ADMIN, false, false)));

        assertThatThrownBy(() -> service.entregar(new EntregarResumenEspirituCommand(actor, 1, "resumen")))
                .isInstanceOf(NotAuthorizedException.class);
    }

    @Test
    void desbloqueaElPrimerAudioCuandoNoHayTrackYElDiaDeProgramaAlcanza() {
        UserId actor = trainee();
        // diaPrograma 8 -> audioDay 1 (AUDIO_UNLOCK_START_DAY = 7)
        when(progresoPort.deParticipante(actor)).thenReturn(
                Optional.of(new ProgresoParticipanteHabits(8, "UTC", RolParticipante.TRAINEE, false, false)));
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
                Optional.of(new ProgresoParticipanteHabits(5, "UTC", RolParticipante.TRAINEE, false, false)));
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
                Optional.of(new ProgresoParticipanteHabits(8, "UTC", RolParticipante.TRAINEE, false, false)));
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
                Optional.of(new ProgresoParticipanteHabits(8, "UTC", RolParticipante.TRAINEE, false, false)));
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
                Optional.of(new ProgresoParticipanteHabits(8, "UTC", RolParticipante.TRAINEE, false, false)));
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
                Optional.of(new ProgresoParticipanteHabits(9, "UTC", RolParticipante.TRAINEE, false, false)));
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
                Optional.of(new ProgresoParticipanteHabits(9, "UTC", RolParticipante.TRAINEE, false, false)));
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
                Optional.of(new ProgresoParticipanteHabits(8, "UTC", RolParticipante.TRAINEE, false, false)));
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
                Optional.of(new ProgresoParticipanteHabits(8, "UTC", RolParticipante.TRAINEE, false, false)));
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
                Optional.of(new ProgresoParticipanteHabits(3, "UTC", RolParticipante.TRAINEE, false, false)));
        when(loadPort.ultimoDe(actor)).thenReturn(Optional.empty());
        when(loadPort.todosDe(actor)).thenReturn(List.of());
        when(audioCatalogPort.todos()).thenReturn(
                List.of(new AudioEspiritu(1, "Dia 1", "drive-1", "audio/mpeg", 1000, RUTA_AUDIO)));

        EstadoEspiritu estado = service.consultar(actor);

        assertThat(estado.dias()).hasSize(1);
        assertThat(estado.dias().get(0).estado()).isEqualTo("LOCKED");
        assertThat(estado.diaActual()).isNull();
    }

    /**
     * E-169: un MENTOR que activó su seguimiento personal opera su programa como cualquiera.
     *
     * <p>Es el reverso exacto del caso de arriba, y se construye sobre su mismo fixture para que
     * la única diferencia sea el dato que importa: `programaActivado`. Antes de esto,
     * {@code POST /api/v1/mentor/activate-tracking} inscribía al staff y después el guard lo
     * echaba — la inscripción estaba construida y el uso prohibido.
     */
    @Test
    @DisplayName("E-169: un MENTOR con su programa ACTIVADO ya no lo rechaza el guard de rol")
    void staffConProgramaActivadoOperaSuPrograma() {
        UserId actor = trainee();
        when(progresoPort.deParticipante(actor)).thenReturn(
                Optional.of(new ProgresoParticipanteHabits(10, "UTC", RolParticipante.MENTOR, false, true)));

        GuardDeRol.noRechaza(() -> service.consultar(actor), "Espiritu es exclusivo de aprendices");
    }

    // ─── Cuenta de revision de las tiendas (D-368, issue #184) ─────────────────────────────

    private static final String CORREO_DE_REVISION = "revision@ejemplo.test";
    /** 05:00 UTC: antes de las 07:00 de desbloqueo, para probar que la cuenta de revision no espera a la hora. */
    private static final FixedClock MADRUGADA = FixedClock.at(Instant.parse("2026-08-24T05:00:00Z"));

    private EspirituService servicioConRevision(UserId cuenta, String correos, FixedClock reloj) {
        UserSummary resumen = org.mockito.Mockito.mock(UserSummary.class);
        lenient().when(resumen.id()).thenReturn(cuenta);
        lenient().when(usuarios.findByEmail(CORREO_DE_REVISION)).thenReturn(Optional.of(resumen));
        return new EspirituService(loadPort, savePort, audioCatalogPort, progresoPort, almacenamientoPort,
                completarPastillaRenacer, new CuentasDeRevisionDeTiendas(correos, usuarios, reloj), reloj,
                idGenerator, transactionManager);
    }

    private void programaEnElDia(UserId actor, int diaPrograma) {
        when(progresoPort.deParticipante(actor)).thenReturn(
                Optional.of(new ProgresoParticipanteHabits(diaPrograma, "UTC", RolParticipante.TRAINEE, false, true)));
        lenient().when(loadPort.todosDe(actor)).thenReturn(List.of());
        lenient().when(audioCatalogPort.todos()).thenReturn(List.of());
    }

    private void catalogoConDias(int... dias) {
        for (int dia : dias) {
            lenient().when(audioCatalogPort.porDia(dia)).thenReturn(
                    Optional.of(new AudioEspiritu(dia, "Dia " + dia, "drive-" + dia, "audio/mpeg", 1000, RUTA_AUDIO)));
        }
    }

    private int diaGuardado() {
        ArgumentCaptor<RegistroEspiritu> guardado = ArgumentCaptor.forClass(RegistroEspiritu.class);
        verify(savePort).save(guardado.capture());
        return guardado.getValue().dia();
    }

    @Test
    @DisplayName("Revision: del dia 1 al 7 del programa y antes de las 7:00, la cuenta recibe el audio 1")
    void laCuentaDeRevisionRecibeElAudio1DesdeElDia1DelPrograma() {
        catalogoConDias(1);
        for (int diaPrograma = 1; diaPrograma <= 7; diaPrograma++) {
            org.mockito.Mockito.clearInvocations(savePort);
            UserId actor = trainee();
            EspirituService conRevision = servicioConRevision(actor, CORREO_DE_REVISION, MADRUGADA);
            programaEnElDia(actor, diaPrograma);
            when(loadPort.ultimoDe(actor)).thenReturn(Optional.empty());

            conRevision.consultar(actor);

            assertThat(diaGuardado()).as("dia de programa %d", diaPrograma).isEqualTo(1);
        }
    }

    @Test
    @DisplayName("Una cuenta normal, del dia 1 al 7 del programa, NO recibe ningun audio (la regla de siempre)")
    void unaCuentaNormalNoRecibeAudioAntesDelDia8() {
        catalogoConDias(1);
        EspirituService conLaListaDeRevision = null;
        for (int diaPrograma = 1; diaPrograma <= 7; diaPrograma++) {
            UserId actor = trainee();
            // la lista tiene OTRA cuenta: esta persona no esta en ella
            conLaListaDeRevision = servicioConRevision(trainee(), CORREO_DE_REVISION, CLOCK);
            programaEnElDia(actor, diaPrograma);
            when(loadPort.ultimoDe(actor)).thenReturn(Optional.empty());

            conLaListaDeRevision.consultar(actor);
        }
        verify(savePort, never()).save(any());
    }

    @Test
    @DisplayName("Revision: si el audio quedo PERDIDO, el siguiente no queda bloqueado un dia")
    void laCuentaDeRevisionNoQuedaBloqueadaTrasUnPerdido() {
        catalogoConDias(1, 2, 3, 4);
        UserId actor = trainee();
        EspirituService conRevision = servicioConRevision(actor, CORREO_DE_REVISION, CLOCK);
        programaEnElDia(actor, 12);
        Instant ayerAlMediodia = Instant.parse("2026-08-23T12:00:00Z");
        RegistroEspiritu perdido = RegistroEspiritu.rehydrate(RegistroEspirituId.of(UUID.randomUUID()), actor, 3,
                ayerAlMediodia.minusSeconds(18000), ayerAlMediodia, null, null,
                com.renaser.os.habits.domain.model.espiritu.EstadoRegistroEspiritu.PERDIDO, ayerAlMediodia,
                ayerAlMediodia);
        when(loadPort.ultimoDe(actor)).thenReturn(Optional.of(perdido));

        conRevision.consultar(actor);

        assertThat(diaGuardado()).isEqualTo(4);
    }

    @Test
    @DisplayName("Una cuenta normal con un audio PERDIDO ayer sigue bloqueada ese dia (comportamiento de siempre)")
    void unaCuentaNormalSigueBloqueadaTrasUnPerdido() {
        catalogoConDias(4);
        UserId actor = trainee();
        programaEnElDia(actor, 12);
        Instant ayerAlMediodia = Instant.parse("2026-08-23T12:00:00Z");
        RegistroEspiritu perdido = RegistroEspiritu.rehydrate(RegistroEspirituId.of(UUID.randomUUID()), actor, 3,
                ayerAlMediodia.minusSeconds(18000), ayerAlMediodia, null, null,
                com.renaser.os.habits.domain.model.espiritu.EstadoRegistroEspiritu.PERDIDO, ayerAlMediodia,
                ayerAlMediodia);
        when(loadPort.ultimoDe(actor)).thenReturn(Optional.of(perdido));

        service.consultar(actor);

        verify(savePort, never()).save(any());
    }

    @Test
    @DisplayName("Revision: al entregar el audio de hoy, el siguiente se abre de inmediato (sin esperar a manana)")
    void laCuentaDeRevisionRecibeElSiguienteAlEntregar() {
        catalogoConDias(1, 2);
        UserId actor = trainee();
        EspirituService conRevision = servicioConRevision(actor, CORREO_DE_REVISION, CLOCK);
        programaEnElDia(actor, 3);
        RegistroEspiritu entregado = RegistroEspiritu.rehydrate(RegistroEspirituId.of(UUID.randomUUID()), actor, 1,
                CLOCK.now().minusSeconds(3600), CLOCK.now().plusSeconds(3600), CLOCK.now(), "resumen",
                com.renaser.os.habits.domain.model.espiritu.EstadoRegistroEspiritu.ENTREGADO, CLOCK.now(), CLOCK.now());
        when(loadPort.ultimoDe(actor)).thenReturn(Optional.of(entregado));

        conRevision.consultar(actor);

        assertThat(diaGuardado()).isEqualTo(2);
    }

    @Test
    @DisplayName("Revision: con el audio de hoy todavia pendiente no se crea otro")
    void laCuentaDeRevisionConservaElAudioPendienteDeHoy() {
        catalogoConDias(1, 2);
        UserId actor = trainee();
        EspirituService conRevision = servicioConRevision(actor, CORREO_DE_REVISION, CLOCK);
        programaEnElDia(actor, 3);
        RegistroEspiritu pendiente = RegistroEspiritu.desbloquear(RegistroEspirituId.of(UUID.randomUUID()), actor, 1,
                CLOCK.now(), CLOCK.now().plusSeconds(3600), CLOCK.now());
        when(loadPort.ultimoDe(actor)).thenReturn(Optional.of(pendiente));

        conRevision.consultar(actor);

        verify(savePort, never()).save(any());
    }

    @Test
    @DisplayName("Revision: al llegar al ultimo audio del catalogo y entregarlo, ese audio sigue con su URL para escucharlo")
    void laCuentaDeRevisionSigueConAudioEnElUltimoDelCatalogo() {
        catalogoConDias(45);
        UserId actor = trainee();
        EspirituService conRevision = servicioConRevision(actor, CORREO_DE_REVISION, CLOCK);
        programaEnElDia(actor, 60);
        RegistroEspiritu ultimoEntregado = RegistroEspiritu.rehydrate(RegistroEspirituId.of(UUID.randomUUID()), actor,
                45, CLOCK.now().minusSeconds(3600), CLOCK.now().plusSeconds(3600), CLOCK.now(), "resumen",
                com.renaser.os.habits.domain.model.espiritu.EstadoRegistroEspiritu.ENTREGADO, CLOCK.now(), CLOCK.now());
        when(loadPort.ultimoDe(actor)).thenReturn(Optional.of(ultimoEntregado));
        when(loadPort.todosDe(actor)).thenReturn(List.of(ultimoEntregado));
        when(audioCatalogPort.todos()).thenReturn(
                List.of(new AudioEspiritu(45, "Dia 45", "drive-45", "audio/mpeg", 1000, RUTA_AUDIO)));
        when(audioCatalogPort.porDia(46)).thenReturn(Optional.empty());
        when(almacenamientoPort.firmarLectura(RUTA_AUDIO, EspirituService.TTL_AUDIO)).thenReturn(URL_FIRMADA);

        EstadoEspiritu estado = conRevision.consultar(actor);

        assertThat(estado.dias().getFirst().estado()).isEqualTo("SUBMITTED");
        assertThat(estado.dias().getFirst().audioUrl()).isEqualTo(URL_FIRMADA.toString());
    }

    @Test
    @DisplayName("Una cuenta normal con su ultimo audio entregado NO recibe URL de ese audio (solo el vigente la trae)")
    void unaCuentaNormalNoRecibeUrlDeUnAudioEntregado() {
        UserId actor = trainee();
        programaEnElDia(actor, 60);
        RegistroEspiritu entregado = RegistroEspiritu.rehydrate(RegistroEspirituId.of(UUID.randomUUID()), actor, 45,
                CLOCK.now().minusSeconds(3600), CLOCK.now().plusSeconds(3600), CLOCK.now(), "resumen",
                com.renaser.os.habits.domain.model.espiritu.EstadoRegistroEspiritu.ENTREGADO, CLOCK.now(), CLOCK.now());
        when(loadPort.ultimoDe(actor)).thenReturn(Optional.of(entregado));
        when(loadPort.todosDe(actor)).thenReturn(List.of(entregado));
        when(audioCatalogPort.todos()).thenReturn(
                List.of(new AudioEspiritu(45, "Dia 45", "drive-45", "audio/mpeg", 1000, RUTA_AUDIO)));

        EstadoEspiritu estado = service.consultar(actor);

        assertThat(estado.dias().getFirst().audioUrl()).isNull();
    }

    @Test
    @DisplayName("La lista de correos vacia (o solo comas y espacios) no marca a nadie y ni siquiera consulta a usuarios")
    void conLaListaVaciaNadieEsDeRevision() {
        UserId cualquiera = trainee();

        assertThat(new CuentasDeRevisionDeTiendas("", usuarios, CLOCK).esDeRevision(cualquiera)).isFalse();
        assertThat(new CuentasDeRevisionDeTiendas(" , ,", usuarios, CLOCK).esDeRevision(cualquiera)).isFalse();
        verifyNoInteractions(usuarios);
    }

    @Test
    @DisplayName("El correo se compara sin importar mayusculas ni espacios, y se resuelve una vez por vigencia")
    void laCuentaDeRevisionSeResuelveUnaVezPorVigencia() {
        UserId cuenta = trainee();
        UserSummary resumen = org.mockito.Mockito.mock(UserSummary.class);
        when(resumen.id()).thenReturn(cuenta);
        when(usuarios.findByEmail(CORREO_DE_REVISION)).thenReturn(Optional.of(resumen));
        var cuentas = new CuentasDeRevisionDeTiendas("  REVISION@Ejemplo.test ", usuarios, CLOCK);

        assertThat(cuentas.esDeRevision(cuenta)).isTrue();
        assertThat(cuentas.esDeRevision(cuenta)).isTrue();
        assertThat(cuentas.esDeRevision(trainee())).isFalse();
        verify(usuarios, org.mockito.Mockito.times(1)).findByEmail(CORREO_DE_REVISION);
    }
}

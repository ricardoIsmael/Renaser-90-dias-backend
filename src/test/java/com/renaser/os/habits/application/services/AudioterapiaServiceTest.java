package com.renaser.os.habits.application.services;

import com.renaser.os.habits.application.ports.in.audioterapia.ConsultarAudioterapiaSemanalUseCase.AudioDeLaSemana;
import com.renaser.os.habits.application.ports.in.audioterapia.ConsultarAudioterapiaSemanalUseCase.EsperandoContenido;
import com.renaser.os.habits.application.ports.in.audioterapia.ConsultarAudioterapiaSemanalUseCase.EstadoAudioterapia;
import com.renaser.os.habits.application.ports.out.audioterapia.AudioterapiaCatalogPort;
import com.renaser.os.habits.application.ports.out.audioterapia.AudioterapiaCatalogPort.Audioterapia;
import com.renaser.os.habits.application.ports.out.habito.LoadHabitoPort;
import com.renaser.os.habits.application.ports.out.horario.LoadHorarioHabitoPort;
import com.renaser.os.habits.application.ports.out.participante.ConsultarProgresoParticipanteHabitsPort;
import com.renaser.os.habits.application.ports.out.participante.ConsultarProgresoParticipanteHabitsPort.ProgresoParticipanteHabits;
import com.renaser.os.habits.application.ports.out.participante.ConsultarProgresoParticipanteHabitsPort.RolParticipante;
import com.renaser.os.habits.domain.model.habito.DetallesHabito;
import com.renaser.os.habits.domain.model.habito.ExigenciaEvidencia;
import com.renaser.os.habits.domain.model.habito.Habito;
import com.renaser.os.habits.domain.model.habito.HabitoId;
import com.renaser.os.habits.domain.model.habito.TipoDia;
import com.renaser.os.habits.domain.model.habito.TipoHabito;
import com.renaser.os.habits.domain.model.horario.HorarioHabito;
import com.renaser.os.habits.domain.model.horario.HorarioHabitoId;
import com.renaser.os.shared.application.ports.out.AlmacenamientoPort;
import com.renaser.os.shared.domain.FixedClock;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AudioterapiaServiceTest {

    private static final Instant AHORA = Instant.parse("2026-08-28T10:00:00Z");

    @Mock
    private LoadHabitoPort loadHabitoPort;
    @Mock
    private LoadHorarioHabitoPort loadHorarioPort;
    @Mock
    private AudioterapiaCatalogPort catalogoPort;
    @Mock
    private ConsultarProgresoParticipanteHabitsPort progresoPort;
    @Mock
    private AlmacenamientoPort almacenamientoPort;

    private AudioterapiaService service;

    private final UserId trainee = UserId.of(UUID.randomUUID());

    @BeforeEach
    void setUp() {
        service = new AudioterapiaService(loadHabitoPort, loadHorarioPort, catalogoPort, progresoPort,
                almacenamientoPort);
        Habito habito = Habito.crearDeSistema(HabitoId.of(UUID.randomUUID()), "AUDIOTERAPIA SEMANAL",
                TipoHabito.JOURNALING, new DetallesHabito(null, "ESPIRITU", ExigenciaEvidencia.OPCIONAL, false, false),
                AHORA);
        lenient().when(loadHabitoPort.porClaveSistema("AUDIO_THERAPY_WEEKLY")).thenReturn(Optional.of(habito));
        HorarioHabito horario = HorarioHabito.crear(HorarioHabitoId.of(UUID.randomUUID()), habito.id(), 11, 90,
                TipoDia.TODOS, null, null, AHORA);
        lenient().when(loadHorarioPort.porHabito(habito.id())).thenReturn(List.of(horario));
        lenient().when(almacenamientoPort.firmarLectura(any(), any())).thenReturn(URI.create("https://firmada"));
    }

    private void progresoDia(int diaPrograma) {
        when(progresoPort.deParticipante(trainee)).thenReturn(
                Optional.of(new ProgresoParticipanteHabits(diaPrograma, "America/Lima", RolParticipante.TRAINEE,
                        false)));
    }

    @Test
    void antesDelDiaDeInicioNoHayAudioterapia() {
        progresoDia(5);

        EstadoAudioterapia estado = service.consultar(trainee);

        assertThat(estado).isInstanceOf(EsperandoContenido.class);
    }

    @Test
    void enElDiaDeInicioResuelveLaSemanaUno() {
        progresoDia(11);
        when(catalogoPort.todasOrdenadas()).thenReturn(
                List.of(new Audioterapia(1, "Semana 1", "ruta/1.mp3", "audio/mpeg", 1000, 7)));

        EstadoAudioterapia estado = service.consultar(trainee);

        assertThat(estado).isInstanceOfSatisfying(AudioDeLaSemana.class, audio -> {
            assertThat(audio.semanaActual()).isEqualTo(1);
            assertThat(audio.titulo()).isEqualTo("Semana 1");
            assertThat(audio.url()).isEqualTo("https://firmada");
            assertThat(audio.diaSiguienteCambio()).isEqualTo(18); // 11 + 7 dias
        });
    }

    @Test
    void respetaDuracionesDistintasPorSemanaAlAcumular() {
        // semana 1 dura 5 dias (11-15), semana 2 arranca el 16
        progresoDia(16);
        when(catalogoPort.todasOrdenadas()).thenReturn(List.of(
                new Audioterapia(1, "Semana 1", "ruta/1.mp3", "audio/mpeg", 1000, 5),
                new Audioterapia(2, "Semana 2", "ruta/2.mp3", "audio/mpeg", 1000, 7)));

        EstadoAudioterapia estado = service.consultar(trainee);

        assertThat(estado).isInstanceOfSatisfying(AudioDeLaSemana.class, audio -> {
            assertThat(audio.semanaActual()).isEqualTo(2);
            assertThat(audio.titulo()).isEqualTo("Semana 2");
        });
    }

    @Test
    void masAlaDeLaUltimaSemanaCargadaQuedaSinContenido() {
        progresoDia(90);
        when(catalogoPort.todasOrdenadas()).thenReturn(
                List.of(new Audioterapia(1, "Semana 1", "ruta/1.mp3", "audio/mpeg", 1000, 7)));

        EstadoAudioterapia estado = service.consultar(trainee);

        assertThat(estado).isInstanceOf(EsperandoContenido.class);
    }

    @Test
    void consultarRechazaSuspendido() {
        when(progresoPort.deParticipante(trainee)).thenReturn(
                Optional.of(new ProgresoParticipanteHabits(20, "UTC", RolParticipante.TRAINEE, true)));

        assertThatThrownBy(() -> service.consultar(trainee)).isInstanceOf(NotAuthorizedException.class);
    }

    @Test
    void consultarRechazaRolDistintoDeTrainee() {
        when(progresoPort.deParticipante(trainee)).thenReturn(
                Optional.of(new ProgresoParticipanteHabits(20, "UTC", RolParticipante.MENTOR, false)));

        assertThatThrownBy(() -> service.consultar(trainee)).isInstanceOf(NotAuthorizedException.class);
    }
}

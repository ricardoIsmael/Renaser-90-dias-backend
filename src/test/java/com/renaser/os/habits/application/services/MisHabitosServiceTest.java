package com.renaser.os.habits.application.services;

import com.renaser.os.habits.application.ports.in.habito.CrearHabitoPersonalUseCase.CrearHabitoPersonalCommand;
import com.renaser.os.habits.application.ports.out.habito.LoadHabitoPort;
import com.renaser.os.habits.application.ports.out.habito.SaveHabitoPort;
import com.renaser.os.habits.application.ports.out.horario.SaveHorarioHabitoPort;
import com.renaser.os.habits.application.ports.out.participante.ConsultarProgresoParticipanteHabitsPort;
import com.renaser.os.habits.application.ports.out.participante.ConsultarProgresoParticipanteHabitsPort.ProgresoParticipanteHabits;
import com.renaser.os.habits.application.ports.out.participante.ConsultarProgresoParticipanteHabitsPort.RolParticipante;
import com.renaser.os.habits.domain.model.habito.AmbitoHabito;
import com.renaser.os.habits.domain.model.habito.DetallesHabito;
import com.renaser.os.habits.domain.model.habito.ExigenciaEvidencia;
import com.renaser.os.habits.domain.model.habito.Habito;
import com.renaser.os.habits.domain.model.habito.HabitoId;
import com.renaser.os.habits.domain.model.habito.PlantillaHabitoPersonal;
import com.renaser.os.habits.domain.model.habito.TipoDia;
import com.renaser.os.habits.domain.model.habito.TipoHabito;
import com.renaser.os.habits.domain.model.horario.HorarioHabito;
import com.renaser.os.shared.domain.FixedClock;
import com.renaser.os.shared.domain.IdGenerator;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import jakarta.validation.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** NOTA: pruebas escritas en esta pasada, no verificadas con {@code ./mvnw} (regla del encargo). */
@ExtendWith(MockitoExtension.class)
class MisHabitosServiceTest {

    private static final FixedClock CLOCK = FixedClock.at(Instant.parse("2026-08-28T10:00:00Z"));
    private static final LocalTime DISPARO = LocalTime.of(6, 0);
    private static final LocalTime LIMITE = LocalTime.of(22, 0);

    @Mock
    private LoadHabitoPort loadPort;
    @Mock
    private SaveHabitoPort savePort;
    @Mock
    private SaveHorarioHabitoPort saveHorarioPort;
    @Mock
    private ConsultarProgresoParticipanteHabitsPort progresoPort;
    @Mock
    private IdGenerator idGenerator;

    private final UserId actor = UserId.of(UUID.randomUUID());

    private MisHabitosService service;

    @BeforeEach
    void setUp() {
        service = new MisHabitosService(loadPort, savePort, saveHorarioPort, progresoPort, CLOCK, idGenerator);
        lenient().when(savePort.save(any())).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(saveHorarioPort.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    private CrearHabitoPersonalCommand comando(LocalTime disparo, LocalTime limite) {
        return new CrearHabitoPersonalCommand(actor, "Correr 5km", TipoHabito.CHECKBOX, "CUERPO",
                PlantillaHabitoPersonal.CORRER, "meta", disparo, limite);
    }

    // ---- consultar (comportamiento preexistente, sin cambios de contrato) ----

    @Test
    void combinaCatalogoActivoConLosPersonalesDelActor() {
        Habito sistema = Habito.crearDeSistema(HabitoId.of(UUID.randomUUID()), "Meditar", TipoHabito.CHECKBOX,
                new DetallesHabito("desc", "MENTE", ExigenciaEvidencia.OPCIONAL, false, false), CLOCK.now());
        Habito personal = Habito.crearPersonal(HabitoId.of(UUID.randomUUID()), actor, "Mi reto", TipoHabito.CHECKBOX,
                "CUERPO", PlantillaHabitoPersonal.OTRO, "etiqueta", CLOCK.now());
        when(loadPort.catalogoActivo()).thenReturn(List.of(sistema));
        when(loadPort.personalesActivosDe(actor)).thenReturn(List.of(personal));

        List<Habito> resultado = service.consultar(actor);

        assertThat(resultado).containsExactlyInAnyOrder(sistema, personal);
    }

    @Test
    void sinHabitosPersonalesDevuelveSoloElCatalogo() {
        Habito sistema = Habito.crearDeSistema(HabitoId.of(UUID.randomUUID()), "Meditar", TipoHabito.CHECKBOX,
                new DetallesHabito("desc", "MENTE", ExigenciaEvidencia.OPCIONAL, false, false), CLOCK.now());
        when(loadPort.catalogoActivo()).thenReturn(List.of(sistema));
        when(loadPort.personalesActivosDe(actor)).thenReturn(List.of());

        assertThat(service.consultar(actor)).containsExactly(sistema);
    }

    // ---- crear (comportamiento preexistente) ----

    @Test
    void crearHabitoPersonalSuspendidoRechazado() {
        when(progresoPort.deParticipante(actor)).thenReturn(
                Optional.of(new ProgresoParticipanteHabits(10, "UTC", RolParticipante.TRAINEE, true)));

        assertThatThrownBy(() -> service.crear(comando(DISPARO, LIMITE))).isInstanceOf(NotAuthorizedException.class);

        // Ni el habito ni el horario deben tocar los puertos out si el guard rechaza antes.
        verifyNoInteractions(savePort, saveHorarioPort);
    }

    @Test
    void crearHabitoPersonalParticipanteNoEncontradoRechazado() {
        when(progresoPort.deParticipante(actor)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.crear(comando(DISPARO, LIMITE)))
                .isInstanceOf(java.util.NoSuchElementException.class);
        verifyNoInteractions(savePort, saveHorarioPort);
    }

    @Test
    void creaUnHabitoPersonalConIdentidadDelActorSinImportarQueNoSePidaEnElComando() {
        when(progresoPort.deParticipante(actor)).thenReturn(
                Optional.of(new ProgresoParticipanteHabits(10, "UTC", RolParticipante.TRAINEE, false)));
        UUID idGenerado = UUID.randomUUID();
        when(idGenerator.newId()).thenReturn(idGenerado);

        Habito resultado = service.crear(comando(DISPARO, LIMITE));

        // El comando NUNCA tiene un campo ambito/participanteId (CrearHabitoPersonalCommand no
        // lo declara) — este assert verifica que el AGREGADO resultante quedo forzado a
        // PERSONAL y al actor autenticado, no a lo que hubiera podido mandar el cliente.
        assertThat(resultado.ambito()).isEqualTo(AmbitoHabito.PERSONAL);
        assertThat(resultado.participanteId()).isEqualTo(actor);
        assertThat(resultado.id()).isEqualTo(HabitoId.of(idGenerado));
        assertThat(resultado.titulo()).isEqualTo("Correr 5km");
        assertThat(resultado.plantillaClave()).isEqualTo(PlantillaHabitoPersonal.CORRER);
        assertThat(resultado.activo()).isTrue();
    }

    // ---- crear + horario (nuevo — habits-personal-con-horario) ----

    @Test
    void crearHabitoPersonalCreaTambienElHorarioConElDiaDeProgramaActualYTipoDiaTodos() {
        when(progresoPort.deParticipante(actor)).thenReturn(
                Optional.of(new ProgresoParticipanteHabits(23, "UTC", RolParticipante.TRAINEE, false)));
        when(idGenerator.newId()).thenReturn(UUID.randomUUID(), UUID.randomUUID());

        service.crear(comando(DISPARO, LIMITE));

        org.mockito.ArgumentCaptor<HorarioHabito> captor = org.mockito.ArgumentCaptor.forClass(HorarioHabito.class);
        org.mockito.Mockito.verify(saveHorarioPort).save(captor.capture());
        HorarioHabito horario = captor.getValue();
        assertThat(horario.diaInicio()).isEqualTo(23);
        assertThat(horario.diaFin()).isNull();
        assertThat(horario.tipoDia()).isEqualTo(TipoDia.TODOS);
        assertThat(horario.horaDisparo()).isEqualTo(DISPARO);
        assertThat(horario.horaLimite()).isEqualTo(LIMITE);
    }

    @Test
    void crearHabitoPersonalSinHoraLimiteEsValido() {
        when(progresoPort.deParticipante(actor)).thenReturn(
                Optional.of(new ProgresoParticipanteHabits(10, "UTC", RolParticipante.TRAINEE, false)));
        when(idGenerator.newId()).thenReturn(UUID.randomUUID(), UUID.randomUUID());

        service.crear(comando(DISPARO, null));

        org.mockito.ArgumentCaptor<HorarioHabito> captor = org.mockito.ArgumentCaptor.forClass(HorarioHabito.class);
        org.mockito.Mockito.verify(saveHorarioPort).save(captor.capture());
        assertThat(captor.getValue().horaLimite()).isNull();
    }

    @Test
    void crearHabitoPersonalSinHoraDisparoEsRechazadoPorElComandoAntesDeLlegarAlServicio() {
        assertThatThrownBy(() -> comando(null, LIMITE)).isInstanceOf(ConstraintViolationException.class);
        verifyNoInteractions(savePort, saveHorarioPort, progresoPort);
    }

    @Test
    void crearHabitoPersonalConHoraLimiteAnteriorAHoraDisparoEsRechazadoPorElComando() {
        assertThatThrownBy(() -> comando(LocalTime.of(22, 0), LocalTime.of(6, 0)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("horaLimite");
        verifyNoInteractions(savePort, saveHorarioPort, progresoPort);
    }

    @Test
    void crearHabitoPersonalConHoraLimiteIgualAHoraDisparoEsRechazadoPorElComando() {
        assertThatThrownBy(() -> comando(DISPARO, DISPARO)).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(savePort, saveHorarioPort, progresoPort);
    }
}

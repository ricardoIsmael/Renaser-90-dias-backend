package com.renaser.os.habits.application.services;

import com.renaser.os.evidence.api.RegistrosConEvidenciaFinder;
import com.renaser.os.habits.application.ports.in.registro.ConsultarTracksDelDiaConCatalogoUseCase.TrackDelDiaConCatalogo;
import com.renaser.os.habits.application.ports.in.registro.ConsultarTracksDelDiaUseCase;
import com.renaser.os.habits.application.ports.in.registro.GenerarTracksDelDiaUseCase;
import com.renaser.os.habits.application.ports.out.guia.LoadGuiaHabitoPort;
import com.renaser.os.habits.application.ports.out.habito.LoadHabitoPort;
import com.renaser.os.habits.application.ports.out.horario.LoadHorarioHabitoPort;
import com.renaser.os.habits.application.ports.out.participante.ConsultarProgresoParticipanteHabitsPort;
import com.renaser.os.habits.application.ports.out.preferencia.LoadPreferenciaHorarioPort;
import com.renaser.os.habits.domain.model.habito.ExigenciaEvidencia;
import com.renaser.os.habits.domain.model.habito.Habito;
import com.renaser.os.habits.domain.model.habito.HabitoId;
import com.renaser.os.habits.domain.model.habito.TipoDia;
import com.renaser.os.habits.domain.model.habito.TipoHabito;
import com.renaser.os.habits.domain.model.registro.RegistroHabito;
import com.renaser.os.habits.domain.model.registro.RegistroHabitoId;
import com.renaser.os.shared.domain.FixedClock;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@code tieneEvidencia} en la proyeccion de {@code GET /habit-tracks/today} (D-113).
 *
 * <p><b>Que bug fija.</b> Antes de este campo el movil respondia la pregunta por su cuenta:
 * cruzaba los {@code registroHabitoId} de {@code GET /api/v1/evidence} contra los tracks del dia.
 * Ese listado es UNA pagina de 20 filas, ordenada por fecha de creacion descendente, sin filtro de
 * dia y mezclando los tres destinos de evidencia — asi que la evidencia de un habito de hoy se
 * caia de la pagina en cuanto habia mas de 20 filas mas nuevas, y la pantalla ofrecia "SUBIR" un
 * archivo ya guardado.
 *
 * <p>De ahi la forma del caso principal de abajo: <b>25 registros y evidencia solo en el ultimo</b>
 * — justo el que un cruce contra una pagina de 20 no alcanza a ver. El resultado del servidor no
 * depende de cuantas filas entren en una pagina, y ese es el punto.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TracksDelDiaEvidenciaTest {

    private static final Instant AHORA = Instant.parse("2026-09-05T14:00:00Z");
    private static final LocalDate HOY = LocalDate.of(2026, 9, 5);
    private static final UserId PARTICIPANTE = UserId.of(UUID.randomUUID());

    @Mock
    private ConsultarTracksDelDiaUseCase consultarTracksUseCase;
    @Mock
    private GenerarTracksDelDiaUseCase generarTracksUseCase;
    @Mock
    private LoadHabitoPort loadHabitoPort;
    @Mock
    private LoadHorarioHabitoPort loadHorarioPort;
    @Mock
    private LoadPreferenciaHorarioPort loadPreferenciaPort;
    @Mock
    private LoadGuiaHabitoPort loadGuiaPort;
    @Mock
    private ConsultarProgresoParticipanteHabitsPort progresoPort;
    @Mock
    private RegistrosConEvidenciaFinder registrosConEvidenciaFinder;

    private TracksDelDiaProyeccionService servicio() {
        return new TracksDelDiaProyeccionService(consultarTracksUseCase, generarTracksUseCase, loadHabitoPort,
                loadHorarioPort, loadPreferenciaPort, loadGuiaPort, progresoPort, registrosConEvidenciaFinder,
                FixedClock.at(AHORA));
    }

    private static Habito habito() {
        return Habito.crearDeSistema(HabitoId.of(UUID.randomUUID()), "Jugo verde", TipoHabito.CHECKBOX, "MENTE",
                ExigenciaEvidencia.OPCIONAL, AHORA);
    }

    private static RegistroHabito registroDe(Habito habito) {
        return RegistroHabito.generar(RegistroHabitoId.of(UUID.randomUUID()), PARTICIPANTE, habito.id(), HOY, 5,
                TipoDia.DISCIPLINA, false, AHORA);
    }

    private void catalogoDe(Habito habito) {
        when(loadHabitoPort.porIds(any())).thenReturn(List.of(habito));
        when(loadHorarioPort.porHabitos(any())).thenReturn(List.of());
        when(loadGuiaPort.porHabitos(any())).thenReturn(List.of());
        when(loadPreferenciaPort.porParticipanteYHabitos(any(), any())).thenReturn(List.of());
    }

    @Test
    @DisplayName("el track con evidencia la declara aunque sea el numero 25 del dia")
    void marcaElTrackConEvidenciaSinImportarSuPosicionEnElDia() {
        Habito habito = habito();
        List<RegistroHabito> registros = new ArrayList<>();
        for (int i = 0; i < 25; i++) {
            registros.add(registroDe(habito));
        }
        RegistroHabito elUltimo = registros.get(24);
        catalogoDe(habito);
        when(consultarTracksUseCase.consultar(PARTICIPANTE, PARTICIPANTE, HOY)).thenReturn(registros);
        when(registrosConEvidenciaFinder.deEntre(any())).thenReturn(Set.of(elUltimo.id().value()));

        List<TrackDelDiaConCatalogo> resultado = servicio().consultar(PARTICIPANTE, PARTICIPANTE, HOY);

        assertThat(resultado).hasSize(25);
        assertThat(resultado).filteredOn(TrackDelDiaConCatalogo::tieneEvidencia)
                .extracting(t -> t.registro().id())
                .containsExactly(elUltimo.id());
    }

    @Test
    @DisplayName("pregunta UNA sola vez, por todos los registros del dia (nunca N+1)")
    void preguntaUnaSolaVezPorTodoElLote() {
        Habito habito = habito();
        List<RegistroHabito> registros = List.of(registroDe(habito), registroDe(habito), registroDe(habito));
        catalogoDe(habito);
        when(consultarTracksUseCase.consultar(PARTICIPANTE, PARTICIPANTE, HOY)).thenReturn(registros);
        when(registrosConEvidenciaFinder.deEntre(any())).thenReturn(Set.of());

        servicio().consultar(PARTICIPANTE, PARTICIPANTE, HOY);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<UUID>> captor = ArgumentCaptor.forClass(Collection.class);
        verify(registrosConEvidenciaFinder, times(1)).deEntre(captor.capture());
        assertThat(captor.getValue())
                .containsExactlyInAnyOrderElementsOf(registros.stream().map(r -> r.id().value()).toList());
    }

    @Test
    @DisplayName("sin evidencia, todos los tracks salen en false — nunca null ni ausente")
    void sinEvidenciaTodosEnFalse() {
        Habito habito = habito();
        catalogoDe(habito);
        when(consultarTracksUseCase.consultar(PARTICIPANTE, PARTICIPANTE, HOY))
                .thenReturn(List.of(registroDe(habito), registroDe(habito)));
        when(registrosConEvidenciaFinder.deEntre(any())).thenReturn(Set.of());

        List<TrackDelDiaConCatalogo> resultado = servicio().consultar(PARTICIPANTE, PARTICIPANTE, HOY);

        assertThat(resultado).isNotEmpty().allMatch(t -> !t.tieneEvidencia());
    }

    @Test
    @DisplayName("un dia sin habitos no le cuesta una consulta a evidence")
    void sinRegistrosNoPreguntaAEvidence() {
        when(consultarTracksUseCase.consultar(PARTICIPANTE, PARTICIPANTE, HOY)).thenReturn(List.of());

        assertThat(servicio().consultar(PARTICIPANTE, PARTICIPANTE, HOY)).isEmpty();

        verify(registrosConEvidenciaFinder, never()).deEntre(any());
    }
}

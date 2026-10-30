package com.renaser.os.rocks.application.services;

import com.renaser.os.evidence.api.EvidenciasDeRocasFinder;
import com.renaser.os.rocks.api.AccionesDelDiaFinder.AccionDelDia;
import com.renaser.os.rocks.application.ports.out.rocadiaria.LoadRocaDiariaPort;
import com.renaser.os.rocks.domain.model.rocadiaria.ColorPareto;
import com.renaser.os.rocks.domain.model.rocadiaria.RocaDiaria;
import com.renaser.os.rocks.domain.model.rocadiaria.RocaDiariaId;
import com.renaser.os.rocks.domain.model.rocamaestra.EjeObjetivo;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Las acciones del día de la ficha del líder (spec 005, R-5): las mismas rocas, con la evidencia de cada una. */
class AccionesDelDiaFinderServiceTest {

    private static final UserId ANA = UserId.of(UUID.fromString("00000000-0000-0000-0000-0000000000b1"));
    private static final LocalDate DESDE = LocalDate.of(2026, 10, 12);
    private static final LocalDate HASTA = LocalDate.of(2026, 10, 18);
    private static final Instant AHORA = Instant.parse("2026-10-14T15:00:00Z");

    private final LoadRocaDiariaPort rocas = mock(LoadRocaDiariaPort.class);
    private final Map<UUID, UUID> evidenciasPorRoca = new java.util.HashMap<>();
    private final java.util.List<Collection<UUID>> preguntadas = new java.util.ArrayList<>();
    private final EvidenciasDeRocasFinder evidencias = ids -> {
        preguntadas.add(List.copyOf(ids));
        Map<UUID, UUID> encontradas = new java.util.HashMap<>();
        ids.forEach(id -> {
            if (evidenciasPorRoca.containsKey(id)) {
                encontradas.put(id, evidenciasPorRoca.get(id));
            }
        });
        return encontradas;
    };
    private final AccionesDelDiaFinderService servicio = new AccionesDelDiaFinderService(rocas, evidencias);

    private static RocaDiaria roca(LocalDate fecha, int posicion, boolean completada) {
        return roca(fecha, posicion, completada, null);
    }

    private static RocaDiaria roca(LocalDate fecha, int posicion, boolean completada, LocalTime horaInicio) {
        return RocaDiaria.rehydrate(new RocaDiariaId(UUID.randomUUID()), ANA, fecha, posicion, "Accion " + posicion,
                null, ColorPareto.paraPosicion(posicion), 5, false, EjeObjetivo.CUERPO, null, horaInicio, null,
                List.of(), completada, completada ? AHORA : null, 0, AHORA, AHORA);
    }

    @Test
    @DisplayName("cada accion trae su dia, su titulo, si esta cumplida y la evidencia con la que se cumplio")
    void accionesConSuEvidencia() {
        RocaDiaria cumplida = roca(LocalDate.of(2026, 10, 14), 1, true);
        RocaDiaria pendiente = roca(LocalDate.of(2026, 10, 14), 2, false);
        UUID foto = UUID.randomUUID();
        evidenciasPorRoca.put(cumplida.id().value(), foto);
        when(rocas.deParticipanteEntreFechas(ANA, DESDE, HASTA)).thenReturn(List.of(cumplida, pendiente));

        List<AccionDelDia> acciones = servicio.accionesEntre(ANA, DESDE, HASTA);

        assertThat(acciones).containsExactly(
                new AccionDelDia(cumplida.id().value(), LocalDate.of(2026, 10, 14), "Accion 1", true, foto),
                new AccionDelDia(pendiente.id().value(), LocalDate.of(2026, 10, 14), "Accion 2", false, null));
        verify(rocas).deParticipanteEntreFechas(ANA, DESDE, HASTA);
    }

    @Test
    @DisplayName("spec 018: cada accion trae la hora en que la persona la agendo, o nada si no la agendo")
    void accionesConSuHora() {
        RocaDiaria agendada = roca(LocalDate.of(2026, 10, 14), 1, false, LocalTime.of(9, 30));
        RocaDiaria suelta = roca(LocalDate.of(2026, 10, 14), 2, false);
        when(rocas.deParticipanteEntreFechas(ANA, DESDE, HASTA)).thenReturn(List.of(agendada, suelta));

        List<AccionDelDia> acciones = servicio.accionesEntre(ANA, DESDE, HASTA);

        assertThat(acciones).extracting(AccionDelDia::horaInicio).containsExactly(LocalTime.of(9, 30), null);
        assertThat(acciones).extracting(AccionDelDia::agendada).containsExactly(true, false);
    }

    @Test
    @DisplayName("solo pregunta por la evidencia de las cumplidas, y de una sola vez")
    void unaSolaPreguntaSoloDeLasCumplidas() {
        RocaDiaria a = roca(LocalDate.of(2026, 10, 13), 1, true);
        RocaDiaria b = roca(LocalDate.of(2026, 10, 14), 1, true);
        RocaDiaria c = roca(LocalDate.of(2026, 10, 15), 1, false);
        when(rocas.deParticipanteEntreFechas(any(), any(), any())).thenReturn(List.of(a, b, c));

        servicio.accionesEntre(ANA, DESDE, HASTA);

        assertThat(preguntadas).singleElement().asList().containsExactly(a.id().value(), b.id().value());
    }

    @Test
    @DisplayName("una persona sin acciones en el rango devuelve la lista vacia, no null")
    void sinAcciones() {
        when(rocas.deParticipanteEntreFechas(any(), any(), any())).thenReturn(List.of());

        assertThat(servicio.accionesEntre(ANA, DESDE, HASTA)).isEmpty();
    }
}

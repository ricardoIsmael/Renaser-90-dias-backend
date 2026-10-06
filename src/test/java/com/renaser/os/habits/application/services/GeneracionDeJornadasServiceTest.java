package com.renaser.os.habits.application.services;

import com.renaser.os.habits.application.ports.in.preferencia.PromoverCambiosHorarioProgramadosUseCase;
import com.renaser.os.habits.application.ports.in.registro.GenerarJornadasDelDiaUseCase.ResultadoDelBarrido;
import com.renaser.os.habits.application.ports.in.registro.GenerarTracksDelDiaUseCase;
import com.renaser.os.habits.application.ports.out.participante.ConsultarProgresoParticipanteHabitsPort;
import com.renaser.os.habits.application.ports.out.registro.ConsultarJornadasGeneradasPort;
import com.renaser.os.shared.domain.FixedClock;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * E-556 — el barrido horario que arma el dia de cada participante cuando empieza SU dia. Por regla 02 §3 y 03, cada
 * caso fija el reloj en una hora UTC que cae en el dia local ANTERIOR de America (entre 00:00 y 05:00 UTC).
 */
@ExtendWith(MockitoExtension.class)
class GeneracionDeJornadasServiceTest {

    private static final ZoneId LIMA = ZoneId.of("America/Lima");
    private static final ZoneId LOS_ANGELES = ZoneId.of("America/Los_Angeles");
    private static final ZoneId TOKIO = ZoneId.of("Asia/Tokyo");
    /** 05:02 UTC del 9/11: 00:02 en Lima, 21:02 del 8/11 en Los Angeles, 14:02 del 9/11 en Tokio. */
    private static final Instant CINCO_Y_DOS = Instant.parse("2026-11-09T05:02:00Z");

    @Mock
    private ConsultarProgresoParticipanteHabitsPort progresoPort;
    @Mock
    private ConsultarJornadasGeneradasPort jornadasPort;
    @Mock
    private ZonasDelPadron zonas;
    @Mock
    private PromoverCambiosHorarioProgramadosUseCase promoverCambios;
    @Mock
    private GenerarTracksDelDiaUseCase generarTracks;

    private final UserId ana = participante();
    private final UserId carla = participante();
    private final UserId eli = participante();

    @BeforeEach
    void preparar() {
        lenient().when(zonas.leerLote(any())).thenReturn(Map.of());
        lenient().when(zonas.de(eq(ana), any())).thenReturn(LIMA);
        lenient().when(zonas.de(eq(carla), any())).thenReturn(LOS_ANGELES);
        lenient().when(zonas.de(eq(eli), any())).thenReturn(TOKIO);
        lenient().when(jornadasPort.conRegistrosEn(any(), any())).thenReturn(Set.of());
        lenient().when(generarTracks.generarDiaCompletoEnSuZona(any())).thenReturn(List.of());
        lenient().when(generarTracks.generarDisponiblesAhora(any())).thenReturn(List.of());
    }

    private GeneracionDeJornadasService servicioA(Instant ahora) {
        return new GeneracionDeJornadasService(progresoPort, jornadasPort, zonas, promoverCambios, generarTracks,
                FixedClock.at(ahora));
    }

    private static UserId participante() {
        return UserId.of(UUID.randomUUID());
    }

    @Test
    @DisplayName("a las 05:02 UTC: Lima recibe su dia completo (00:02), Los Angeles (21:02 de ayer) y Tokio (14:02) lo disponible de SU fecha")
    void cadaUnoRecibeSuDiaEnSuZona() {
        when(progresoPort.participantesInscritosActivos()).thenReturn(List.of(ana, carla, eli));
        // Ana: 9/11 sin registros. Carla: 8/11 sin registros. Eli: 9/11 sin registros.

        ResultadoDelBarrido resultado = servicioA(CINCO_Y_DOS).generarLasQueYaEmpezaron();

        verify(jornadasPort).conRegistrosEn(any(), eq(LocalDate.of(2026, 11, 9)));
        verify(jornadasPort).conRegistrosEn(List.of(carla), LocalDate.of(2026, 11, 8));
        assertThat(resultado).isEqualTo(new ResultadoDelBarrido(3, 3, 0));
        verify(generarTracks).generarDiaCompletoEnSuZona(ana);
        verify(generarTracks, never()).generarDiaCompletoEnSuZona(carla);
        verify(generarTracks).generarDisponiblesAhora(carla);
    }

    @Test
    @DisplayName("quien ya tiene registros de SU hoy no se toca: ni se promueve ni se genera")
    void quienYaTieneSuDiaNoSeToca() {
        when(progresoPort.participantesInscritosActivos()).thenReturn(List.of(ana, carla));
        when(jornadasPort.conRegistrosEn(any(), eq(LocalDate.of(2026, 11, 9)))).thenReturn(Set.of(ana));
        when(jornadasPort.conRegistrosEn(any(), eq(LocalDate.of(2026, 11, 8)))).thenReturn(Set.of(carla));

        ResultadoDelBarrido resultado = servicioA(CINCO_Y_DOS).generarLasQueYaEmpezaron();

        assertThat(resultado).isEqualTo(new ResultadoDelBarrido(2, 0, 0));
        verify(generarTracks, never()).generarDiaCompletoEnSuZona(any());
        verify(generarTracks, never()).generarDisponiblesAhora(any());
        verify(promoverCambios, never()).promoverLosDe(any(), any());
    }

    @Test
    @DisplayName("al amanecer de su zona se arma el dia COMPLETO; con el backend caido (15:00 UTC) solo lo que todavia se puede completar")
    void completoAlAmanecerYDisponibleSiLlegaTarde() {
        when(progresoPort.participantesInscritosActivos()).thenReturn(List.of(ana));

        servicioA(Instant.parse("2026-11-09T05:02:00Z")).generarLasQueYaEmpezaron();
        servicioA(Instant.parse("2026-11-09T15:02:00Z")).generarLasQueYaEmpezaron();

        verify(generarTracks, times(1)).generarDiaCompletoEnSuZona(ana);
        verify(generarTracks, times(1)).generarDisponiblesAhora(ana);
    }

    @Test
    @DisplayName("los cambios de horario de la persona rigen ANTES de generarle el dia, con su hoy local (no el UTC)")
    void promueveSusCambiosAntesDeGenerar() {
        when(progresoPort.participantesInscritosActivos()).thenReturn(List.of(carla));

        servicioA(Instant.parse("2026-11-09T08:02:00Z")).generarLasQueYaEmpezaron();

        InOrder orden = inOrder(promoverCambios, generarTracks);
        orden.verify(promoverCambios).promoverLosDe(carla, LocalDate.of(2026, 11, 9));
        orden.verify(generarTracks).generarDiaCompletoEnSuZona(carla);
    }

    @Test
    @DisplayName("un participante que falla no detiene el barrido de los demas")
    void unParticipanteQueFallaNoFrenaAlResto() {
        UserId falla = participante();
        when(progresoPort.participantesInscritosActivos()).thenReturn(List.of(ana, falla, eli));
        lenient().when(zonas.de(eq(falla), any())).thenReturn(LIMA);
        when(generarTracks.generarDiaCompletoEnSuZona(falla)).thenThrow(new IllegalStateException("cuenta suspendida"));

        ResultadoDelBarrido resultado = servicioA(CINCO_Y_DOS).generarLasQueYaEmpezaron();

        assertThat(resultado).isEqualTo(new ResultadoDelBarrido(3, 2, 1));
        verify(generarTracks).generarDiaCompletoEnSuZona(ana);
        verify(generarTracks).generarDisponiblesAhora(eli);
    }

    @Test
    @DisplayName("un participante con la zona rota no frena a los demas")
    void unaZonaRotaNoFrenaAlResto() {
        UserId zonaRota = participante();
        when(progresoPort.participantesInscritosActivos()).thenReturn(List.of(zonaRota, ana));
        when(zonas.de(eq(zonaRota), any())).thenThrow(new IllegalArgumentException("Zona/Inexistente"));

        ResultadoDelBarrido resultado = servicioA(CINCO_Y_DOS).generarLasQueYaEmpezaron();

        assertThat(resultado).isEqualTo(new ResultadoDelBarrido(2, 1, 1));
        verify(generarTracks).generarDiaCompletoEnSuZona(ana);
    }

    @Test
    @DisplayName("si falla la consulta de 'quien ya tiene su dia' se genera igual: es idempotente")
    void sinSaberQuienTieneSuDiaSeGeneraIgual() {
        when(progresoPort.participantesInscritosActivos()).thenReturn(List.of(ana));
        when(jornadasPort.conRegistrosEn(any(), any())).thenThrow(new IllegalStateException("base caida un instante"));

        ResultadoDelBarrido resultado = servicioA(CINCO_Y_DOS).generarLasQueYaEmpezaron();

        assertThat(resultado).isEqualTo(new ResultadoDelBarrido(1, 1, 0));
        verify(generarTracks).generarDiaCompletoEnSuZona(ana);
    }

    @Test
    @DisplayName("con el padron vacio no consulta ni genera nada")
    void padronVacio() {
        when(progresoPort.participantesInscritosActivos()).thenReturn(List.of());

        assertThat(servicioA(CINCO_Y_DOS).generarLasQueYaEmpezaron()).isEqualTo(ResultadoDelBarrido.VACIO);

        verify(jornadasPort, never()).conRegistrosEn(any(), any());
    }

    @Test
    @DisplayName("pagina el padron de a TAMANO_LOTE: una consulta de zonas por lote, no una por persona")
    void recorreElPadronPorLotes() {
        List<UserId> padron = IntStream.range(0, GeneracionDeJornadasService.TAMANO_LOTE + 1)
                .mapToObj(i -> participante()).toList();
        when(progresoPort.participantesInscritosActivos()).thenReturn(padron);
        padron.forEach(p -> lenient().when(zonas.de(eq(p), any())).thenReturn(LIMA));

        ResultadoDelBarrido resultado = servicioA(CINCO_Y_DOS).generarLasQueYaEmpezaron();

        assertThat(resultado.participantes()).isEqualTo(padron.size());
        verify(zonas, times(2)).leerLote(any());
    }
}

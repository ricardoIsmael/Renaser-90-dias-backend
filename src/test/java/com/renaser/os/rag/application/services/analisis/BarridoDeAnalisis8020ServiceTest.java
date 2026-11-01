package com.renaser.os.rag.application.services.analisis;

import com.renaser.os.rag.application.ports.in.analisis.GenerarAnalisis8020UseCase;
import com.renaser.os.rag.application.ports.out.analisis.LoadAnalisisDelPeriodoPort;
import com.renaser.os.rag.application.ports.out.analisis.LoadAnalisisDelPeriodoPort.EstadoDeUnPeriodo;
import com.renaser.os.rag.application.services.circuito.InterruptoresDeSer;
import com.renaser.os.rag.domain.model.analisis.EstadoDelAnalisis;
import com.renaser.os.rag.domain.model.analisis.ReglasDelAnalisis;
import com.renaser.os.rag.domain.model.analisis.TipoDePeriodo;
import com.renaser.os.rocks.api.PeriodosDelProgramaFinder;
import com.renaser.os.rocks.api.PeriodosDelProgramaFinder.PeriodoDelPrograma;
import com.renaser.os.rocks.api.PeriodosDelProgramaFinder.Tipo;
import com.renaser.os.shared.domain.FixedClock;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.ParticipacionProgramaFinder;
import com.renaser.os.users.api.ProgramasActivadosFinder;
import com.renaser.os.users.api.ProgramasActivadosFinder.ProgramaActivado;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.renaser.os.rag.application.services.analisis.CifrasDePrueba.LIMA;
import static com.renaser.os.rag.application.services.analisis.CifrasDePrueba.SEMANA_10;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * El barrido horario (spec 031, FR-005, FR-016, FR-018 y FR-019): decide por persona, con SU día, qué periodos que cerraron tocan
 * generarse, sin tocar lo ya decidido y sin que una persona que falla detenga a las demás. El reloj se fija en la madrugada UTC.
 */
@ExtendWith(MockitoExtension.class)
class BarridoDeAnalisis8020ServiceTest {

    private static final UserId ANA = UserId.of(UUID.fromString("00000000-0000-0000-0000-0000000000a1"));
    private static final UserId LUIS = UserId.of(UUID.fromString("00000000-0000-0000-0000-0000000000a2"));
    private static final LocalDate DIA_UNO = LocalDate.of(2026, 8, 17);
    private static final Instant LUNES_00_30_EN_LIMA = Instant.parse("2026-10-26T05:30:00Z");
    private static final Instant DOMINGO_23_00_EN_LIMA = Instant.parse("2026-10-26T04:00:00Z");
    private static final PeriodoDelPrograma MES_2 = new PeriodoDelPrograma(Tipo.MES, 2, LocalDate.of(2026, 9, 14), LocalDate.of(2026, 10, 11));

    @Mock
    private ParticipacionProgramaFinder participaciones;
    @Mock
    private ProgramasActivadosFinder programas;
    @Mock
    private PeriodosDelProgramaFinder periodos;
    @Mock
    private LoadAnalisisDelPeriodoPort decididos;
    @Mock
    private GenerarAnalisis8020UseCase generar;
    @Mock
    private InterruptoresDeSer interruptores;

    private BarridoDeAnalisis8020Service barrido(Instant ahora) {
        return new BarridoDeAnalisis8020Service(participaciones, programas, periodos, decididos, generar, interruptores,
                ReglasDelAnalisis.POR_DEFECTO, FixedClock.at(ahora));
    }

    private void elPadron(UserId... personas) {
        when(interruptores.analisisPrendido()).thenReturn(true);
        when(participaciones.participantesInscritosActivos()).thenReturn(List.of(personas));
        Map<UserId, ProgramaActivado> activados = new java.util.LinkedHashMap<>();
        for (UserId persona : personas) {
            activados.put(persona, new ProgramaActivado(persona, LIMA, DIA_UNO, DIA_UNO.plusDays(89)));
        }
        when(programas.deVarios(any())).thenReturn(activados);
    }

    @Test
    @DisplayName("FR-018: cuando para Ana ya cerró la semana (lunes 00:30 en Lima) la genera; antes (domingo 23:00 en Lima, lunes en UTC) no")
    void decidePorElDiaDeCadaPersona() {
        elPadron(ANA);
        when(decididos.estadosDe(any(), any())).thenReturn(List.of());
        when(periodos.cerradosHastaHoy(eq(DIA_UNO), eq(LocalDate.of(2026, 10, 26)), anyInt())).thenReturn(List.of(SEMANA_10));
        when(periodos.cerradosHastaHoy(eq(DIA_UNO), eq(LocalDate.of(2026, 10, 25)), anyInt())).thenReturn(List.of());

        barrido(DOMINGO_23_00_EN_LIMA).generarLosQueTocan();
        verifyNoInteractions(generar);

        barrido(LUNES_00_30_EN_LIMA).generarLosQueTocan();
        verify(generar).generar(ANA, TipoDePeriodo.SEMANA, SEMANA_10.inicio());
    }

    @Test
    @DisplayName("genera la semana y el mes que cerraron juntos, cada uno una vez")
    void semanaYMes() {
        elPadron(ANA);
        when(decididos.estadosDe(any(), any())).thenReturn(List.of());
        when(periodos.cerradosHastaHoy(any(), any(), anyInt())).thenReturn(List.of(SEMANA_10, MES_2));

        barrido(LUNES_00_30_EN_LIMA).generarLosQueTocan();

        verify(generar).generar(ANA, TipoDePeriodo.SEMANA, SEMANA_10.inicio());
        verify(generar).generar(ANA, TipoDePeriodo.MES, MES_2.inicio());
    }

    @Test
    @DisplayName("lo ya decidido (listo, sin datos, agotado) no se vuelve a pedir; un pendiente cuya espera pasó sí")
    void loDecididoNoSeToca() {
        elPadron(ANA);
        when(periodos.cerradosHastaHoy(any(), any(), anyInt())).thenReturn(List.of(SEMANA_10, MES_2));
        when(decididos.estadosDe(any(), any())).thenReturn(List.of(
                new EstadoDeUnPeriodo(ANA, TipoDePeriodo.SEMANA, SEMANA_10.inicio(), EstadoDelAnalisis.LISTO, LUNES_00_30_EN_LIMA),
                new EstadoDeUnPeriodo(ANA, TipoDePeriodo.MES, MES_2.inicio(), EstadoDelAnalisis.PENDIENTE,
                        LUNES_00_30_EN_LIMA.minusSeconds(7 * 3600))));

        barrido(LUNES_00_30_EN_LIMA).generarLosQueTocan();

        verify(generar, never()).generar(ANA, TipoDePeriodo.SEMANA, SEMANA_10.inicio());
        verify(generar).generar(ANA, TipoDePeriodo.MES, MES_2.inicio());
    }

    @Test
    @DisplayName("un pendiente reintentado hace una hora todavía espera")
    void pendienteEnEspera() {
        elPadron(ANA);
        when(periodos.cerradosHastaHoy(any(), any(), anyInt())).thenReturn(List.of(SEMANA_10));
        when(decididos.estadosDe(any(), any())).thenReturn(List.of(
                new EstadoDeUnPeriodo(ANA, TipoDePeriodo.SEMANA, SEMANA_10.inicio(), EstadoDelAnalisis.PENDIENTE,
                        LUNES_00_30_EN_LIMA.minusSeconds(3600))));

        barrido(LUNES_00_30_EN_LIMA).generarLosQueTocan();

        verifyNoInteractions(generar);
    }

    @Test
    @DisplayName("FR-019: con ia.analisis apagada el barrido no corre y no pide nada")
    void conLaFuncionalidadApagada() {
        when(interruptores.analisisPrendido()).thenReturn(false);

        barrido(LUNES_00_30_EN_LIMA).generarLosQueTocan();

        verifyNoInteractions(participaciones, programas, generar);
    }

    @Test
    @DisplayName("una persona que falla no detiene a las demás")
    void unaFallaNoDetieneElBarrido() {
        elPadron(ANA, LUIS);
        when(decididos.estadosDe(any(), any())).thenReturn(List.of());
        when(periodos.cerradosHastaHoy(any(), any(), anyInt())).thenReturn(List.of(SEMANA_10));
        when(generar.generar(ANA, TipoDePeriodo.SEMANA, SEMANA_10.inicio())).thenThrow(new IllegalStateException("se cayó"));

        barrido(LUNES_00_30_EN_LIMA).generarLosQueTocan();

        verify(generar).generar(LUIS, TipoDePeriodo.SEMANA, SEMANA_10.inicio());
    }

    @Test
    @DisplayName("pide los programas y lo decidido en una consulta por lote, no una por persona")
    void unaConsultaPorLote() {
        elPadron(ANA, LUIS);
        when(decididos.estadosDe(any(), any())).thenReturn(List.of());
        when(periodos.cerradosHastaHoy(any(), any(), anyInt())).thenReturn(List.of());

        barrido(LUNES_00_30_EN_LIMA).generarLosQueTocan();

        verify(programas).deVarios(any());
        verify(decididos).estadosDe(any(), any());
    }
}

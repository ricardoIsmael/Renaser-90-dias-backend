package com.renaser.os.rag.application.services.analisis;

import com.renaser.os.rag.api.Analisis8020Finder;
import com.renaser.os.rag.api.Analisis8020Finder.AnalisisLeido;
import com.renaser.os.rag.api.Analisis8020Finder.ResumenDeSemana;
import com.renaser.os.rag.api.Analisis8020Finder.Tipo;
import com.renaser.os.rag.api.Analisis8020Finder.VistaDelAnalisis;
import com.renaser.os.rag.application.ports.in.analisis.ConsultarAnalisis8020UseCase.ConsultaDeAnalisis;
import com.renaser.os.rag.application.ports.out.analisis.LoadAnalisisDelPeriodoPort;
import com.renaser.os.rag.domain.model.analisis.AnalisisDelPeriodo;
import com.renaser.os.rag.domain.model.analisis.AnalisisDelPeriodoId;
import com.renaser.os.rag.domain.model.analisis.EstadoDelAnalisis;
import com.renaser.os.rag.domain.model.analisis.ReglasDelAnalisis;
import com.renaser.os.rag.domain.model.analisis.TextoDelAnalisis;
import com.renaser.os.rag.domain.model.analisis.TipoDePeriodo;
import com.renaser.os.rocks.api.PeriodosDelProgramaFinder;
import com.renaser.os.rocks.api.PeriodosDelProgramaFinder.PeriodoDelPrograma;
import com.renaser.os.shared.domain.FixedClock;
import com.renaser.os.shared.domain.Formacion;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.FasePrograma;
import com.renaser.os.users.api.ParticipacionPrograma;
import com.renaser.os.users.api.ParticipacionProgramaFinder;
import com.renaser.os.users.api.ProgramasActivadosFinder;
import com.renaser.os.users.api.ProgramasActivadosFinder.ProgramaActivado;
import com.renaser.os.users.api.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

import static com.renaser.os.rag.application.services.analisis.CifrasDePrueba.LIMA;
import static com.renaser.os.rag.application.services.analisis.CifrasDePrueba.SEMANA_10;
import static com.renaser.os.rag.application.services.analisis.CifrasDePrueba.cifras;
import static com.renaser.os.rag.application.services.analisis.CifrasDePrueba.semanaDeDoce;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Lo que se lee del 80/20 (spec 031): cifras al día mientras el periodo está abierto; al cerrar, el análisis si ya está o el motivo por el
 * que todavía no. Siempre lectura pura (FR-006) y siempre en la formación de la persona hoy (FR-022).
 */
@ExtendWith(MockitoExtension.class)
class ConsultaDelAnalisis8020ServiceTest {

    private static final UserId ANA = UserId.of(UUID.fromString("00000000-0000-0000-0000-0000000000a1"));
    private static final LocalDate DIA_UNO = LocalDate.of(2026, 8, 17);
    private static final PeriodoDelPrograma SEMANA_9 = new PeriodoDelPrograma(PeriodosDelProgramaFinder.Tipo.SEMANA, 9,
            LocalDate.of(2026, 10, 12), LocalDate.of(2026, 10, 18));
    private static final PeriodoDelPrograma SEMANA_11 = new PeriodoDelPrograma(PeriodosDelProgramaFinder.Tipo.SEMANA, 11,
            LocalDate.of(2026, 10, 26), LocalDate.of(2026, 11, 1));
    private static final Instant MIERCOLES_21 = Instant.parse("2026-10-21T15:00:00Z");
    private static final Instant LUNES_26 = Instant.parse("2026-10-26T15:00:00Z");

    @Mock
    private ParticipacionProgramaFinder participaciones;
    @Mock
    private ProgramasActivadosFinder programas;
    @Mock
    private PeriodosDelProgramaFinder periodos;
    @Mock
    private LoadAnalisisDelPeriodoPort analisis;
    @Mock
    private ReunirInsumosDelAnalisis insumos;

    private static ParticipacionPrograma participacion(Formacion formacion, boolean activado) {
        return new ParticipacionPrograma(ANA, true, activado ? 66 : 0, DIA_UNO, LIMA, FasePrograma.PHASE_3_ALCHEMIST_WARRIOR, null, null,
                UserRole.TRAINEE, false, activado, formacion, 90);
    }

    private Analisis8020Finder servicio(Instant ahora) {
        return new ConsultaDelAnalisis8020Service(participaciones, programas, periodos, analisis, insumos, ReglasDelAnalisis.POR_DEFECTO,
                FixedClock.at(ahora));
    }

    @BeforeEach
    void anaEnLaEscuela() {
        lenient().when(participaciones.deParticipante(ANA)).thenReturn(Optional.of(participacion(Formacion.ESCUELA, true)));
        lenient().when(programas.de(ANA)).thenReturn(Optional.of(new ProgramaActivado(ANA, LIMA, DIA_UNO, DIA_UNO.plusDays(89))));
        lenient().when(periodos.semanaDe(DIA_UNO, 90, LocalDate.of(2026, 10, 21))).thenReturn(SEMANA_10);
        lenient().when(periodos.semanaDe(DIA_UNO, 90, LocalDate.of(2026, 10, 26))).thenReturn(SEMANA_11);
        lenient().when(periodos.queEmpiezaEn(DIA_UNO, 90, PeriodosDelProgramaFinder.Tipo.SEMANA, SEMANA_10.inicio())).thenReturn(Optional.of(SEMANA_10));
        lenient().when(periodos.anteriorA(DIA_UNO, 90, SEMANA_10)).thenReturn(Optional.of(SEMANA_9));
        lenient().when(periodos.anteriorA(DIA_UNO, 90, SEMANA_11)).thenReturn(Optional.of(SEMANA_10));
        lenient().when(periodos.siguienteA(any(), anyInt(), any(), any())).thenReturn(Optional.empty());
        lenient().when(periodos.siguienteA(DIA_UNO, 90, SEMANA_10, LocalDate.of(2026, 10, 26))).thenReturn(Optional.of(SEMANA_11));
    }

    private static AnalisisDelPeriodo fila(Formacion formacion, EstadoDelAnalisis estado) {
        TextoDelAnalisis texto = estado == EstadoDelAnalisis.LISTO
                ? new TextoDelAnalisis("Entrenar movió más.", "La llamada quedó pendiente.", "Bajas por las tardes.", List.of("Haz la llamada primero."))
                : null;
        String foto = estado == EstadoDelAnalisis.LISTO
                ? JsonDelAnalisis.escribir(new SnapshotDelAnalisis(semanaDeDoce(), List.of(), null)) : null;
        return AnalisisDelPeriodo.rehydrate(AnalisisDelPeriodoId.of(UUID.randomUUID()), ANA, formacion, TipoDePeriodo.SEMANA, 10,
                SEMANA_10.inicio(), SEMANA_10.fin(), estado, estado == EstadoDelAnalisis.AGOTADO ? 3 : 1, Instant.parse("2026-10-26T06:00:00Z"),
                estado == EstadoDelAnalisis.LISTO ? Instant.parse("2026-10-26T06:00:00Z") : null, Instant.parse("2026-10-26T06:00:00Z"), texto, foto);
    }

    @Test
    @DisplayName("FR-003 / US1.3: mientras la semana está abierta se ven las cifras al día, sin análisis y sin buscar nada guardado")
    void semanaEnCurso() {
        when(insumos.cifrasDe(ANA, SEMANA_10)).thenReturn(semanaDeDoce());

        VistaDelAnalisis vista = servicio(MIERCOLES_21).de(ANA, Tipo.SEMANA, null);

        assertThat(vista.estado()).isEqualTo("EN_CURSO");
        assertThat(vista.cerrado()).isFalse();
        assertThat(vista.numero()).isEqualTo(10);
        assertThat(vista.cifras().planificadas()).isEqualTo(12);
        assertThat(vista.analisis()).isNull();
        assertThat(vista.anterior()).isEqualTo(SEMANA_9.inicio());
        assertThat(vista.siguiente()).isNull();
        assertThat(vista.falta()).isEmpty();
        verify(analisis, never()).deLaPersona(any(), any(), any());
    }

    @Test
    @DisplayName("FR-006: un análisis listo se lee con la foto de cifras que se guardó con él, no con las de ahora")
    void analisisListo() {
        when(analisis.deLaPersona(ANA, TipoDePeriodo.SEMANA, SEMANA_10.inicio())).thenReturn(Optional.of(fila(Formacion.ESCUELA, EstadoDelAnalisis.LISTO)));

        VistaDelAnalisis vista = servicio(LUNES_26).de(ANA, Tipo.SEMANA, SEMANA_10.inicio());

        assertThat(vista.estado()).isEqualTo("LISTO");
        assertThat(vista.cerrado()).isTrue();
        assertThat(vista.cifras()).isEqualTo(semanaDeDoce());
        assertThat(vista.analisis().loQueMasMovio()).isEqualTo("Entrenar movió más.");
        assertThat(vista.analisis().proximosPasos()).containsExactly("Haz la llamada primero.");
        assertThat(vista.generadoEn()).isEqualTo(Instant.parse("2026-10-26T06:00:00Z"));
        assertThat(vista.siguiente()).isEqualTo(SEMANA_11.inicio());
        verify(insumos, never()).cifrasDe(any(), any());
    }

    @Test
    @DisplayName("cerró con datos y todavía no hay análisis: «llega pronto» (US3.2)")
    void pendiente() {
        when(analisis.deLaPersona(any(), any(), any())).thenReturn(Optional.empty());
        when(insumos.cifrasDe(ANA, SEMANA_10)).thenReturn(semanaDeDoce());

        VistaDelAnalisis vista = servicio(LUNES_26).de(ANA, Tipo.SEMANA, SEMANA_10.inicio());

        assertThat(vista.estado()).isEqualTo("PENDIENTE");
        assertThat(vista.analisis()).isNull();
        assertThat(vista.cifras().planificadas()).isEqualTo(12);
    }

    @Test
    @DisplayName("US3.1: cerró sin datos suficientes: lo dice con lo que falta y cuántas acciones hacen falta, sin inventar un análisis")
    void sinDatosSuficientes() {
        when(analisis.deLaPersona(any(), any(), any())).thenReturn(Optional.empty());
        when(insumos.cifrasDe(ANA, SEMANA_10)).thenReturn(cifras(2, 1, 1));

        VistaDelAnalisis vista = servicio(LUNES_26).de(ANA, Tipo.SEMANA, SEMANA_10.inicio());

        assertThat(vista.estado()).isEqualTo("SIN_DATOS");
        assertThat(vista.falta()).containsExactly("ACCIONES", "DIAS");
        assertThat(vista.minimoDeAcciones()).isEqualTo(5);
        assertThat(vista.analisis()).isNull();
    }

    @Test
    @DisplayName("US3.4: se acabaron los intentos: el periodo queda sin análisis, con sus cifras")
    void agotado() {
        when(analisis.deLaPersona(any(), any(), any())).thenReturn(Optional.of(fila(Formacion.ESCUELA, EstadoDelAnalisis.AGOTADO)));
        when(insumos.cifrasDe(ANA, SEMANA_10)).thenReturn(semanaDeDoce());

        VistaDelAnalisis vista = servicio(LUNES_26).de(ANA, Tipo.SEMANA, SEMANA_10.inicio());

        assertThat(vista.estado()).isEqualTo("SIN_ANALISIS");
        assertThat(vista.cifras().cumplidas()).isEqualTo(9);
    }

    @Test
    @DisplayName("FR-022: lo que quedó de otra formación no se muestra: nunca se mezclan datos de dos formaciones")
    void soloLaFormacionDeHoy() {
        when(analisis.deLaPersona(any(), any(), any())).thenReturn(Optional.of(fila(Formacion.DIPLOMADO, EstadoDelAnalisis.LISTO)));
        when(insumos.cifrasDe(ANA, SEMANA_10)).thenReturn(semanaDeDoce());

        VistaDelAnalisis vista = servicio(LUNES_26).de(ANA, Tipo.SEMANA, SEMANA_10.inicio());

        assertThat(vista.estado()).isEqualTo("PENDIENTE");
        assertThat(vista.analisis()).isNull();
    }

    @Test
    @DisplayName("quien todavía no activó su programa no tiene periodos: 404, no un periodo inventado")
    void sinDiaUno() {
        when(participaciones.deParticipante(ANA)).thenReturn(Optional.of(participacion(Formacion.ESCUELA, false)));

        // issue #62: ya no se elige el Día 1; el 404 dice lo que pasa de verdad (el programa no está activado).
        assertThatThrownBy(() -> servicio(MIERCOLES_21).de(ANA, Tipo.SEMANA, null)).isInstanceOf(NoSuchElementException.class)
                .hasMessage("Todavía no activó su programa").hasMessageNotContaining("Día 1");
    }

    @Test
    @DisplayName("un inicio que no es el de ningún periodo, o de un periodo que todavía no llega, es un pedido mal hecho (400)")
    void inicioInvalido() {
        when(periodos.queEmpiezaEn(DIA_UNO, 90, PeriodosDelProgramaFinder.Tipo.SEMANA, LocalDate.of(2026, 10, 20))).thenReturn(Optional.empty());
        when(periodos.queEmpiezaEn(DIA_UNO, 90, PeriodosDelProgramaFinder.Tipo.SEMANA, SEMANA_11.inicio())).thenReturn(Optional.of(SEMANA_11));

        assertThatThrownBy(() -> servicio(MIERCOLES_21).de(ANA, Tipo.SEMANA, LocalDate.of(2026, 10, 20))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> servicio(MIERCOLES_21).de(ANA, Tipo.SEMANA, SEMANA_11.inicio())).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("un mes en curso muestra solo las semanas que ya empezaron")
    void mesEnCurso() {
        PeriodoDelPrograma mes3 = new PeriodoDelPrograma(PeriodosDelProgramaFinder.Tipo.MES, 3, LocalDate.of(2026, 10, 12), LocalDate.of(2026, 11, 14));
        PeriodoDelPrograma semana12 = new PeriodoDelPrograma(PeriodosDelProgramaFinder.Tipo.SEMANA, 12, LocalDate.of(2026, 11, 2), LocalDate.of(2026, 11, 8));
        when(periodos.mesDe(DIA_UNO, 90, LocalDate.of(2026, 10, 21))).thenReturn(mes3);
        when(periodos.anteriorA(DIA_UNO, 90, mes3)).thenReturn(Optional.empty());
        when(periodos.semanasDelMes(DIA_UNO, 90, mes3)).thenReturn(List.of(SEMANA_9, SEMANA_10, SEMANA_11, semana12));
        when(insumos.cifrasDe(ANA, mes3)).thenReturn(cifras(30, 20, 9));
        when(insumos.resumenesDe(ANA, List.of(SEMANA_9, SEMANA_10))).thenReturn(List.of(new ResumenDeSemana(9, 10, 8, 80, 50),
                new ResumenDeSemana(10, 12, 9, 75, 55)));

        VistaDelAnalisis vista = servicio(MIERCOLES_21).de(ANA, Tipo.MES, null);

        assertThat(vista.tipo()).isEqualTo(Tipo.MES);
        assertThat(vista.estado()).isEqualTo("EN_CURSO");
        assertThat(vista.semanas()).extracting(ResumenDeSemana::numero).containsExactly(9, 10);
        assertThat(vista.anterior()).isNull();
    }

    @Test
    @DisplayName("SER lee el más reciente que está listo, en la formación de la persona")
    void ultimoListo() {
        when(analisis.ultimoListo(ANA, Formacion.ESCUELA, TipoDePeriodo.SEMANA)).thenReturn(Optional.of(fila(Formacion.ESCUELA, EstadoDelAnalisis.LISTO)));
        when(analisis.ultimoListo(ANA, Formacion.ESCUELA, TipoDePeriodo.MES)).thenReturn(Optional.empty());

        Optional<AnalisisLeido> semana = servicio(LUNES_26).ultimoListo(ANA, Tipo.SEMANA);

        assertThat(semana).get().satisfies(leido -> {
            assertThat(leido.numero()).isEqualTo(10);
            assertThat(leido.texto().comoEstuviste()).isEqualTo("Bajas por las tardes.");
        });
        assertThat(servicio(LUNES_26).ultimoListo(ANA, Tipo.MES)).isEmpty();
    }

    @Test
    @DisplayName("la propia persona usa la misma lectura, siempre sobre sí misma")
    void deLaPersona() {
        when(insumos.cifrasDe(ANA, SEMANA_10)).thenReturn(semanaDeDoce());

        VistaDelAnalisis vista = ((ConsultaDelAnalisis8020Service) servicio(MIERCOLES_21))
                .deLaPersona(new ConsultaDeAnalisis(ANA, TipoDePeriodo.SEMANA, null));

        assertThat(vista.estado()).isEqualTo("EN_CURSO");
    }
}

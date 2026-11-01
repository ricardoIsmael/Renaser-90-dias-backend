package com.renaser.os.rag.application.services.analisis;

import com.renaser.os.habits.api.RespuestasDelRadarFinder.RespuestaDelRadar;
import com.renaser.os.rag.application.ports.in.analisis.GenerarAnalisis8020UseCase.ResultadoDeLaGeneracion;
import com.renaser.os.rag.application.ports.out.analisis.GenerarAnalisis8020Port;
import com.renaser.os.rag.application.ports.out.analisis.GenerarAnalisis8020Port.PedidoDeAnalisis;
import com.renaser.os.rag.application.ports.out.analisis.LoadAnalisisDelPeriodoPort;
import com.renaser.os.rag.application.services.analisis.QuienPuedeTenerAnalisis.ParticipanteAnalizable;
import com.renaser.os.rag.domain.model.analisis.AnalisisDelPeriodo;
import com.renaser.os.rag.domain.model.analisis.AnalisisDelPeriodoId;
import com.renaser.os.rag.domain.model.analisis.EstadoDelAnalisis;
import com.renaser.os.rag.domain.model.analisis.ReglasDelAnalisis;
import com.renaser.os.rag.domain.model.analisis.TextoDelAnalisis;
import com.renaser.os.rag.domain.model.analisis.TipoDePeriodo;
import com.renaser.os.rocks.api.PeriodosDelProgramaFinder;
import com.renaser.os.rocks.api.PeriodosDelProgramaFinder.Tipo;
import com.renaser.os.shared.domain.FixedClock;
import com.renaser.os.shared.domain.Formacion;
import com.renaser.os.shared.domain.IdGenerator;
import com.renaser.os.shared.domain.ProveedorIaNoDisponibleException;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.renaser.os.rag.application.services.analisis.CifrasDePrueba.LIMA;
import static com.renaser.os.rag.application.services.analisis.CifrasDePrueba.SEMANA_10;
import static com.renaser.os.rag.application.services.analisis.CifrasDePrueba.cifras;
import static com.renaser.os.rag.application.services.analisis.CifrasDePrueba.insumos;
import static com.renaser.os.rag.application.services.analisis.CifrasDePrueba.respuesta;
import static com.renaser.os.rag.application.services.analisis.CifrasDePrueba.semanaDeDoce;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Lo que decide la generación del análisis 80/20 (spec 031): una sola vez, sin llamar a la IA si no hay datos suficientes, sin guardar un
 * texto que no se puede mostrar, con reintentos acotados y sin perder nada cuando la IA está apagada o caída. Las lecturas y la IA son
 * dobles; el reloj se fija en la madrugada UTC, que es el día local anterior en Lima (regla 02 §3).
 */
@ExtendWith(MockitoExtension.class)
class GeneracionDeAnalisis8020ServiceTest {

    private static final UserId ANA = UserId.of(UUID.fromString("00000000-0000-0000-0000-0000000000a1"));
    private static final LocalDate DIA_UNO = LocalDate.of(2026, 8, 17);
    /** Lunes 26 de octubre, 00:30 en Lima (05:30 UTC): la semana 10 (19 al 25) acaba de cerrar. */
    private static final Instant CERRO = Instant.parse("2026-10-26T05:30:00Z");
    /** Domingo 25 de octubre, 23:00 en Lima (04:00 UTC del lunes): en UTC ya es lunes, para Ana todavía no cerró. */
    private static final Instant ANTES_DE_CERRAR = Instant.parse("2026-10-26T04:00:00Z");
    private static final TextoDelAnalisis BUENO = new TextoDelAnalisis(
            "Cumpliste 9 de 12 acciones, el 75 %. Entrenar fue lo que más movió.", "Llamar al cliente grande quedó pendiente.",
            "Tu energía baja por las tardes.", List.of("Haz primero la llamada al cliente."));

    @Mock
    private LoadAnalisisDelPeriodoPort analisis;
    @Mock
    private QuienPuedeTenerAnalisis quien;
    @Mock
    private PeriodosDelProgramaFinder periodos;
    @Mock
    private ReunirInsumosDelAnalisis reunir;
    @Mock
    private PuertasDeLaIa puertas;
    @Mock
    private GenerarAnalisis8020Port ia;
    @Mock
    private RegistroDelAnalisis registro;

    private final IdGenerator ids = UUID::randomUUID;

    private GeneracionDeAnalisis8020Service servicio(Instant ahora) {
        return new GeneracionDeAnalisis8020Service(analisis, quien, periodos, reunir, puertas, ia, registro, ReglasDelAnalisis.POR_DEFECTO,
                FixedClock.at(ahora), ids);
    }

    @BeforeEach
    void unaPersonaConSuSemanaCerrada() {
        org.mockito.Mockito.lenient().when(analisis.deLaPersona(ANA, TipoDePeriodo.SEMANA, SEMANA_10.inicio())).thenReturn(Optional.empty());
        org.mockito.Mockito.lenient().when(quien.de(ANA)).thenReturn(Optional.of(new ParticipanteAnalizable(ANA, Formacion.ESCUELA, LIMA, DIA_UNO, 90)));
        org.mockito.Mockito.lenient().when(periodos.queEmpiezaEn(DIA_UNO, 90, Tipo.SEMANA, SEMANA_10.inicio())).thenReturn(Optional.of(SEMANA_10));
        org.mockito.Mockito.lenient().when(registro.registrar(any())).thenReturn(true);
    }

    private void conDatosSuficientes() {
        List<RespuestaDelRadar> respuestas = List.of(respuesta(Instant.parse("2026-10-20T14:00:00Z"), 8, "el correo"),
                respuesta(Instant.parse("2026-10-20T20:00:00Z"), 4, "el celular"), respuesta(Instant.parse("2026-10-21T20:00:00Z"), 5, "x"));
        when(reunir.reunir(any(), any(), any(), anyInt(), any(), any())).thenReturn(insumos(semanaDeDoce(), respuestas));
    }

    private void conLaIaAbierta() {
        when(puertas.estanAbiertas()).thenReturn(true);
        when(puertas.pedirPaso()).thenReturn(true);
    }

    private AnalisisDelPeriodo loQueSeGuardo() {
        ArgumentCaptor<AnalisisDelPeriodo> captura = ArgumentCaptor.forClass(AnalisisDelPeriodo.class);
        verify(registro).registrar(captura.capture());
        return captura.getValue();
    }

    private AnalisisDelPeriodo pendienteConIntentos(int intentos, Instant ultimoIntento) {
        AnalisisDelPeriodo pendiente = AnalisisDelPeriodo.pendiente(AnalisisDelPeriodoId.of(UUID.randomUUID()), ANA, Formacion.ESCUELA,
                TipoDePeriodo.SEMANA, 10, SEMANA_10.inicio(), SEMANA_10.fin(), FixedClock.at(ultimoIntento));
        for (int i = 0; i < intentos; i++) {
            pendiente.fallar(ReglasDelAnalisis.POR_DEFECTO, FixedClock.at(ultimoIntento));
        }
        return pendiente;
    }

    @Test
    @DisplayName("al cerrar la semana con datos suficientes genera el análisis una vez, lo guarda con las cifras que cita y queda listo")
    void generaYGuarda() {
        conDatosSuficientes();
        conLaIaAbierta();
        when(ia.generar(any())).thenReturn(BUENO);

        ResultadoDeLaGeneracion resultado = servicio(CERRO).generar(ANA, TipoDePeriodo.SEMANA, SEMANA_10.inicio());

        assertThat(resultado).isEqualTo(ResultadoDeLaGeneracion.LISTO);
        AnalisisDelPeriodo guardado = loQueSeGuardo();
        assertThat(guardado.estado()).isEqualTo(EstadoDelAnalisis.LISTO);
        assertThat(guardado.texto()).isEqualTo(BUENO);
        assertThat(guardado.formacion()).isEqualTo(Formacion.ESCUELA);
        assertThat(guardado.cifrasEnJson()).contains("\"planificadas\":12").contains("\"cumplidas\":9");
        verify(puertas).anotarQueRespondio();
    }

    @Test
    @DisplayName("FR-009: al proveedor viaja el periodo con las cifras ya calculadas, y nunca el correo ni el id de la persona")
    void loQueViajaAlProveedor() {
        conDatosSuficientes();
        conLaIaAbierta();
        when(ia.generar(any())).thenReturn(BUENO);

        servicio(CERRO).generar(ANA, TipoDePeriodo.SEMANA, SEMANA_10.inicio());

        ArgumentCaptor<PedidoDeAnalisis> pedido = ArgumentCaptor.forClass(PedidoDeAnalisis.class);
        verify(ia).generar(pedido.capture());
        assertThat(pedido.getValue().tipo()).isEqualTo(TipoDePeriodo.SEMANA);
        assertThat(pedido.getValue().datosEnJson()).contains("\"planificadas\":12").doesNotContain(ANA.value().toString());
    }

    @Test
    @DisplayName("FR-015 / SC-008: una semana con menos del mínimo se anota como «sin datos» y NO se llama a la IA")
    void sinDatosSuficientes() {
        when(reunir.reunir(any(), any(), any(), anyInt(), any(), any())).thenReturn(insumos(cifras(4, 3, 3), List.of()));

        ResultadoDeLaGeneracion resultado = servicio(CERRO).generar(ANA, TipoDePeriodo.SEMANA, SEMANA_10.inicio());

        assertThat(resultado).isEqualTo(ResultadoDeLaGeneracion.SIN_DATOS);
        assertThat(loQueSeGuardo().estado()).isEqualTo(EstadoDelAnalisis.SIN_DATOS);
        verifyNoInteractions(ia);
        verify(puertas, never()).pedirPaso();
    }

    @Test
    @DisplayName("FR-016: si la IA está caída el periodo queda pendiente con un intento, y el circuito lo sabe")
    void iaCaida() {
        conDatosSuficientes();
        conLaIaAbierta();
        when(ia.generar(any())).thenThrow(new ProveedorIaNoDisponibleException("caída", Duration.ofSeconds(10)));

        ResultadoDeLaGeneracion resultado = servicio(CERRO).generar(ANA, TipoDePeriodo.SEMANA, SEMANA_10.inicio());

        assertThat(resultado).isEqualTo(ResultadoDeLaGeneracion.FALLO);
        AnalisisDelPeriodo guardado = loQueSeGuardo();
        assertThat(guardado.estado()).isEqualTo(EstadoDelAnalisis.PENDIENTE);
        assertThat(guardado.intentos()).isEqualTo(1);
        assertThat(guardado.texto()).isNull();
        verify(puertas).anotarQueFallo();
    }

    @Test
    @DisplayName("FR-009 / US3.5: un texto con una cifra que no es la calculada no se guarda ni se muestra: cuenta como un intento fallido")
    void textoConCifraAjena() {
        conDatosSuficientes();
        conLaIaAbierta();
        when(ia.generar(any())).thenReturn(new TextoDelAnalisis("Cumpliste 11 de 12 acciones.", "Nada.", "Bien.", List.of("Sigue.")));

        ResultadoDeLaGeneracion resultado = servicio(CERRO).generar(ANA, TipoDePeriodo.SEMANA, SEMANA_10.inicio());

        assertThat(resultado).isEqualTo(ResultadoDeLaGeneracion.FALLO);
        AnalisisDelPeriodo guardado = loQueSeGuardo();
        assertThat(guardado.estado()).isEqualTo(EstadoDelAnalisis.PENDIENTE);
        assertThat(guardado.texto()).isNull();
        assertThat(guardado.intentos()).isEqualTo(1);
    }

    @Test
    @DisplayName("un diagnóstico tampoco se muestra")
    void textoConDiagnostico() {
        conDatosSuficientes();
        conLaIaAbierta();
        when(ia.generar(any())).thenReturn(new TextoDelAnalisis("Cumpliste 9 de 12 acciones.", "Pasa.", "Parece depresión.", List.of("Sigue.")));

        assertThat(servicio(CERRO).generar(ANA, TipoDePeriodo.SEMANA, SEMANA_10.inicio())).isEqualTo(ResultadoDeLaGeneracion.FALLO);
        assertThat(loQueSeGuardo().texto()).isNull();
    }

    @Test
    @DisplayName("una respuesta que no es un análisis legible es un intento fallido, y el proveedor sí respondió")
    void respuestaIlegible() {
        conDatosSuficientes();
        conLaIaAbierta();
        when(ia.generar(any())).thenThrow(new IllegalStateException("no es JSON"));

        assertThat(servicio(CERRO).generar(ANA, TipoDePeriodo.SEMANA, SEMANA_10.inicio())).isEqualTo(ResultadoDeLaGeneracion.FALLO);
        verify(puertas).anotarQueRespondio();
        verify(puertas, never()).anotarQueFallo();
    }

    @Test
    @DisplayName("FR-016: el tercer intento fallido deja el periodo sin análisis, y el sistema no sigue intentando")
    void seAcabanLosIntentos() {
        Instant hace7Horas = CERRO.minus(Duration.ofHours(7));
        when(analisis.deLaPersona(ANA, TipoDePeriodo.SEMANA, SEMANA_10.inicio())).thenReturn(Optional.of(pendienteConIntentos(2, hace7Horas)));
        conDatosSuficientes();
        conLaIaAbierta();
        when(ia.generar(any())).thenThrow(new ProveedorIaNoDisponibleException("caída", Duration.ofSeconds(10)));

        servicio(CERRO).generar(ANA, TipoDePeriodo.SEMANA, SEMANA_10.inicio());

        assertThat(loQueSeGuardo().estado()).isEqualTo(EstadoDelAnalisis.AGOTADO);
    }

    @Test
    @DisplayName("FR-017 / FR-019: con la IA apagada, el circuito abierto o el tope del día alcanzado queda pendiente y NO cuenta como intento")
    void puertasCerradas() {
        conDatosSuficientes();
        when(puertas.estanAbiertas()).thenReturn(false);

        assertThat(servicio(CERRO).generar(ANA, TipoDePeriodo.SEMANA, SEMANA_10.inicio())).isEqualTo(ResultadoDeLaGeneracion.DIFERIDO);

        verifyNoInteractions(ia);
        verify(registro, never()).registrar(any());
    }

    @Test
    @DisplayName("el circuito medio abierto que no deja pasar este pedido tampoco gasta un intento")
    void circuitoNoDejaPasar() {
        conDatosSuficientes();
        when(puertas.estanAbiertas()).thenReturn(true);
        when(puertas.pedirPaso()).thenReturn(false);

        assertThat(servicio(CERRO).generar(ANA, TipoDePeriodo.SEMANA, SEMANA_10.inicio())).isEqualTo(ResultadoDeLaGeneracion.DIFERIDO);

        verifyNoInteractions(ia);
        verify(registro, never()).registrar(any());
    }

    @Test
    @DisplayName("FR-006 / SC-003: un periodo que ya tiene su análisis no se vuelve a generar")
    void yaListo() {
        AnalisisDelPeriodo listo = pendienteConIntentos(0, CERRO);
        listo.completar(BUENO, "{}", FixedClock.at(CERRO));
        when(analisis.deLaPersona(ANA, TipoDePeriodo.SEMANA, SEMANA_10.inicio())).thenReturn(Optional.of(listo));

        assertThat(servicio(CERRO.plus(Duration.ofHours(3))).generar(ANA, TipoDePeriodo.SEMANA, SEMANA_10.inicio()))
                .isEqualTo(ResultadoDeLaGeneracion.YA_DECIDIDO);

        verifyNoInteractions(ia, reunir, registro);
    }

    @Test
    @DisplayName("un pendiente no se reintenta antes de la espera entre intentos")
    void esperaEntreIntentos() {
        when(analisis.deLaPersona(ANA, TipoDePeriodo.SEMANA, SEMANA_10.inicio()))
                .thenReturn(Optional.of(pendienteConIntentos(1, CERRO.minus(Duration.ofHours(1)))));

        assertThat(servicio(CERRO).generar(ANA, TipoDePeriodo.SEMANA, SEMANA_10.inicio())).isEqualTo(ResultadoDeLaGeneracion.YA_DECIDIDO);

        verifyNoInteractions(ia, reunir);
    }

    @Test
    @DisplayName("FR-018: con el reloj en la madrugada UTC, el domingo 23:00 de Lima la semana todavía no cerró")
    void madrugadaUtc() {
        assertThat(servicio(ANTES_DE_CERRAR).generar(ANA, TipoDePeriodo.SEMANA, SEMANA_10.inicio())).isEqualTo(ResultadoDeLaGeneracion.NO_APLICA);

        verifyNoInteractions(ia, reunir, registro);
    }

    @Test
    @DisplayName("una cuenta suspendida, cerrada o de prueba de «ver como», o sin programa, no tiene análisis")
    void cuentaQueNoAplica() {
        when(quien.de(ANA)).thenReturn(Optional.empty());

        assertThat(servicio(CERRO).generar(ANA, TipoDePeriodo.SEMANA, SEMANA_10.inicio())).isEqualTo(ResultadoDeLaGeneracion.NO_APLICA);

        verifyNoInteractions(ia, reunir, registro);
    }

    @Test
    @DisplayName("un periodo que no existe en el programa de la persona no genera nada")
    void periodoQueNoExiste() {
        when(periodos.queEmpiezaEn(DIA_UNO, 90, Tipo.SEMANA, SEMANA_10.inicio())).thenReturn(Optional.empty());

        assertThat(servicio(CERRO).generar(ANA, TipoDePeriodo.SEMANA, SEMANA_10.inicio())).isEqualTo(ResultadoDeLaGeneracion.NO_APLICA);

        verifyNoInteractions(ia, reunir, registro);
    }
}

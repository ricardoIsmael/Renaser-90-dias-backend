package com.renaser.os.rocks.application.services;

import com.renaser.os.habits.api.EvaluacionDelDiaPort;
import com.renaser.os.rocks.api.CierresDelDiaFinder.DiaDelCierre;
import com.renaser.os.rocks.application.ports.in.cierre.CerrarElDiaUseCase.CerrarElDia;
import com.renaser.os.rocks.application.ports.in.cierre.CerrarElDiaUseCase.RespuestaDeAccion;
import com.renaser.os.rocks.application.ports.in.cierre.ConsultarCierreDelDiaUseCase;
import com.renaser.os.rocks.application.ports.in.cierre.ConsultarCierreDelDiaUseCase.VistaDelCierre;
import com.renaser.os.rocks.application.ports.out.participante.ConsultarProgresoParticipanteRocksPort;
import com.renaser.os.rocks.application.ports.out.participante.ConsultarProgresoParticipanteRocksPort.ProgresoParticipanteRocks;
import com.renaser.os.rocks.application.ports.out.participante.ConsultarProgresoParticipanteRocksPort.RolParticipante;
import com.renaser.os.rocks.application.ports.out.rocadiaria.LoadRocaDiariaPort;
import com.renaser.os.rocks.application.ports.out.rocadiaria.SaveRocaDiariaPort;
import com.renaser.os.rocks.domain.model.cierre.CierreDeAccion;
import com.renaser.os.rocks.domain.model.rocadiaria.RocaDiaria;
import com.renaser.os.shared.application.EntregaSinRedDePrueba;
import com.renaser.os.shared.domain.CodigoNoAplicado;
import com.renaser.os.shared.domain.FixedClock;
import com.renaser.os.shared.domain.HechoSinRed;
import com.renaser.os.shared.domain.NoAplicadoSinRedException;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.OrigenDeLaHora;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static com.renaser.os.rocks.domain.model.cierre.RocasDeCierreDePrueba.ANA;
import static com.renaser.os.rocks.domain.model.cierre.RocasDeCierreDePrueba.conCierre;
import static com.renaser.os.rocks.domain.model.cierre.RocasDeCierreDePrueba.cumplida;
import static com.renaser.os.rocks.domain.model.cierre.RocasDeCierreDePrueba.pendiente;
import static com.renaser.os.rocks.domain.model.cierre.RocasDeCierreDePrueba.pendienteCreadaEn;
import static com.renaser.os.rocks.domain.model.cierre.RocasDeCierreDePrueba.respuesta;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * «Cierra tu día» hecho sin red (spec 048, fase 3, FR-019): las reglas de siempre con la hora real y la regla de choques. La persona
 * cierra su lunes 9 a las 21:40 de Lima (02:40 UTC del 10) y la app lo envía el martes 10 a las 08:00 de Lima: en UTC los dos
 * instantes caen el mismo día calendario, en Lima no (regla 02).
 */
class CierreDelDiaSinRedServiceTest {

    private static final ZoneId LIMA = ZoneId.of("America/Lima");
    private static final LocalDate EL_9 = LocalDate.of(2026, 11, 9);
    private static final Instant HECHO = Instant.parse("2026-11-10T02:40:00Z");
    private static final Instant LLEGA = Instant.parse("2026-11-10T13:00:00Z");
    private static final UUID ENVIO = UUID.fromString("5f0c1f4e-2d0a-4b7e-9d77-0c9f3f6b1a10");
    private static final HechoSinRed SIN_RED = new HechoSinRed(HECHO, ENVIO, OrigenDeLaHora.RELOJ_DEL_SISTEMA);

    private final ConsultarProgresoParticipanteRocksPort progreso = mock(ConsultarProgresoParticipanteRocksPort.class);
    private final LoadRocaDiariaPort cargar = mock(LoadRocaDiariaPort.class);
    private final SaveRocaDiariaPort guardar = mock(SaveRocaDiariaPort.class);
    private final EvaluacionDelDiaPort marcas = mock(EvaluacionDelDiaPort.class);
    private final ConsultarCierreDelDiaUseCase consulta = mock(ConsultarCierreDelDiaUseCase.class);
    private final VistaDelCierre vistaDelNueve = new VistaDelCierre(
            new DiaDelCierre(EL_9, "CERRADO", HECHO, null, List.of()), false, null, Set.of());

    private EntregaSinRedDePrueba entrega;
    private CierreDelDiaSinRedService servicio;

    private final RocaDiaria propuesta = cumplida(EL_9, 1, "Enviar la propuesta", LocalTime.of(10, 0), 8);
    private final RocaDiaria llamada = pendiente(EL_9, 2, "Llamar a mi mentor", LocalTime.of(17, 30));
    private final RocaDiaria facturas = pendiente(EL_9, 3, "Revisar las facturas", LocalTime.of(20, 30));

    @BeforeEach
    void preparar() {
        FixedClock reloj = FixedClock.at(LLEGA);
        entrega = EntregaSinRedDePrueba.nueva(reloj);
        servicio = new CierreDelDiaSinRedService(progreso, cargar, guardar, marcas, consulta, entrega.puerto(), reloj);
        when(progreso.deParticipante(ANA)).thenReturn(Optional.of(
                new ProgresoParticipanteRocks(17, LocalDate.of(2026, 10, 24), LIMA, RolParticipante.TRAINEE, false, true)));
        when(cargar.deParticipanteYFecha(ANA, EL_9)).thenReturn(List.of(propuesta, llamada, facturas));
        when(consulta.consultar(ANA, EL_9)).thenReturn(vistaDelNueve);
        when(marcas.actualizadoEn(ANA, EL_9)).thenReturn(Optional.empty());
    }

    @AfterEach
    void terminar() {
        entrega.terminar();
    }

    private static RespuestaDeAccion responde(RocaDiaria roca) {
        return new RespuestaDeAccion(roca.id().value(), "SIN_TIEMPO", "OTRA_HORA", null);
    }

    private CerrarElDia pedido(LocalDate fecha, RocaDiaria... respondidas) {
        return new CerrarElDia(ANA, fecha, java.util.Arrays.stream(respondidas).map(CierreDelDiaSinRedServiceTest::responde).toList(), "Buen día");
    }

    @Test
    @DisplayName("cerrar el lunes a las 21:40 y llegar el martes SÍ se aplica: el cierre queda con la hora real y la respuesta es la del lunes")
    void cierraAyerYLlegaHoy() { // spec 048 FR-019
        VistaDelCierre vista = servicio.cerrar(pedido(EL_9, llamada, facturas), SIN_RED);

        assertThat(vista).isSameAs(vistaDelNueve);
        @SuppressWarnings("unchecked") ArgumentCaptor<List<RocaDiaria>> cambiadas = ArgumentCaptor.forClass(List.class);
        verify(guardar).guardarSoloElCierre(cambiadas.capture());
        assertThat(cambiadas.getValue()).containsExactlyInAnyOrder(llamada, facturas);
        assertThat(llamada.cierre().cerradoEn()).isEqualTo(HECHO);
        assertThat(llamada.actualizadoEn()).isEqualTo(HECHO);
        verify(marcas).dejarCerrado(ANA, EL_9, "Buen día", HECHO);
        assertThat(entrega.registro(ENVIO)).isPresent();
        assertThat(entrega.registro(ENVIO).orElseThrow().noAplicado()).isNull();
    }

    @Test
    @DisplayName("nunca completa una acción ni reescribe una roca entera: cerrar solo escribe las columnas de cierre")
    void cerrarNoCumpleNada() { // spec 048 FR-019
        servicio.cerrar(pedido(EL_9, llamada, facturas), SIN_RED);

        verify(guardar, never()).save(any());
        verify(guardar, never()).saveAll(any());
        assertThat(llamada.completada()).isFalse();
        assertThat(facturas.completada()).isFalse();
        assertThat(propuesta.completada()).isTrue();
    }

    @Test
    @DisplayName("lo que se exige es lo que ya había llegado a la HORA REAL: una acción de las 22:30 no se exige a las 21:40 aunque a la llegada sí")
    void seExigeSegunLaHoraReal() { // spec 048 FR-019
        RocaDiaria despues = pendiente(EL_9, 3, "Estirar antes de dormir", LocalTime.of(22, 30));
        when(cargar.deParticipanteYFecha(ANA, EL_9)).thenReturn(List.of(propuesta, llamada, facturas, despues));

        servicio.cerrar(pedido(EL_9, llamada, facturas), SIN_RED);

        verify(guardar).guardarSoloElCierre(any());
        assertThat(despues.cierre()).isNull();
    }

    @Test
    @DisplayName("una acción creada DESPUÉS de lo que hiciste no se exige ni se toca: el teléfono no la conocía")
    void unaAccionCreadaDespuesNoSeExige() { // spec 048 FR-019
        RocaDiaria nueva = pendienteCreadaEn(EL_9, 3, "Llamar al banco", null, HECHO.plusSeconds(3600));
        when(cargar.deParticipanteYFecha(ANA, EL_9)).thenReturn(List.of(propuesta, llamada, facturas, nueva));

        servicio.cerrar(pedido(EL_9, llamada, facturas), SIN_RED);

        @SuppressWarnings("unchecked") ArgumentCaptor<List<RocaDiaria>> cambiadas = ArgumentCaptor.forClass(List.class);
        verify(guardar).guardarSoloElCierre(cambiadas.capture());
        assertThat(cambiadas.getValue()).doesNotContain(nueva);
        assertThat(nueva.cierre()).isNull();
    }

    @Test
    @DisplayName("cerrar un día que no es el de la hora real (el de antes o el de la llegada) es FUERA_DE_SU_DIA y no escribe nada")
    void otroDiaEsFueraDeSuDia() { // spec 048 FR-019
        assertThatThrownBy(() -> servicio.cerrar(pedido(EL_9.minusDays(1)), SIN_RED)).isInstanceOfSatisfying(
                NoAplicadoSinRedException.class, e -> assertThat(e.codigo()).isEqualTo(CodigoNoAplicado.FUERA_DE_SU_DIA));
        assertThatThrownBy(() -> servicio.cerrar(pedido(EL_9.plusDays(1)), new HechoSinRed(HECHO, UUID.randomUUID(), null)))
                .isInstanceOfSatisfying(NoAplicadoSinRedException.class,
                        e -> assertThat(e.codigo()).isEqualTo(CodigoNoAplicado.FUERA_DE_SU_DIA));

        verifyNoInteractions(guardar);
        verify(marcas, never()).dejarCerrado(any(), any(), any(), any());
    }

    @Test
    @DisplayName("D13: si la nota del día cambió en el servidor DESPUÉS de lo que hiciste, gana el servidor y queda anotado como no aplicado")
    void laNotaMasNuevaDelServidorGana() { // spec 048 FR-019
        when(marcas.actualizadoEn(ANA, EL_9)).thenReturn(Optional.of(HECHO.plusSeconds(300)));

        assertThatThrownBy(() -> servicio.cerrar(pedido(EL_9, llamada, facturas), SIN_RED)).isInstanceOfSatisfying(
                NoAplicadoSinRedException.class,
                e -> assertThat(e.codigo()).isEqualTo(CodigoNoAplicado.CAMBIO_MAS_NUEVO_EN_EL_SERVIDOR));

        verifyNoInteractions(guardar);
        verify(marcas, never()).dejarCerrado(any(), any(), any(), any());
        assertThat(entrega.noAplicado(ENVIO)).contains(CodigoNoAplicado.CAMBIO_MAS_NUEVO_EN_EL_SERVIDOR);
    }

    @Test
    @DisplayName("D13: un cierre de una acción hecho en el servidor DESPUÉS de lo que hiciste también gana; uno ANTERIOR no choca")
    void unCierreMasNuevoGana() { // spec 048 FR-019
        RocaDiaria cerradaDespues = conCierre(llamada, new CierreDeAccion(respuesta(
                com.renaser.os.rocks.domain.model.cierre.MotivoDeAccionPendiente.SIN_TIEMPO,
                com.renaser.os.rocks.domain.model.cierre.PlanDeManana.OTRA_HORA, null), HECHO.plusSeconds(60)));
        when(cargar.deParticipanteYFecha(ANA, EL_9)).thenReturn(List.of(propuesta, cerradaDespues, facturas));

        assertThatThrownBy(() -> servicio.cerrar(new CerrarElDia(ANA, EL_9,
                List.of(responde(cerradaDespues), responde(facturas)), null), SIN_RED))
                .isInstanceOfSatisfying(NoAplicadoSinRedException.class,
                        e -> assertThat(e.codigo()).isEqualTo(CodigoNoAplicado.CAMBIO_MAS_NUEVO_EN_EL_SERVIDOR));

        RocaDiaria cerradaAntes = conCierre(llamada, new CierreDeAccion(respuesta(
                com.renaser.os.rocks.domain.model.cierre.MotivoDeAccionPendiente.SIN_TIEMPO,
                com.renaser.os.rocks.domain.model.cierre.PlanDeManana.OTRA_HORA, null), HECHO.minusSeconds(60)));
        when(cargar.deParticipanteYFecha(ANA, EL_9)).thenReturn(List.of(propuesta, cerradaAntes, facturas));

        servicio.cerrar(new CerrarElDia(ANA, EL_9, List.of(responde(cerradaAntes), responde(facturas)), null),
                new HechoSinRed(HECHO, UUID.randomUUID(), null));

        verify(guardar).guardarSoloElCierre(any());
    }

    @Test
    @DisplayName("P7: una acción que ya no existe deja TODO sin aplicar (RECURSO_YA_NO_EXISTE): no se guarda ni siquiera lo demás")
    void unaAccionQueYaNoExisteEsTodoONada() { // spec 048 FR-019
        CerrarElDia conUnaFantasma = new CerrarElDia(ANA, EL_9, List.of(responde(llamada), responde(facturas),
                new RespuestaDeAccion(UUID.randomUUID(), "SIN_TIEMPO", "OTRA_HORA", null)), "Buen día");

        assertThatThrownBy(() -> servicio.cerrar(conUnaFantasma, SIN_RED)).isInstanceOfSatisfying(
                NoAplicadoSinRedException.class, e -> assertThat(e.codigo()).isEqualTo(CodigoNoAplicado.RECURSO_YA_NO_EXISTE));

        verifyNoInteractions(guardar);
        verify(marcas, never()).dejarCerrado(any(), any(), any(), any());
    }

    @Test
    @DisplayName("una pendiente que se pregunta y el teléfono no conocía (sin respuesta) es CAMBIO_MAS_NUEVO_EN_EL_SERVIDOR, no un 400")
    void unaPendienteQueElTelefonoNoConocia() { // spec 048 FR-019
        assertThatThrownBy(() -> servicio.cerrar(pedido(EL_9, llamada), SIN_RED)).isInstanceOfSatisfying(
                NoAplicadoSinRedException.class,
                e -> assertThat(e.codigo()).isEqualTo(CodigoNoAplicado.CAMBIO_MAS_NUEVO_EN_EL_SERVIDOR));

        verifyNoInteractions(guardar);
    }

    @Test
    @DisplayName("el mismo envío dos veces: la segunda no toca nada y devuelve la misma lectura del día")
    void repetirElEnvioNoDuplica() { // spec 048 FR-008
        servicio.cerrar(pedido(EL_9, llamada, facturas), SIN_RED);

        VistaDelCierre otraVez = servicio.cerrar(pedido(EL_9, llamada, facturas), SIN_RED);

        assertThat(otraVez).isSameAs(vistaDelNueve);
        verify(guardar, org.mockito.Mockito.times(1)).guardarSoloElCierre(any());
        verify(marcas, org.mockito.Mockito.times(1)).dejarCerrado(any(), any(), any(), any());
    }

    @Test
    @DisplayName("una cuenta suspendida recibe el 403 de siempre, con o sin hora real")
    void cuentaSuspendida() { // spec 048 FR-019
        when(progreso.deParticipante(ANA)).thenReturn(Optional.of(
                new ProgresoParticipanteRocks(17, LocalDate.of(2026, 10, 24), LIMA, RolParticipante.TRAINEE, true, true)));

        assertThatThrownBy(() -> servicio.cerrar(pedido(EL_9, llamada, facturas), SIN_RED))
                .isInstanceOf(NotAuthorizedException.class);
        verifyNoInteractions(guardar);
    }
}

package com.renaser.os.rag.application.services.herramientas;

import com.renaser.os.rag.application.ports.in.propuesta.ConsultarPropuestasDelTurnoUseCase.PedidoDeEvidencia;
import com.renaser.os.rag.application.ports.out.habitos.ConsultarAgendaHabitosPort;
import com.renaser.os.rag.application.ports.out.rocas.ConsultarRocasDelAprendizPort;
import com.renaser.os.rag.application.ports.out.rocas.ConsultarRocasDelAprendizPort.PlanDeManana;
import com.renaser.os.rag.application.ports.out.rocas.ConsultarRocasDelAprendizPort.RocaDelDia;
import com.renaser.os.rag.application.ports.out.rocas.ConsultarRocasDelAprendizPort.RocasDelDia;
import com.renaser.os.rag.domain.model.conversacion.DestinoDeEvidencia;
import com.renaser.os.rag.domain.model.herramienta.InvocacionHerramienta;
import com.renaser.os.rag.domain.model.herramienta.ResultadoHerramienta;
import com.renaser.os.shared.domain.FixedClock;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@code proponer_registrar_accion_con_foto} (D-178): valida con las rocas de hoy que devuelve
 * {@code rocks} y deja la tarjeta de la camara con destino ROCA. Regla 02: el reloj esta a las 03:00
 * UTC, las 22:00 del dia ANTERIOR en Lima; la tarjeta vence a la medianoche de Lima, no a la de UTC.
 */
class PropuestaDeRegistrarAccionConFotoTest {

    private static final UserId APRENDIZ = UserId.of(UUID.randomUUID());
    private static final ZoneId LIMA = ZoneId.of("America/Lima");
    /** 22:00 del viernes 25/09 en Lima; en UTC ya es sabado. */
    private static final Instant NOCHE_EN_LIMA = Instant.parse("2026-09-26T03:00:00Z");
    private static final LocalDate VIERNES_EN_LIMA = LocalDate.of(2026, 9, 25);
    /** La medianoche que cierra el viernes 25/09 en Lima. */
    private static final Instant FIN_DEL_VIERNES_EN_LIMA = Instant.parse("2026-09-26T05:00:00Z");
    private static final UUID VERDE = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID AMARILLA = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final PlanDeManana PLAN = new PlanDeManana(false, 0, true, LocalTime.of(18, 0), true);

    private final ConsultarRocasDelAprendizPort rocas = mock(ConsultarRocasDelAprendizPort.class);
    private final ConsultarAgendaHabitosPort agenda = mock(ConsultarAgendaHabitosPort.class);
    private final PedidosDeEvidenciaDelTurno pedidos = new PedidosDeEvidenciaDelTurno(FixedClock.at(NOCHE_EN_LIMA));
    private final PropuestaDeRegistrarAccionConFoto herramienta = new PropuestaDeRegistrarAccionConFoto(rocas,
            agenda, pedidos, FixedClock.at(NOCHE_EN_LIMA));

    private static RocaDelDia verde(boolean completada) {
        return new RocaDelDia(VERDE, "TRABAJO", 1, "VERDE", "Llamar a 3 clientes", null, null, completada, false);
    }

    private static RocaDelDia amarilla(boolean bloqueada) {
        return new RocaDelDia(AMARILLA, "TRABAJO", 2, "AMARILLA", "Enviar 2 propuestas", null, null, false,
                bloqueada);
    }

    private void hoyTiene(RocaDelDia... deHoy) {
        when(rocas.deHoy(APRENDIZ)).thenReturn(new RocasDelDia(VIERNES_EN_LIMA, List.of(deHoy), PLAN));
        when(agenda.zonaDe(APRENDIZ)).thenReturn(LIMA);
    }

    private ResultadoHerramienta pedirFoto(String rocaId) {
        return herramienta.ejecutar(APRENDIZ, new InvocacionHerramienta(PropuestaDeRegistrarAccionConFoto.NOMBRE,
                Map.of("roca_id", rocaId)));
    }

    private static String fallo(ResultadoHerramienta resultado) {
        return ((ResultadoHerramienta.Fallo) resultado).motivo();
    }

    private List<PedidoDeEvidencia> pedidosDelTurno() {
        return pedidos.pedidasDesde(APRENDIZ, NOCHE_EN_LIMA);
    }

    @Test
    @DisplayName("pide la tarjeta con destino ROCA, sin pregunta, que vence al terminar el dia de Lima y no el de UTC")
    void pideLaFoto() {
        hoyTiene(verde(false), amarilla(true));

        ResultadoHerramienta resultado = pedirFoto(" " + VERDE + " ");

        assertThat(pedidosDelTurno()).containsExactly(new PedidoDeEvidencia(VERDE, "Llamar a 3 clientes",
                NOCHE_EN_LIMA, FIN_DEL_VIERNES_EN_LIMA, false, DestinoDeEvidencia.ROCA));
        assertThat(((ResultadoHerramienta.Exito) resultado).contenido())
                .contains("TODAVIA NO esta registrada")
                .contains("Te deje abajo el boton para sacarle foto a Llamar a 3 clientes")
                .contains("No preguntes si quiere");
    }

    @Test
    @DisplayName("con la verde ya hecha, la segunda del eje se desbloquea y se puede pedir")
    void laSegundaConLaVerdeHecha() {
        hoyTiene(verde(true), amarilla(false));

        assertThat(pedirFoto(AMARILLA.toString())).isInstanceOf(ResultadoHerramienta.Exito.class);
        assertThat(pedidosDelTurno()).extracting(PedidoDeEvidencia::registroId).containsExactly(AMARILLA);
    }

    @Test
    @DisplayName("Pareto: bloqueada, se rechaza diciendo cual verde va primero y con su roca_id; no hay tarjeta")
    void bloqueadaPorPareto() {
        hoyTiene(verde(false), amarilla(true));

        String motivo = fallo(pedirFoto(AMARILLA.toString()));

        assertThat(motivo).contains("'Enviar 2 propuestas' todavia no se puede registrar")
                .contains("primero va la accion verde")
                .contains("Primero tiene que registrar 'Llamar a 3 clientes' (roca_id=" + VERDE + ")");
        assertThat(pedidosDelTurno()).isEmpty();
    }

    @Test
    @DisplayName("Pareto sin la verde en la lista: igual se rechaza, sin inventar cual es")
    void bloqueadaSinVerdeALaVista() {
        hoyTiene(amarilla(true));

        assertThat(fallo(pedirFoto(AMARILLA.toString())))
                .contains("primero tiene que completar la verde de ese eje")
                .doesNotContain("roca_id=");
        assertThat(pedidosDelTurno()).isEmpty();
    }

    @Test
    @DisplayName("ya completada: no hace falta otra foto")
    void yaCompletada() {
        hoyTiene(verde(true));

        assertThat(fallo(pedirFoto(VERDE.toString()))).contains("ya esta registrada hoy");
        assertThat(pedidosDelTurno()).isEmpty();
    }

    @Test
    @DisplayName("una roca que no esta entre las de hoy (otro dia, otra persona, inventada) no se pide")
    void noEsDeHoy() {
        hoyTiene(verde(false));

        assertThat(fallo(pedirFoto(UUID.randomUUID().toString())))
                .contains("no esta entre las de hoy").contains("consultar_rocas con alcance hoy");
        assertThat(pedidosDelTurno()).isEmpty();
    }

    @Test
    @DisplayName("un roca_id que no es un UUID no consulta nada")
    void idInvalido() {
        assertThat(fallo(pedirFoto("la verde"))).contains("roca_id no es valido");
        assertThat(pedidosDelTurno()).isEmpty();
    }

    @Test
    @DisplayName("cuenta suspendida o sin programa de rocas: fallo legible, sin tarjeta")
    void sinAcceso() {
        when(rocas.deHoy(APRENDIZ)).thenThrow(new NotAuthorizedException("suspendido"));

        assertThat(fallo(pedirFoto(VERDE.toString()))).contains("cuenta esta suspendida");
        assertThat(pedidosDelTurno()).isEmpty();
    }
}

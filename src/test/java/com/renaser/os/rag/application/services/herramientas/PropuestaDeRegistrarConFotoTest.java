package com.renaser.os.rag.application.services.herramientas;

import com.renaser.os.rag.application.ports.in.propuesta.ConsultarPropuestasDelTurnoUseCase.PedidoDeEvidencia;
import com.renaser.os.rag.application.ports.out.habitos.ConsultarAgendaHabitosPort;
import com.renaser.os.rag.application.ports.out.habitos.ConsultarAgendaHabitosPort.HabitoDelDia;
import com.renaser.os.rag.domain.model.herramienta.InvocacionHerramienta;
import com.renaser.os.rag.domain.model.herramienta.ResultadoHerramienta;
import com.renaser.os.shared.domain.FixedClock;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@code proponer_registrar_con_foto} (D-171): valida con los datos de {@code habits} y deja la
 * tarjeta de la camara para el turno. Regla 02: el reloj esta a las 03:00 UTC, las 22:00 del dia
 * ANTERIOR en Lima; el fin del dia de la persona es la medianoche de Lima, no la de UTC.
 */
class PropuestaDeRegistrarConFotoTest {

    private static final UserId APRENDIZ = UserId.of(UUID.randomUUID());
    private static final ZoneId LIMA = ZoneId.of("America/Lima");
    /** 22:00 del viernes 25/09 en Lima. */
    private static final Instant NOCHE_EN_LIMA = Instant.parse("2026-09-26T03:00:00Z");
    /** La medianoche que cierra el viernes 25/09 en Lima. En UTC ya seria el sabado 26 a las 24:00. */
    private static final Instant FIN_DEL_VIERNES_EN_LIMA = Instant.parse("2026-09-26T05:00:00Z");
    private static final UUID JUGO = UUID.fromString("55555555-5555-5555-5555-555555555555");

    private final ConsultarAgendaHabitosPort agenda = mock(ConsultarAgendaHabitosPort.class);
    private final PedidosDeEvidenciaDelTurno pedidos = new PedidosDeEvidenciaDelTurno(FixedClock.at(NOCHE_EN_LIMA));
    private final PropuestaDeRegistrarConFoto herramienta = new PropuestaDeRegistrarConFoto(agenda, pedidos,
            FixedClock.at(NOCHE_EN_LIMA));

    private static HabitoDelDia habito(String estado, boolean exigeEvidencia, String clave) {
        Integer puntos = estado.equals("PENDIENTE") || estado.equals("EN_CURSO") ? 10 : null;
        return new HabitoDelDia(JUGO, "JUGO VERDE", estado, puntos, 10, null, exigeEvidencia, List.of(), clave);
    }

    private ResultadoHerramienta pedirFoto(String registroId) {
        return herramienta.ejecutar(APRENDIZ, new InvocacionHerramienta(PropuestaDeRegistrarConFoto.NOMBRE,
                Map.of("registro_id", registroId)));
    }

    private static String fallo(ResultadoHerramienta resultado) {
        return ((ResultadoHerramienta.Fallo) resultado).motivo();
    }

    private List<PedidoDeEvidencia> pedidosDelTurno() {
        return pedidos.pedidasDesde(APRENDIZ, NOCHE_EN_LIMA);
    }

    @Test
    @DisplayName("pide la tarjeta de la camara, que vence al terminar el dia de Lima y no el de UTC")
    void pideLaFoto() {
        when(agenda.deHoyDe(APRENDIZ)).thenReturn(List.of(habito("PENDIENTE", true, "GREEN_JUICE")));
        when(agenda.zonaDe(APRENDIZ)).thenReturn(LIMA);

        ResultadoHerramienta resultado = pedirFoto(" " + JUGO + " ");

        assertThat(pedidosDelTurno()).containsExactly(
                new PedidoDeEvidencia(JUGO, "JUGO VERDE", NOCHE_EN_LIMA, FIN_DEL_VIERNES_EN_LIMA));
        assertThat(((ResultadoHerramienta.Exito) resultado).contenido())
                .contains("TODAVIA NO esta registrado")
                .contains("Te deje abajo el boton para sacarle foto a JUGO VERDE")
                .contains("No preguntes si quiere");
    }

    @Test
    @DisplayName("un registro que no esta entre los de hoy (de otra persona o de ayer) no pide nada")
    void noEsDeHoy() {
        when(agenda.deHoyDe(APRENDIZ)).thenReturn(List.of(new HabitoDelDia(UUID.randomUUID(), "Otro", "PENDIENTE",
                10, 10, null, true)));

        assertThat(fallo(pedirFoto(JUGO.toString()))).contains("no esta entre los de hoy");
        assertThat(pedidosDelTurno()).isEmpty();
    }

    @Test
    @DisplayName("ya completado, vencido o fallido: no pide la foto y dice por que")
    void estadosTerminales() {
        when(agenda.deHoyDe(APRENDIZ)).thenReturn(List.of(habito("COMPLETADO", true, null)));
        assertThat(fallo(pedirFoto(JUGO.toString()))).contains("ya esta registrado hoy");

        when(agenda.deHoyDe(APRENDIZ)).thenReturn(List.of(habito("EXPIRADO", true, null)));
        assertThat(fallo(pedirFoto(JUGO.toString()))).contains("ya vencio");

        when(agenda.deHoyDe(APRENDIZ)).thenReturn(List.of(habito("FALLIDO", true, null)));
        assertThat(fallo(pedirFoto(JUGO.toString()))).contains("ya se cerro");

        assertThat(pedidosDelTurno()).isEmpty();
    }

    @Test
    @DisplayName("un habito que no pide evidencia manda a marcar_habito_completado")
    void noPideEvidencia() {
        when(agenda.deHoyDe(APRENDIZ)).thenReturn(List.of(habito("PENDIENTE", false, null)));

        assertThat(fallo(pedirFoto(JUGO.toString()))).contains("no pide foto").contains("marcar_habito_completado");
        assertThat(pedidosDelTurno()).isEmpty();
    }

    @Test
    @DisplayName("la Clase diaria pide evidencia en el catalogo, pero se entrega con su resumen: sin camara")
    void claseDiaria() {
        when(agenda.deHoyDe(APRENDIZ)).thenReturn(List.of(habito("PENDIENTE", true, "DAILY_CLASS")));

        assertThat(fallo(pedirFoto(JUGO.toString()))).contains("proponer_entregar_clase_de_hoy");
        assertThat(pedidosDelTurno()).isEmpty();
    }

    @Test
    @DisplayName("un id inventado se rechaza sin mirar la agenda")
    void idInventado() {
        assertThat(fallo(pedirFoto("el-jugo"))).contains("no es valido");
        assertThat(pedidosDelTurno()).isEmpty();
    }

    @Test
    @DisplayName("fin del dia: a las 04:59 UTC todavia es el dia anterior en Lima; a las 05:00 ya es el siguiente")
    void finDelDiaLocal() {
        assertThat(PropuestaDeRegistrarConFoto.finDelDiaLocal(Instant.parse("2026-09-26T04:59:00Z"), LIMA))
                .isEqualTo(FIN_DEL_VIERNES_EN_LIMA);
        assertThat(PropuestaDeRegistrarConFoto.finDelDiaLocal(FIN_DEL_VIERNES_EN_LIMA, LIMA))
                .isEqualTo(Instant.parse("2026-09-27T05:00:00Z"));
    }
}

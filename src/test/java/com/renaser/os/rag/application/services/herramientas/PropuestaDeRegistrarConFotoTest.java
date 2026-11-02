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
                new PedidoDeEvidencia(JUGO, "JUGO VERDE", NOCHE_EN_LIMA, FIN_DEL_VIERNES_EN_LIMA, false));
        assertThat(((ResultadoHerramienta.Exito) resultado).contenido())
                .contains("TODAVIA NO esta registrado")
                .contains("Te deje abajo el boton para sacarle foto a JUGO VERDE")
                .contains("No preguntes si quiere").doesNotContain("Hoy");
    }

    @Test
    @DisplayName("spec 038: los tres rituales se registran ESCRIBIENDO (sin foto); en el jugo verde, foto y listo")
    void losRitualesSeEscriben() {
        when(agenda.zonaDe(APRENDIZ)).thenReturn(LIMA);
        for (String clave : List.of("RITUAL_MORNING", "RITUAL_MIDDAY", "RITUAL_NIGHT")) {
            when(agenda.deHoyDe(APRENDIZ)).thenReturn(List.of(habito("PENDIENTE", true, clave)));

            ResultadoHerramienta ritual = pedirFoto(JUGO.toString());

            assertThat(((ResultadoHerramienta.Exito) ritual).contenido()).as(clave)
                    .contains("Boton para escribir como se siente").contains("TODAVIA NO esta registrado")
                    .contains("Te deje abajo el boton para contar como te sientes despues de")
                    .contains("No le pidas foto").contains("no le preguntes tu que sintio")
                    .doesNotContain("camara").doesNotContain("sacarle foto").doesNotContain("saque la foto");
        }
        // La tarjeta viaja con conPregunta=true: la app lee «ritual: se escribe» y abre el registro por texto de Training, no la camara.
        assertThat(pedidosDelTurno()).extracting(PedidoDeEvidencia::conPregunta).containsOnly(true);

        when(agenda.deHoyDe(APRENDIZ)).thenReturn(List.of(habito("PENDIENTE", true, "GREEN_JUICE")));
        ResultadoHerramienta jugo = pedirFoto(JUGO.toString());

        assertThat(((ResultadoHerramienta.Exito) jugo).contenido()).contains("Boton de la camara").doesNotContain("escribir")
                .doesNotContain("sintio");
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
    @DisplayName("ya completado, dia cerrado (EXPIRADO, D-259) o fallido: no pide la foto y dice por que, sin decir que vencio")
    void estadosTerminales() {
        when(agenda.deHoyDe(APRENDIZ)).thenReturn(List.of(habito("COMPLETADO", true, null)));
        assertThat(fallo(pedirFoto(JUGO.toString()))).contains("ya esta registrado hoy");

        when(agenda.deHoyDe(APRENDIZ)).thenReturn(List.of(habito("EXPIRADO", true, null)));
        assertThat(fallo(pedirFoto(JUGO.toString()))).contains("es de un dia que ya cerro: ya no se puede registrar")
                .contains("No dejes boton").doesNotContain("vencio").doesNotContain("desde Hoy");

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

    private static HabitoDelDia kilometros(String estado, boolean conPasosConectados) {
        var medicion = new ConsultarAgendaHabitosPort.MedicionDelHabito("KILOMETROS", new java.math.BigDecimal("5.00"), null, null, null,
                conPasosConectados);
        Integer puntos = estado.equals("PENDIENTE") ? 10 : null;
        return new HabitoDelDia(JUGO, "KILOMETROS DIARIOS", estado, puntos, 10, null, true, List.of(), "DAILY_KM", null, null, "Cuerpo",
                null, null, medicion);
    }

    // spec 037 (T021 de la 038): con sus pasos conectados los km los lee el telefono; SER no deja el boton de la camara y manda al «Registrar» de Training
    @Test
    @DisplayName("spec 037: kilometros con los pasos conectados no piden foto ni dejan tarjeta: mandan al «Registrar» de la fila de Training")
    void kilometrosConPasosConectadosNoPidenFoto() {
        when(agenda.deHoyDe(APRENDIZ)).thenReturn(List.of(kilometros("PENDIENTE", true)));
        when(agenda.zonaDe(APRENDIZ)).thenReturn(LIMA);

        String motivo = fallo(pedirFoto(JUGO.toString()));

        assertThat(motivo).contains("mide los kilometros solo").contains("No dejes boton de camara")
                .contains("toque «Registrar» en la fila de Kilometros diarios").contains("meta de 5 km")
                .contains("No digas que ya quedo registrado");
        assertThat(pedidosDelTurno()).isEmpty();
    }

    @Test
    @DisplayName("spec 037: kilometros sin pasos conectados siguen con la tarjeta de la camara de siempre (captura o galeria)")
    void kilometrosSinPasosConectadosSiguenConLaCamara() {
        when(agenda.deHoyDe(APRENDIZ)).thenReturn(List.of(kilometros("PENDIENTE", false)));
        when(agenda.zonaDe(APRENDIZ)).thenReturn(LIMA);

        ResultadoHerramienta resultado = pedirFoto(JUGO.toString());

        assertThat(((ResultadoHerramienta.Exito) resultado).contenido()).contains("Boton de la camara listo para 'KILOMETROS DIARIOS'");
        assertThat(pedidosDelTurno()).hasSize(1);
    }

    @Test
    @DisplayName("spec 037: kilometros ya registrados (por pasos) dicen que ya quedo, no que se mide solo; y un dia cerrado sigue siendo dia cerrado")
    void kilometrosYaRegistradosOCerrados() {
        when(agenda.deHoyDe(APRENDIZ)).thenReturn(List.of(kilometros("COMPLETADO", true)));
        assertThat(fallo(pedirFoto(JUGO.toString()))).contains("ya esta registrado hoy");

        when(agenda.deHoyDe(APRENDIZ)).thenReturn(List.of(kilometros("EXPIRADO", true)));
        assertThat(fallo(pedirFoto(JUGO.toString()))).contains("es de un dia que ya cerro");
    }
}

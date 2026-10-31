package com.renaser.os.rocks.domain.model.rocadiaria;

import com.renaser.os.rocks.domain.model.cierre.CierreDeAccion;
import com.renaser.os.rocks.domain.model.cierre.CierreDeOtroDiaException;
import com.renaser.os.rocks.domain.model.cierre.MomentoDelCierre;
import com.renaser.os.rocks.domain.model.cierre.MotivoDeAccionPendiente;
import com.renaser.os.rocks.domain.model.cierre.PlanDeManana;
import com.renaser.os.rocks.domain.model.cierre.RespuestaDeCierre;
import com.renaser.os.rocks.domain.model.rocadiaria.CierreDelDia.RespuestaDeUnaAccion;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import static com.renaser.os.rocks.domain.model.cierre.RocasDeCierreDePrueba.cumplida;
import static com.renaser.os.rocks.domain.model.cierre.RocasDeCierreDePrueba.pendiente;
import static com.renaser.os.rocks.domain.model.cierre.RocasDeCierreDePrueba.respuesta;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * La regla de cerrar el día (spec 017): una respuesta por cada pendiente, nada por lo cumplido y, sobre todo, <b>cerrar no
 * cumple acciones ni toca puntos</b> (SC-001).
 */
class CierreDelDiaTest {

    private static final LocalDate HOY = LocalDate.of(2026, 10, 28);
    private static final ZoneId LIMA = ZoneId.of("America/Lima");
    private static final Instant INSTANTE = Instant.parse("2026-10-29T03:00:00Z"); // el 28 a las 22:00 en Lima
    private static final MomentoDelCierre AHORA = MomentoDelCierre.de(INSTANTE, LIMA);

    private final RocaDiaria propuesta = cumplida(HOY, 1, "Enviar la propuesta", LocalTime.of(10, 0), 8);
    private final RocaDiaria llamada = pendiente(HOY, 2, "Llamar a mi mentor", LocalTime.of(17, 30));
    private final RocaDiaria facturas = pendiente(HOY, 3, "Revisar las facturas", LocalTime.of(20, 30));

    private static RespuestaDeUnaAccion dice(RocaDiaria roca, MotivoDeAccionPendiente motivo, PlanDeManana plan) {
        return new RespuestaDeUnaAccion(roca.id(), respuesta(motivo, plan, null));
    }

    @Test
    @DisplayName("cierra las pendientes con su respuesta y NO toca completada, puntos ni la hora de cumplida de ninguna")
    void cerrarNoCumpleNiPaga() {
        List<RocaDiaria> delDia = List.of(propuesta, llamada, facturas);
        Instant cumplidaEn = propuesta.completadaEn();

        List<RocaDiaria> cambiadas = CierreDelDia.cerrar(delDia, List.of(
                dice(llamada, MotivoDeAccionPendiente.SIN_ENERGIA, PlanDeManana.MAS_CORTA),
                dice(facturas, MotivoDeAccionPendiente.SIN_TIEMPO, PlanDeManana.OTRA_HORA)), AHORA);

        assertThat(cambiadas).containsExactlyInAnyOrder(llamada, facturas);
        assertThat(llamada.cierre()).isEqualTo(new CierreDeAccion(
                respuesta(MotivoDeAccionPendiente.SIN_ENERGIA, PlanDeManana.MAS_CORTA, null), INSTANTE));
        assertThat(facturas.cierre().respuesta().manana()).isEqualTo(PlanDeManana.OTRA_HORA);
        // SC-001: lo cumplido sigue cumplido, lo pendiente sigue pendiente, y ningun punto se movio.
        assertThat(propuesta.completada()).isTrue();
        assertThat(propuesta.completadaEn()).isEqualTo(cumplidaEn);
        assertThat(propuesta.puntosOtorgados()).isEqualTo(8);
        assertThat(llamada.completada()).isFalse();
        assertThat(facturas.completada()).isFalse();
        assertThat(llamada.puntosOtorgados()).isZero();
        assertThat(facturas.completadaEn()).isNull();
    }

    @Test
    @DisplayName("una pendiente sin respuesta es 400 y no deja NINGUNA roca a medio cerrar")
    void faltaUnaRespuesta() {
        assertThatThrownBy(() -> CierreDelDia.cerrar(List.of(llamada, facturas),
                List.of(dice(llamada, MotivoDeAccionPendiente.SIN_TIEMPO, PlanDeManana.DEJARLA)), AHORA))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Revisar las facturas");

        assertThat(llamada.cierre()).isNull();
        assertThat(facturas.cierre()).isNull();
    }

    @Test
    @DisplayName("un dia sin acciones se cierra con una lista vacia")
    void diaSinAcciones() {
        assertThat(CierreDelDia.cerrar(List.of(), List.of(), AHORA)).isEmpty();
    }

    @Test
    @DisplayName("todo cumplido: no hay nada que contar y no cambia ninguna roca")
    void todoCumplido() {
        assertThat(CierreDelDia.cerrar(List.of(propuesta), List.of(), AHORA)).isEmpty();
    }

    @Test
    @DisplayName("una respuesta de una accion ya cumplida se ignora, y limpia la que tuviera de antes")
    void cumplidaDespuesDeCerrar() {
        RocaDiaria conRespuestaVieja = com.renaser.os.rocks.domain.model.cierre.RocasDeCierreDePrueba.conCierre(
                cumplida(HOY, 2, "Llamar a mi mentor", LocalTime.of(17, 30), 5),
                new CierreDeAccion(respuesta(MotivoDeAccionPendiente.PARA_DESPUES, PlanDeManana.PEDIR_AYUDA, "luego"), INSTANTE));

        List<RocaDiaria> cambiadas = CierreDelDia.cerrar(List.of(conRespuestaVieja),
                List.of(dice(conRespuestaVieja, MotivoDeAccionPendiente.SIN_TIEMPO, PlanDeManana.DEJARLA)), AHORA);

        assertThat(cambiadas).containsExactly(conRespuestaVieja);
        assertThat(conRespuestaVieja.cierre()).isNull();
        assertThat(conRespuestaVieja.completada()).isTrue();
        assertThat(conRespuestaVieja.puntosOtorgados()).isEqualTo(5);
    }

    @Test
    @DisplayName("volver a cerrar reemplaza la respuesta anterior")
    void reemplazaLaRespuestaAnterior() {
        CierreDelDia.cerrar(List.of(facturas), List.of(dice(facturas, MotivoDeAccionPendiente.SIN_TIEMPO, PlanDeManana.OTRA_HORA)), AHORA);
        Instant masTarde = INSTANTE.plusSeconds(600);

        CierreDelDia.cerrar(List.of(facturas), List.of(new RespuestaDeUnaAccion(facturas.id(),
                respuesta(MotivoDeAccionPendiente.SURGIO_OTRA_COSA, PlanDeManana.PEDIR_AYUDA, "Llegó una visita"))),
                MomentoDelCierre.de(masTarde, LIMA));

        assertThat(facturas.cierre().respuesta()).isEqualTo(
                respuesta(MotivoDeAccionPendiente.SURGIO_OTRA_COSA, PlanDeManana.PEDIR_AYUDA, "Llegó una visita"));
        assertThat(facturas.cierre().cerradoEn()).isEqualTo(masTarde);
    }

    @Test
    @DisplayName("una respuesta para una accion que no es de este dia, o repetida, es 400")
    void respuestasAjenasORepetidas() {
        RocaDiaria deOtro = pendiente(HOY, 1, "De otra persona u otro dia", null);
        assertThatThrownBy(() -> CierreDelDia.cerrar(List.of(facturas), List.of(
                dice(facturas, MotivoDeAccionPendiente.SIN_TIEMPO, PlanDeManana.DEJARLA),
                dice(deOtro, MotivoDeAccionPendiente.SIN_TIEMPO, PlanDeManana.DEJARLA)), AHORA))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CierreDelDia.cerrar(List.of(facturas), List.of(
                dice(facturas, MotivoDeAccionPendiente.SIN_TIEMPO, PlanDeManana.DEJARLA),
                new RespuestaDeUnaAccion(facturas.id(), respuesta(MotivoDeAccionPendiente.SIN_ENERGIA, PlanDeManana.DEJARLA, null))),
                AHORA)).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("repetida");
    }

    // ---- Solo se exige lo que ya llegó a su hora (decisión del dueño, 2026-10-30) ----

    private static MomentoDelCierre aLas(int hora, int minuto) {
        // El 28 a las hh:mm en Lima (UTC-5): el instante cae en la madrugada UTC del 29 cuando la hora local es >= 19.
        return MomentoDelCierre.de(java.time.ZonedDateTime.of(HOY, LocalTime.of(hora, minuto), LIMA).toInstant(), LIMA);
    }

    @Test
    @DisplayName("a las 06:00 no se exige la de las 20:30 ni la de las 17:30: cerrar sin respuestas para ellas es válido")
    void laDeHoraFuturaNoSeExige() {
        List<RocaDiaria> cambiadas = CierreDelDia.cerrar(List.of(propuesta, llamada, facturas), List.of(), aLas(6, 0));

        assertThat(cambiadas).isEmpty();
        assertThat(llamada.cierre()).isNull();
        assertThat(facturas.cierre()).isNull();
        assertThat(llamada.completada()).isFalse();
        assertThat(facturas.completada()).isFalse();
        assertThat(propuesta.completada()).isTrue();
    }

    @Test
    @DisplayName("a las 21:00 sí se exige la de las 20:30 (y la de las 17:30): sin su respuesta es 400 y nada queda a medio cerrar")
    void laDeHoraPasadaSeExige() {
        assertThatThrownBy(() -> CierreDelDia.cerrar(List.of(llamada, facturas),
                List.of(dice(llamada, MotivoDeAccionPendiente.SIN_TIEMPO, PlanDeManana.DEJARLA)), aLas(21, 0)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Revisar las facturas");
        assertThat(llamada.cierre()).isNull();
    }

    @Test
    @DisplayName("a las 19:00 se exige la de las 17:30 pero no la de las 20:30")
    void soloLaQueYaPaso() {
        List<RocaDiaria> cambiadas = CierreDelDia.cerrar(List.of(llamada, facturas),
                List.of(dice(llamada, MotivoDeAccionPendiente.SIN_ENERGIA, PlanDeManana.MAS_CORTA)), aLas(19, 0));

        assertThat(cambiadas).containsExactly(llamada);
        assertThat(facturas.cierre()).isNull();
        assertThat(CierreDelDia.seExigeRespuesta(llamada, aLas(19, 0))).isTrue();
        assertThat(CierreDelDia.seExigeRespuesta(facturas, aLas(19, 0))).isFalse();
    }

    @Test
    @DisplayName("justo a su hora (20:30) ya se exige; un minuto antes no")
    void justoALaHora() {
        assertThat(CierreDelDia.seExigeRespuesta(facturas, aLas(20, 29))).isFalse();
        assertThat(CierreDelDia.seExigeRespuesta(facturas, aLas(20, 30))).isTrue();
    }

    @Test
    @DisplayName("una acción sin hora se pregunta como siempre, a las 06:00 también")
    void sinHoraSePregunta() {
        RocaDiaria sinHora = pendiente(HOY, 1, "Ordenar el escritorio", null);

        assertThat(CierreDelDia.seExigeRespuesta(sinHora, aLas(6, 0))).isTrue();
        assertThatThrownBy(() -> CierreDelDia.cerrar(List.of(sinHora), List.of(), aLas(6, 0)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Ordenar el escritorio");
    }

    @Test
    @DisplayName("una cumplida nunca se exige, sea cual sea su hora")
    void cumplidaNuncaSeExige() {
        assertThat(CierreDelDia.seExigeRespuesta(propuesta, aLas(6, 0))).isFalse();
        assertThat(CierreDelDia.seExigeRespuesta(propuesta, aLas(23, 0))).isFalse();
    }

    @Test
    @DisplayName("la de hora futura que llega con respuesta se guarda (una app anterior pregunta por todas)")
    void laDeHoraFuturaConRespuestaSeGuarda() {
        List<RocaDiaria> cambiadas = CierreDelDia.cerrar(List.of(facturas),
                List.of(dice(facturas, MotivoDeAccionPendiente.PARA_DESPUES, PlanDeManana.OTRA_HORA)), aLas(6, 0));

        assertThat(cambiadas).containsExactly(facturas);
        assertThat(facturas.cierre().respuesta().motivo()).isEqualTo(MotivoDeAccionPendiente.PARA_DESPUES);
    }

    @Test
    @DisplayName("volver a guardar sin respuesta para la de hora futura olvida la que tuviera: guardar reemplaza lo del día")
    void guardarSinRespuestaLimpiaLaDeHoraFutura() {
        CierreDelDia.cerrar(List.of(facturas),
                List.of(dice(facturas, MotivoDeAccionPendiente.PARA_DESPUES, PlanDeManana.OTRA_HORA)), aLas(6, 0));

        List<RocaDiaria> cambiadas = CierreDelDia.cerrar(List.of(facturas), List.of(), aLas(7, 0));

        assertThat(cambiadas).containsExactly(facturas);
        assertThat(facturas.cierre()).isNull();
        assertThat(facturas.completada()).isFalse();
    }

    @Test
    @DisplayName("la hora es la de la zona de la persona: a las 03:00 UTC del 29 en Lima son las 22:00 del 28, no las 03:00")
    void laHoraEsLaDeSuZona() {
        MomentoDelCierre enLima = MomentoDelCierre.de(Instant.parse("2026-10-29T03:00:00Z"), LIMA);
        MomentoDelCierre enUtc = MomentoDelCierre.de(Instant.parse("2026-10-29T03:00:00Z"), ZoneId.of("UTC"));

        assertThat(CierreDelDia.seExigeRespuesta(facturas, enLima)).isTrue();  // 22:00 >= 20:30
        assertThat(CierreDelDia.seExigeRespuesta(facturas, enUtc)).isFalse();  // 03:00 < 20:30
    }

    @Test
    @DisplayName("solo se cierra hoy: ayer es 409 (D-259), manana es 400")
    void soloHoy() {
        CierreDelDia.exigirQueSeaHoy(HOY, HOY);

        assertThatThrownBy(() -> CierreDelDia.exigirQueSeaHoy(HOY.minusDays(1), HOY))
                .isInstanceOf(CierreDeOtroDiaException.class).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Ese día ya cerró");
        assertThatThrownBy(() -> CierreDelDia.exigirQueSeaHoy(HOY.plusDays(1), HOY))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("el id de una respuesta puede venir de la app como cualquier UUID: se compara por valor")
    void elIdSeComparaPorValor() {
        RespuestaDeUnaAccion r = new RespuestaDeUnaAccion(
                new RocaDiariaId(UUID.fromString(facturas.id().value().toString())), respuesta(MotivoDeAccionPendiente.SIN_TIEMPO, PlanDeManana.DEJARLA, null));
        assertThat(CierreDelDia.cerrar(List.of(facturas), List.of(r), AHORA)).containsExactly(facturas);
    }
}

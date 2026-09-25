package com.renaser.os;

import com.renaser.os.points.api.ColorSemaforo;
import com.renaser.os.points.api.DetalleDelSemaforo;
import com.renaser.os.points.api.DiaDelSemaforo;
import com.renaser.os.points.api.EstadoDiaSemaforo;
import com.renaser.os.points.api.PausaDelSemaforo;
import com.renaser.os.points.api.SemanaCerrada;
import com.renaser.os.points.api.VentanaDelSemaforo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * El detalle del semáforo de una persona sale por cuatro rutas con UN solo formato
 * (docs/arquitectura/SEMAFORO_DEL_APRENDIZ.md §4.1): {@code /me/semaforo} lo arma {@code points} y las
 * del mentor y de administración, {@code mentoring}. Son dos records porque un módulo no importa los
 * adaptadores de otro. La app los lee con un único esquema, así que un campo que se agregue o cambie en
 * uno solo rompería la pantalla del otro sin que nada avise: esta prueba es lo que los mantiene iguales.
 */
class DetalleDelSemaforoMismoFormatoTest {

    private static final LocalDate JUEVES = LocalDate.of(2026, 9, 24);

    private final JsonMapper json = JsonMapper.builder().build();

    @Test
    @DisplayName("con pausa, días de todos los estados y semanas con y sin datos, los dos formatos son idénticos")
    void detalleCompleto() {
        DetalleDelSemaforo detalle = new DetalleDelSemaforo(true, false, ZoneId.of("America/Lima"),
                new PausaDelSemaforo(JUEVES.plusDays(1), JUEVES.plusDays(10)), ventana(), semanas(),
                Instant.parse("2026-09-25T05:25:03Z"));

        assertThat(deMentoring(detalle)).isEqualTo(dePoints(detalle));
    }

    @Test
    @DisplayName("cuando el semáforo no aplica, los dos formatos son idénticos")
    void noAplica() {
        DetalleDelSemaforo detalle = DetalleDelSemaforo.noAplica(ZoneId.of("America/Lima"), true);

        assertThat(deMentoring(detalle)).isEqualTo(dePoints(detalle));
    }

    private JsonNode dePoints(DetalleDelSemaforo detalle) {
        return json.valueToTree(
                com.renaser.os.points.infrastructure.adapter.in.rest.semaforo.DetalleDelSemaforoResponse.de(detalle));
    }

    private JsonNode deMentoring(DetalleDelSemaforo detalle) {
        return json.valueToTree(
                com.renaser.os.mentoring.infrastructure.adapter.in.rest.semaforo.DetalleDelSemaforoResponse.from(detalle));
    }

    private static VentanaDelSemaforo ventana() {
        List<DiaDelSemaforo> dias = List.of(
                new DiaDelSemaforo(JUEVES.minusDays(6), EstadoDiaSemaforo.MEDIDO, 100, ColorSemaforo.VERDE, 5, 5, 2, 2),
                new DiaDelSemaforo(JUEVES.minusDays(5), EstadoDiaSemaforo.MEDIDO, 67, ColorSemaforo.AMARILLO, 4, 3, 2, 1),
                new DiaDelSemaforo(JUEVES.minusDays(4), EstadoDiaSemaforo.MEDIDO, 40, ColorSemaforo.ROJO, 3, 1, 2, 1),
                DiaDelSemaforo.sinPorcentaje(JUEVES.minusDays(3), EstadoDiaSemaforo.SIN_DATOS),
                DiaDelSemaforo.sinPorcentaje(JUEVES.minusDays(2), EstadoDiaSemaforo.PAUSADO),
                DiaDelSemaforo.sinPorcentaje(JUEVES.minusDays(1), EstadoDiaSemaforo.FUERA_DEL_PROGRAMA),
                DiaDelSemaforo.sinPorcentaje(JUEVES, EstadoDiaSemaforo.PENDIENTE));
        return new VentanaDelSemaforo(JUEVES.minusDays(6), JUEVES, new BigDecimal("69.0"), ColorSemaforo.AMARILLO,
                3, false, dias);
    }

    private static List<SemanaCerrada> semanas() {
        return List.of(
                new SemanaCerrada(LocalDate.of(2026, 9, 12), LocalDate.of(2026, 9, 18), new BigDecimal("82.1"),
                        ColorSemaforo.VERDE, 7, Instant.parse("2026-09-19T05:25:03Z")),
                new SemanaCerrada(LocalDate.of(2026, 9, 5), LocalDate.of(2026, 9, 11), null,
                        ColorSemaforo.SIN_DATOS, 0, Instant.parse("2026-09-12T05:25:03Z")));
    }
}

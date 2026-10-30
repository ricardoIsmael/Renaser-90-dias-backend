package com.renaser.os.habits.domain.model.radar;

import com.renaser.os.habits.domain.model.radar.DiaDelRadar.MotivoSinHorario;
import com.renaser.os.habits.domain.model.radar.DiaDeMiRadar.Modo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/** Spec 019: las horas de cada dia, el reparto parejo, hoy y lo pasado, y cuando se puede registrar. */
class HorarioDelRadarTest {

    private static final ZoneId LIMA = ZoneId.of("America/Lima");

    private static List<Integer> de(int desde, int hasta) {
        return IntStream.rangeClosed(desde, hasta).boxed().toList();
    }

    private static ZonedDateTime lima(String instanteLocal) {
        return ZonedDateTime.of(java.time.LocalDateTime.parse(instanteLocal), LIMA);
    }

    // ─── las horas que caben ──────────────────────────────────────────────

    @Test
    @DisplayName("Despertar 05:30 y Dormir 22:30: de 06 a 22, 17 veces y sin el tope de 12")
    void diecisieteHoras() {
        assertThat(HorarioDelRadar.horasQueCaben(LocalTime.of(5, 30), LocalTime.of(22, 30))).containsExactlyElementsOf(de(6, 22));
    }

    @Test
    @DisplayName("Despertar 08:00 y Dormir 23:00: de 08 a 22, 15 veces (la de las 23:00 ya es la hora de dormir)")
    void quinceHoras() {
        assertThat(HorarioDelRadar.horasQueCaben(LocalTime.of(8, 0), LocalTime.of(23, 0))).containsExactlyElementsOf(de(8, 22));
    }

    @Test
    @DisplayName("Despertar 06:00 y Dormir 22:00: de 06 a 21 (la hora del Despertar cuenta y la del Dormir no)")
    void bordesExactos() {
        assertThat(HorarioDelRadar.horasQueCaben(LocalTime.of(6, 0), LocalTime.of(22, 0))).containsExactlyElementsOf(de(6, 21));
    }

    @Test
    @DisplayName("se recorta a 03:00-23:00: Despertar 02:00 y Dormir 23:30 dan de 03 a 23 (21 veces)")
    void recortaA03Y23() {
        assertThat(HorarioDelRadar.horasQueCaben(LocalTime.of(2, 0), LocalTime.of(23, 30))).containsExactlyElementsOf(de(3, 23));
    }

    @Test
    @DisplayName("sin Despertar, sin Dormir, o con Dormir igual o antes del Despertar: ninguna, no se inventa un horario")
    void sinHorarioNoHayHoras() {
        assertThat(HorarioDelRadar.horasQueCaben(null, LocalTime.of(22, 0))).isEmpty();
        assertThat(HorarioDelRadar.horasQueCaben(LocalTime.of(6, 0), null)).isEmpty();
        assertThat(HorarioDelRadar.horasQueCaben(LocalTime.of(22, 0), LocalTime.of(6, 0))).isEmpty();
        assertThat(HorarioDelRadar.horasQueCaben(LocalTime.of(6, 0), LocalTime.of(6, 0))).isEmpty();
        assertThat(HorarioDelRadar.horasQueCaben(LocalTime.of(5, 10), LocalTime.of(5, 50))).as("ninguna hora en punto").isEmpty();
    }

    // ─── el reparto parejo ────────────────────────────────────────────────

    @Test
    @DisplayName("6 de 17 (06 a 22) se reparten en 06, 09, 12, 16, 19 y 22")
    void seisDeDiecisiete() {
        assertThat(HorarioDelRadar.repartir(de(6, 22), 6)).containsExactly(6, 9, 12, 16, 19, 22);
    }

    @Test
    @DisplayName("los extremos: 1 deja la primera, 2 la primera y la ultima, y n igual o mayor que las que caben las deja todas")
    void extremosDelReparto() {
        assertThat(HorarioDelRadar.repartir(de(6, 22), 1)).containsExactly(6);
        assertThat(HorarioDelRadar.repartir(de(6, 22), 0)).containsExactly(6);
        assertThat(HorarioDelRadar.repartir(de(6, 22), 2)).containsExactly(6, 22);
        assertThat(HorarioDelRadar.repartir(de(6, 22), 17)).containsExactlyElementsOf(de(6, 22));
        assertThat(HorarioDelRadar.repartir(de(6, 22), 40)).containsExactlyElementsOf(de(6, 22));
        assertThat(HorarioDelRadar.repartir(List.of(), 3)).isEmpty();
    }

    @Test
    @DisplayName("para cualquier cantidad que cabe, salen exactamente n horas distintas, en orden, dentro del horario y con la primera y la ultima")
    void nHorasDistintasYParejas() {
        for (int total = 1; total <= 21; total++) {
            List<Integer> horas = de(3, 2 + total);
            for (int n = 2; n <= total; n++) {
                List<Integer> elegidas = HorarioDelRadar.repartir(horas, n);
                assertThat(elegidas).as("%d de %d", n, total).hasSize(n).doesNotHaveDuplicates().isSorted()
                        .startsWith(horas.getFirst()).endsWith(horas.getLast()).isSubsetOf(horas);
            }
        }
    }

    // ─── el dia resuelto ──────────────────────────────────────────────────

    private static DiaDelRadar plan(DiaDeMiRadar dia, LocalTime despertar, LocalTime dormir) {
        return HorarioDelRadar.planDelDia(dia, LocalDate.of(2026, 10, 31), despertar, dormir, true);
    }

    @Test
    @DisplayName("Automatico pregunta todas las horas que caben")
    void automatico() {
        DiaDelRadar dia = plan(DiaDeMiRadar.normal(6), LocalTime.of(5, 30), LocalTime.of(22, 30));

        assertThat(dia.horas()).containsExactlyElementsOf(de(6, 22));
        assertThat(dia.veces()).isEqualTo(17);
        assertThat(dia.maximo()).isEqualTo(17);
        assertThat(dia.motivo()).isNull();
    }

    @Test
    @DisplayName("Personalizado reparte las veces elegidas y las recorta a lo que cabe si el horario se achico")
    void personalizado() {
        DiaDeMiRadar seis = new DiaDeMiRadar(6, Modo.PERSONALIZADO, 6, false);

        assertThat(plan(seis, LocalTime.of(5, 30), LocalTime.of(22, 30)).horas()).containsExactly(6, 9, 12, 16, 19, 22);
        DiaDelRadar corto = plan(seis, LocalTime.of(9, 0), LocalTime.of(12, 30));
        assertThat(corto.horas()).containsExactly(9, 10, 11, 12);
        assertThat(corto.veces()).isEqualTo(4);
    }

    @Test
    @DisplayName("Personalizado sin veces guardadas propone 6, o menos si no caben")
    void porDefectoSeis() {
        DiaDeMiRadar dia = new DiaDeMiRadar(6, Modo.PERSONALIZADO, null, false);

        assertThat(dia.veces()).isEqualTo(6);
        assertThat(plan(dia, LocalTime.of(8, 0), LocalTime.of(10, 30)).veces()).isEqualTo(3);
    }

    @Test
    @DisplayName("un dia sin Despertar, sin Dormir o con Dormir antes del Despertar no tiene Radar, en los dos modos, y dice por que")
    void sinHorarioNoTieneRadar() {
        for (Modo modo : Modo.values()) {
            DiaDeMiRadar dia = new DiaDeMiRadar(6, modo, 6, false);

            assertThat(plan(dia, null, LocalTime.of(22, 0)).horas()).as("sin Despertar %s", modo).isEmpty();
            assertThat(plan(dia, null, LocalTime.of(22, 0)).motivo()).isEqualTo(MotivoSinHorario.SIN_DESPERTAR);
            assertThat(plan(dia, LocalTime.of(6, 0), null).motivo()).isEqualTo(MotivoSinHorario.SIN_DORMIR);
            assertThat(plan(dia, LocalTime.of(22, 0), LocalTime.of(6, 0)).motivo()).isEqualTo(MotivoSinHorario.HORARIO_INVALIDO);
            assertThat(plan(dia, LocalTime.of(22, 0), LocalTime.of(6, 0)).veces()).isZero();
        }
    }

    @Test
    @DisplayName("«Sin Radar» apaga el dia pero deja a la vista cuantas caben")
    void sinRadarDeLaPersona() {
        DiaDelRadar dia = plan(new DiaDeMiRadar(6, Modo.AUTOMATICO, null, true), LocalTime.of(6, 0), LocalTime.of(22, 0));

        assertThat(dia.horas()).isEmpty();
        assertThat(dia.veces()).isZero();
        assertThat(dia.maximo()).isEqualTo(16);
        assertThat(dia.motivo()).isNull();
    }

    // ─── hoy y lo pasado no se editan ─────────────────────────────────────

    @Test
    @DisplayName("un viernes lunes a viernes tienen candado y sabado y domingo se editan")
    void viernes() {
        LocalDate viernes = LocalDate.of(2026, 10, 30);

        for (DayOfWeek dia : DayOfWeek.values()) {
            assertThat(HorarioDelRadar.esEditable(dia, viernes)).as(dia.name()).isEqualTo(dia.getValue() > 5);
        }
    }

    @Test
    @DisplayName("un lunes solo el lunes tiene candado")
    void lunes() {
        LocalDate lunes = LocalDate.of(2026, 10, 26);

        assertThat(HorarioDelRadar.esEditable(DayOfWeek.MONDAY, lunes)).isFalse();
        assertThat(HorarioDelRadar.esEditable(DayOfWeek.TUESDAY, lunes)).isTrue();
        assertThat(HorarioDelRadar.esEditable(DayOfWeek.SUNDAY, lunes)).isTrue();
    }

    @Test
    @DisplayName("un domingo se muestra la semana siguiente: de lunes a sabado se edita, pero el domingo mismo no (cambiaria hoy)")
    void domingo() {
        LocalDate domingo = LocalDate.of(2026, 11, 1);

        for (DayOfWeek dia : DayOfWeek.values()) {
            assertThat(HorarioDelRadar.esEditable(dia, domingo)).as(dia.name()).isEqualTo(dia != DayOfWeek.SUNDAY);
        }
        assertThat(HorarioDelRadar.lunesDeLaSemanaMostrada(domingo)).isEqualTo(LocalDate.of(2026, 11, 2));
    }

    @Test
    @DisplayName("la semana que se muestra empieza el lunes de hoy, y cada dia se resuelve con la proxima fecha en que cae")
    void fechas() {
        LocalDate viernes = LocalDate.of(2026, 10, 30);

        assertThat(HorarioDelRadar.lunesDeLaSemanaMostrada(viernes)).isEqualTo(LocalDate.of(2026, 10, 26));
        assertThat(HorarioDelRadar.proximaFecha(5, viernes)).as("hoy mismo").isEqualTo(viernes);
        assertThat(HorarioDelRadar.proximaFecha(6, viernes)).isEqualTo(LocalDate.of(2026, 10, 31));
        assertThat(HorarioDelRadar.proximaFecha(1, viernes)).as("el lunes pasado cae el proximo").isEqualTo(LocalDate.of(2026, 11, 2));
    }

    // ─── registrar ────────────────────────────────────────────────────────

    @Test
    @DisplayName("los dias 0 a 7 no cambian: se registra a cualquier hora y la hora prevista es la local de 08 a 19")
    void primeraSemana() {
        for (int dia : new int[]{0, 1, 7}) {
            assertThat(HorarioDelRadar.puedeRegistrar(dia, false, List.of(), lima("2026-10-26T01:30:00"))).isTrue();
            assertThat(HorarioDelRadar.horaPrevista(dia, List.of(), lima("2026-10-26T10:15:00"))).hasValue(10);
            assertThat(HorarioDelRadar.horaPrevista(dia, List.of(), lima("2026-10-26T22:00:00"))).isEmpty();
        }
    }

    @Test
    @DisplayName("desde el dia 8 solo con Mi Radar encendido, un dia con Radar y de 03:00 a 23:59")
    void desdeElDia8() {
        List<Integer> horas = de(6, 22);

        assertThat(HorarioDelRadar.puedeRegistrar(8, true, horas, lima("2026-10-26T10:15:00"))).isTrue();
        assertThat(HorarioDelRadar.puedeRegistrar(8, true, horas, lima("2026-10-26T03:00:00"))).isTrue();
        assertThat(HorarioDelRadar.puedeRegistrar(8, true, horas, lima("2026-10-26T23:59:00"))).isTrue();
        assertThat(HorarioDelRadar.puedeRegistrar(8, true, horas, lima("2026-10-26T02:59:00"))).isFalse();
        assertThat(HorarioDelRadar.puedeRegistrar(8, false, horas, lima("2026-10-26T10:15:00"))).as("apagado").isFalse();
        assertThat(HorarioDelRadar.puedeRegistrar(8, true, List.of(), lima("2026-10-26T10:15:00"))).as("dia sin Radar").isFalse();
    }

    @Test
    @DisplayName("desde el dia 8 la hora prevista es la local solo si es una de las de hoy; si no, el registro es libre")
    void horaPrevistaDesdeElDia8() {
        assertThat(HorarioDelRadar.horaPrevista(10, de(6, 22), lima("2026-10-26T10:15:00"))).hasValue(10);
        assertThat(HorarioDelRadar.horaPrevista(10, List.of(6, 9, 12), lima("2026-10-26T10:15:00"))).isEmpty();
        assertThat(HorarioDelRadar.horaPrevista(10, de(6, 22), lima("2026-10-26T23:10:00"))).isEmpty();
    }
}

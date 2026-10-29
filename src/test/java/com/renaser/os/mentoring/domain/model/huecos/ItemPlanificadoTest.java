package com.renaser.os.mentoring.domain.model.huecos;

import com.renaser.os.shared.domain.TramoDelDia;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;

class ItemPlanificadoTest {

    private static final LocalDate LUNES = LocalDate.of(2026, 10, 26);
    private static final LocalDate MARTES = LUNES.plusDays(1);

    @Test
    @DisplayName("un habito dura 30 minutos por defecto")
    void habito() {
        ItemPlanificado item = ItemPlanificado.deHabito(LUNES, LocalTime.of(7, 0));

        assertThat(item.tramoEn(LUNES)).contains(new TramoDelDia(420, 450));
        assertThat(item.tramoEn(MARTES)).isEmpty();
    }

    @Test
    @DisplayName("una accion usa su inicio y su fin; sin fin, o con el fin antes del inicio, dura 30 minutos")
    void accion() {
        assertThat(ItemPlanificado.deAccion(LUNES, LocalTime.of(9, 0), LocalTime.of(10, 30)).tramoEn(LUNES))
                .contains(new TramoDelDia(540, 630));
        assertThat(ItemPlanificado.deAccion(LUNES, LocalTime.of(9, 0), null).tramoEn(LUNES))
                .contains(new TramoDelDia(540, 570));
        assertThat(ItemPlanificado.deAccion(LUNES, LocalTime.of(9, 0), LocalTime.of(8, 0)).tramoEn(LUNES))
                .contains(new TramoDelDia(540, 570));
        assertThat(ItemPlanificado.deAccion(LUNES, LocalTime.of(9, 0), LocalTime.of(9, 0)).tramoEn(LUNES))
                .contains(new TramoDelDia(540, 570));
    }

    @Test
    @DisplayName("un evento sin duracion dura una hora; con duracion, la suya")
    void evento() {
        LocalDateTime inicia = LUNES.atTime(10, 0);

        assertThat(ItemPlanificado.deEvento(inicia, null).tramoEn(LUNES)).contains(new TramoDelDia(600, 660));
        assertThat(ItemPlanificado.deEvento(inicia, LUNES.atTime(11, 30)).tramoEn(LUNES))
                .contains(new TramoDelDia(600, 690));
        assertThat(ItemPlanificado.deEvento(inicia, LUNES.atTime(9, 0)).tramoEn(LUNES))
                .contains(new TramoDelDia(600, 660));
    }

    @Test
    @DisplayName("un evento que cruza la medianoche se parte: cada dia cuenta su pedazo")
    void cruzaLaMedianoche() {
        ItemPlanificado item = ItemPlanificado.deEvento(LUNES.atTime(23, 0), MARTES.atTime(1, 30));

        assertThat(item.tramoEn(LUNES)).contains(new TramoDelDia(1380, TramoDelDia.FIN_DEL_DIA));
        assertThat(item.tramoEn(MARTES)).contains(new TramoDelDia(0, 90));
        assertThat(item.tramoEn(MARTES.plusDays(1))).isEmpty();
        assertThat(item.tramoEn(LUNES.minusDays(1))).isEmpty();
    }

    @Test
    @DisplayName("una accion de 30 minutos por defecto que empieza a las 23:50 deja su resto en la madrugada")
    void accionPorDefectoQueCruza() {
        ItemPlanificado item = ItemPlanificado.deAccion(LUNES, LocalTime.of(23, 50), null);

        assertThat(item.tramoEn(LUNES)).contains(new TramoDelDia(1430, TramoDelDia.FIN_DEL_DIA));
        assertThat(item.tramoEn(MARTES)).contains(new TramoDelDia(0, 20));
    }
}

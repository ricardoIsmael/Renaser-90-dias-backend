package com.renaser.os.mentoring.infrastructure.adapter.out.persistence.huecos;

import com.renaser.os.mentoring.domain.model.huecos.DiaDeHuecos;
import com.renaser.os.mentoring.domain.model.huecos.HabitoConHora;
import com.renaser.os.mentoring.domain.model.huecos.ItemPlanificado;
import com.renaser.os.mentoring.domain.model.huecos.LoReal;
import com.renaser.os.shared.domain.TramoDelDia;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * V120 dice que la foto semanal guarda tramos de tiempo y «ninguna etiqueta: ni título de hábito ni de evento». Desde
 * que un día calculado lleva los nombres de lo ocupado (spec 015, corregido 2026-10-29), esta prueba frena que se
 * cuelen en el jsonb, sin necesitar la base.
 */
class FotoDeHuecosSinTitulosTest {

    private static final LocalDate LUNES = LocalDate.of(2026, 10, 26);

    @Test
    @DisplayName("el dia que se guarda en la foto no lleva titulos de habitos, acciones ni eventos, ni lo cumplido")
    void laFotoNoGuardaNombres() {
        DiaDeHuecos calculado = DiaDeHuecos.calcular(LUNES,
                List.of(new HabitoConHora(HabitoConHora.DESPERTAR, LocalTime.of(5, 30), "Despertar secreto"),
                        new HabitoConHora(HabitoConHora.DORMIR, LocalTime.of(22, 30), "Dormir secreto")),
                List.of(ItemPlanificado.deEvento(LUNES.atTime(9, 0), LUNES.atTime(10, 0), "Cita con el medico"),
                        ItemPlanificado.deAccion(LUNES, LocalTime.of(11, 0), null, "Pagar la deuda")),
                List.of(new TramoDelDia(840, 900))).conLoReal(new LoReal(4, 3, 2));

        String json = JsonMapper.builder().build().writeValueAsString(FotosDeHuecosJdbcAdapter.DiaJson.de(calculado));

        assertThat(json).doesNotContain("secreto", "medico", "deuda", "titulo", "bloques", "detalle", "real");
        // Y se lee de vuelta como un dia de la foto: sin detalle.
        assertThat(FotosDeHuecosJdbcAdapter.DiaJson.de(calculado).aDominio().detalle()).isNull();
        assertThat(FotosDeHuecosJdbcAdapter.DiaJson.de(calculado).aDominio().ocupados()).isEqualTo(calculado.ocupados());
    }
}

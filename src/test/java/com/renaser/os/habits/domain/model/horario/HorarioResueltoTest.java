package com.renaser.os.habits.domain.model.horario;

import com.renaser.os.habits.domain.model.habito.HabitoId;
import com.renaser.os.habits.domain.model.habito.TipoDia;
import com.renaser.os.habits.domain.model.preferencia.PreferenciaHorario;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** "La preferencia gana campo por campo; el catalogo es el respaldo." */
class HorarioResueltoTest {

    private static final Instant AHORA = Instant.parse("2026-09-05T12:00:00Z");
    private static final HabitoId HABITO = HabitoId.of(UUID.randomUUID());
    private static final UserId PARTICIPANTE = UserId.of(UUID.randomUUID());

    private static HorarioHabito catalogo(LocalTime disparo, LocalTime limite) {
        return HorarioHabito.crear(HorarioHabitoId.of(UUID.randomUUID()), HABITO, 1, null, TipoDia.TODOS, disparo,
                limite, AHORA);
    }

    private static PreferenciaHorario preferencia(LocalTime disparo, LocalTime limite) {
        return PreferenciaHorario.rehydrate(PARTICIPANTE, HABITO, disparo, limite, false, null, AHORA, AHORA);
    }

    @Test
    @DisplayName("sin catalogo y sin preferencia -> sin horario")
    void sinNadaNoHayHorario() {
        HorarioResuelto resuelto = HorarioResuelto.de(null, null);

        assertThat(resuelto.sinHorario()).isTrue();
        assertThat(resuelto.horaDisparo()).isNull();
        assertThat(resuelto.horaLimite()).isNull();
    }

    @Test
    @DisplayName("sin preferencia manda el catalogo")
    void sinPreferenciaMandaElCatalogo() {
        HorarioResuelto resuelto = HorarioResuelto.de(catalogo(LocalTime.of(6, 0), LocalTime.of(8, 0)), null);

        assertThat(resuelto.horaDisparo()).isEqualTo(LocalTime.of(6, 0));
        assertThat(resuelto.horaLimite()).isEqualTo(LocalTime.of(8, 0));
        assertThat(resuelto.sinHorario()).isFalse();
    }

    @Test
    @DisplayName("la preferencia pisa al catalogo cuando fija las dos horas")
    void laPreferenciaPisaAlCatalogo() {
        HorarioResuelto resuelto = HorarioResuelto.de(catalogo(LocalTime.of(6, 0), LocalTime.of(8, 0)),
                preferencia(LocalTime.of(20, 0), LocalTime.of(22, 0)));

        assertThat(resuelto.horaDisparo()).isEqualTo(LocalTime.of(20, 0));
        assertThat(resuelto.horaLimite()).isEqualTo(LocalTime.of(22, 0));
    }

    @Test
    @DisplayName("el respaldo es POR CAMPO: mover solo el inicio no borra el cierre del catalogo")
    void elRespaldoEsPorCampo() {
        HorarioResuelto resuelto = HorarioResuelto.de(catalogo(LocalTime.of(6, 0), LocalTime.of(8, 0)),
                preferencia(LocalTime.of(7, 0), null));

        assertThat(resuelto.horaDisparo()).isEqualTo(LocalTime.of(7, 0));
        assertThat(resuelto.horaLimite()).isEqualTo(LocalTime.of(8, 0));
    }

    @Test
    @DisplayName("una preferencia sola, sin catalogo vigente, alcanza para tener horario")
    void laPreferenciaSolaAlcanza() {
        HorarioResuelto resuelto = HorarioResuelto.de(null, preferencia(LocalTime.of(5, 0), null));

        assertThat(resuelto.sinHorario()).isFalse();
        assertThat(resuelto.horaDisparo()).isEqualTo(LocalTime.of(5, 0));
        assertThat(resuelto.horaLimite()).isNull();
    }
}

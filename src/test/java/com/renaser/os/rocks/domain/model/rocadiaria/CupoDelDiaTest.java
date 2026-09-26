package com.renaser.os.rocks.domain.model.rocadiaria;

import com.renaser.os.rocks.domain.model.rocamaestra.EjeObjetivo;
import com.renaser.os.shared.domain.FixedClock;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** D-177: donde entra una accion agregada a un dia ya planificado. */
class CupoDelDiaTest {

    private static final FixedClock RELOJ = FixedClock.at(Instant.parse("2026-09-24T15:00:00Z"));
    private static final UserId APRENDIZ = UserId.of(UUID.randomUUID());
    private static final LocalDate JUEVES = LocalDate.of(2026, 9, 24);

    @Test
    @DisplayName("un eje sin acciones empieza en la 1 (la VERDE)")
    void ejeVacioEmpiezaEnUno() {
        assertThat(CupoDelDia.siguientePosicion(List.of(roca(EjeObjetivo.TRABAJO, 1)), EjeObjetivo.CUERPO)).isEqualTo(1);
    }

    @Test
    @DisplayName("va detras de las que el eje ya tiene; las de otro eje no cuentan")
    void vaDetrasDeLasDelEje() {
        List<RocaDiaria> delDia = List.of(roca(EjeObjetivo.CUERPO, 1), roca(EjeObjetivo.CUERPO, 2),
                roca(EjeObjetivo.TRABAJO, 1));

        assertThat(CupoDelDia.siguientePosicion(delDia, EjeObjetivo.CUERPO)).isEqualTo(3);
        assertThat(CupoDelDia.siguientePosicion(delDia, EjeObjetivo.TRABAJO)).isEqualTo(2);
    }

    @Test
    @DisplayName("un eje con sus 3 acciones rechaza con AXIS_FULL")
    void ejeCompleto() {
        List<RocaDiaria> delDia = List.of(roca(EjeObjetivo.CUERPO, 1), roca(EjeObjetivo.CUERPO, 2),
                roca(EjeObjetivo.CUERPO, 3));

        assertThatThrownBy(() -> CupoDelDia.siguientePosicion(delDia, EjeObjetivo.CUERPO))
                .isInstanceOf(IllegalStateException.class).hasMessageStartingWith("AXIS_FULL");
    }

    @Test
    @DisplayName("un dia con 9 acciones rechaza con DAY_FULL antes de mirar el eje")
    void diaCompleto() {
        List<RocaDiaria> delDia = new ArrayList<>();
        for (EjeObjetivo eje : EjeObjetivo.values()) {
            for (int posicion = 1; posicion <= 3; posicion++) {
                delDia.add(roca(eje, posicion));
            }
        }

        assertThatThrownBy(() -> CupoDelDia.siguientePosicion(delDia, EjeObjetivo.CUERPO))
                .isInstanceOf(IllegalStateException.class).hasMessageStartingWith("DAY_FULL");
    }

    private static RocaDiaria roca(EjeObjetivo eje, int posicion) {
        return RocaDiaria.planificar(RocaDiariaId.of(UUID.randomUUID()), APRENDIZ, JUEVES, posicion, "Accion",
                null, 5, false, eje, null, null, null, List.of(), RELOJ);
    }
}

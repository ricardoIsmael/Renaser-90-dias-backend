package com.renaser.os.habits.domain.model.radar;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Spec 012: las reglas de las horas de Mi Radar, para todos los dias y por dia de la semana (FR-003, FR-005a, SC-008).
 * Dominio puro, sin Spring.
 */
class MiRadarTest {

    @Test
    @DisplayName("apagado no tiene horas ni esta encendido")
    void apagado() {
        MiRadar apagado = MiRadar.apagado();

        assertThat(apagado.estaEncendido()).isFalse();
        assertThat(apagado.horas()).isEmpty();
        assertThat(apagado.dias()).isEmpty();
        for (DayOfWeek dia : DayOfWeek.values()) {
            assertThat(apagado.horasDelDia(dia)).as("apagado, %s", dia).isEmpty();
        }
    }

    @Test
    @DisplayName("encendido guarda las horas de menor a mayor, aunque lleguen desordenadas")
    void encendidoOrdenaLasHoras() {
        MiRadar miRadar = MiRadar.encendido(List.of(20, 9, 14));

        assertThat(miRadar.estaEncendido()).isTrue();
        assertThat(miRadar.horas()).containsExactly(9, 14, 20);
        assertThat(miRadar.dias()).isEmpty();
        assertThat(miRadar.horasDelDia(DayOfWeek.WEDNESDAY)).containsExactly(9, 14, 20);
    }

    @Test
    @DisplayName("los extremos valen: 3 y 23 en punto, y una sola hora")
    void extremos() {
        assertThat(MiRadar.encendido(List.of(3)).horas()).containsExactly(3);
        assertThat(MiRadar.encendido(List.of(23)).horas()).containsExactly(23);
        assertThat(MiRadar.encendido(List.of(3, 23)).horas()).containsExactly(3, 23);
    }

    @Test
    @DisplayName("hasta 12 horas: con 12 vale y con 13 es 400")
    void maximoDeDoce() {
        List<Integer> doce = IntStream.rangeClosed(8, 19).boxed().toList();
        List<Integer> trece = IntStream.rangeClosed(8, 20).boxed().toList();

        assertThat(MiRadar.encendido(doce).horas()).hasSize(12);
        assertThatThrownBy(() -> MiRadar.encendido(trece)).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Puedes elegir hasta 12 horas");
    }

    @Test
    @DisplayName("encendido sin ninguna hora (vacia o nula) es 400: «Elige al menos una hora»")
    void encendidoSinHoras() {
        assertThatThrownBy(() -> MiRadar.encendido(List.of())).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Elige al menos una hora");
        assertThatThrownBy(() -> MiRadar.encendido(null)).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Elige al menos una hora");
    }

    @Test
    @DisplayName("fuera de 03:00 a 23:00 es 400: la 2, la 24 y los negativos, sin madrugada despues de la medianoche")
    void fueraDeRango() {
        for (int hora : new int[]{0, 1, 2, 24, 25, -1, 100}) {
            assertThatThrownBy(() -> MiRadar.encendido(List.of(9, hora)))
                    .as("hora %d", hora)
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Las horas de Mi Radar van de 03:00 a 23:00, en punto");
        }
    }

    @Test
    @DisplayName("una hora repetida es 400")
    void repetidas() {
        assertThatThrownBy(() -> MiRadar.encendido(List.of(9, 14, 9))).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("No repitas la misma hora");
    }

    @Test
    @DisplayName("una hora nula dentro de la lista es 400 y no un NullPointerException (seria un 500)")
    void horaNula() {
        List<Integer> conNula = new ArrayList<>(Arrays.asList(9, null));

        assertThatThrownBy(() -> MiRadar.encendido(conNula)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("es inmutable: cambiar la lista que se paso no cambia a Mi Radar")
    void inmutable() {
        List<Integer> origen = new ArrayList<>(List.of(9, 14));
        MiRadar miRadar = MiRadar.encendido(origen);

        origen.add(20);

        assertThat(miRadar.horas()).containsExactly(9, 14);
        assertThatThrownBy(() -> miRadar.horas().add(20)).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("dos Mi Radar con las mismas horas son iguales, vengan en el orden que vengan")
    void igualdadPorHoras() {
        assertThat(MiRadar.encendido(List.of(14, 9))).isEqualTo(MiRadar.encendido(List.of(9, 14)));
        assertThat(MiRadar.apagado()).isEqualTo(new MiRadar(List.of(), List.of()));
    }

    // ── por dia de la semana (cuarta ronda) ─────────────────────────────────

    private static MiRadar.DiaPropio dia(int numero, Integer... horas) {
        return new MiRadar.DiaPropio(numero, Arrays.asList(horas));
    }

    @Test
    @DisplayName("un dia con horas propias usa las suyas; los demas, las de todos los dias")
    void horasDelDiaPropias() {
        MiRadar miRadar = MiRadar.encendido(List.of(9, 14, 20), List.of(dia(6, 11, 8), dia(1, 15)));

        assertThat(miRadar.horasDelDia(DayOfWeek.SATURDAY)).containsExactly(8, 11);
        assertThat(miRadar.horasDelDia(DayOfWeek.MONDAY)).containsExactly(15);
        for (DayOfWeek diaComun : new DayOfWeek[]{DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY,
                DayOfWeek.FRIDAY, DayOfWeek.SUNDAY}) {
            assertThat(miRadar.horasDelDia(diaComun)).as("%s sigue las de todos los dias", diaComun)
                    .containsExactly(9, 14, 20);
        }
    }

    @Test
    @DisplayName("«Sin Radar ese dia»: un dia con horas vacias no tiene ninguna, y Mi Radar sigue encendido")
    void diaSinRadar() {
        MiRadar miRadar = MiRadar.encendido(List.of(9, 14), List.of(dia(7)));

        assertThat(miRadar.estaEncendido()).isTrue();
        assertThat(miRadar.horasDelDia(DayOfWeek.SUNDAY)).isEmpty();
        assertThat(miRadar.horasDelDia(DayOfWeek.SATURDAY)).containsExactly(9, 14);
    }

    @Test
    @DisplayName("los dias quedan de lunes a domingo y con las horas de cada uno ordenadas, vengan como vengan")
    void diasOrdenados() {
        MiRadar miRadar = MiRadar.encendido(List.of(9), List.of(dia(7, 20, 10), dia(2, 14), dia(5)));

        assertThat(miRadar.dias()).extracting(MiRadar.DiaPropio::dia).containsExactly(2, 5, 7);
        assertThat(miRadar.dias().get(2).horas()).containsExactly(10, 20);
    }

    @Test
    @DisplayName("el dia va de 1 (lunes) a 7 (domingo): 0, 8 y negativos son 400")
    void diaFueraDeRango() {
        for (int numero : new int[]{0, 8, -1, 100}) {
            assertThatThrownBy(() -> dia(numero, 9)).as("dia %d", numero)
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("El día de Mi Radar va de 1 (lunes) a 7 (domingo)");
        }
        assertThat(dia(1, 9).dia()).isEqualTo(1);
        assertThat(dia(7, 9).dia()).isEqualTo(7);
    }

    @Test
    @DisplayName("un dia repetido es 400")
    void diaRepetido() {
        assertThatThrownBy(() -> MiRadar.encendido(List.of(9), List.of(dia(6, 11), dia(6, 12))))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("No repitas el mismo día");
    }

    @Test
    @DisplayName("las horas propias de un dia tienen las mismas reglas: hasta 12, de 03 a 23, en punto y sin repetir")
    void horasPropiasConLasMismasReglas() {
        List<Integer> trece = IntStream.rangeClosed(8, 20).boxed().toList();

        assertThat(dia(3, 3, 23).horas()).containsExactly(3, 23);
        assertThat(new MiRadar.DiaPropio(3, IntStream.rangeClosed(8, 19).boxed().toList()).horas()).hasSize(12);
        assertThatThrownBy(() -> new MiRadar.DiaPropio(3, trece)).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Puedes elegir hasta 12 horas");
        assertThatThrownBy(() -> dia(3, 2)).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Las horas de Mi Radar van de 03:00 a 23:00, en punto");
        assertThatThrownBy(() -> dia(3, 24)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> dia(3, 9, 9)).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("No repitas la misma hora");
        assertThatThrownBy(() -> new MiRadar.DiaPropio(3, new ArrayList<>(Arrays.asList(9, null))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new MiRadar.DiaPropio(3, null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("las horas de un dia propio pueden coincidir con las de otro dia o con las de todos los dias")
    void horasIgualesEntreDiasValen() {
        MiRadar miRadar = MiRadar.encendido(List.of(9, 14), List.of(dia(1, 9, 14), dia(2, 9, 14)));

        assertThat(miRadar.dias()).hasSize(2);
    }

    @Test
    @DisplayName("sin horas para todos los dias no hay dias propios: encendido con dias pero sin horas es 400")
    void diasSinHorasDeTodosLosDias() {
        assertThatThrownBy(() -> MiRadar.encendido(List.of(), List.of(dia(6, 11))))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("Elige al menos una hora");
        assertThatThrownBy(() -> new MiRadar(List.of(), List.of(dia(6, 11))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("los dias nulos, o con un dia nulo adentro, son 400 y no un NullPointerException (seria un 500)")
    void diasNulos() {
        List<MiRadar.DiaPropio> conNulo = new ArrayList<>(Arrays.asList(dia(1, 9), null));

        assertThatThrownBy(() -> MiRadar.encendido(List.of(9), null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> MiRadar.encendido(List.of(9), conNulo)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("los dias son inmutables: cambiar la lista que se paso no cambia a Mi Radar")
    void diasInmutables() {
        List<MiRadar.DiaPropio> origen = new ArrayList<>(List.of(dia(6, 11)));
        MiRadar miRadar = MiRadar.encendido(List.of(9), origen);

        origen.add(dia(7, 12));

        assertThat(miRadar.dias()).hasSize(1);
        assertThatThrownBy(() -> miRadar.dias().add(dia(7, 12))).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("la igualdad cuenta los dias: las mismas horas con distintos dias propios no son iguales")
    void igualdadConDias() {
        assertThat(MiRadar.encendido(List.of(9), List.of(dia(6, 11), dia(1, 8))))
                .isEqualTo(MiRadar.encendido(List.of(9), List.of(dia(1, 8), dia(6, 11))));
        assertThat(MiRadar.encendido(List.of(9), List.of(dia(6, 11)))).isNotEqualTo(MiRadar.encendido(List.of(9)));
    }
}

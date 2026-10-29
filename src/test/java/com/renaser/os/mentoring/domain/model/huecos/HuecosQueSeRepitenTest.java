package com.renaser.os.mentoring.domain.model.huecos;

import com.renaser.os.shared.domain.TramoDelDia;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** «Un hueco que se repite»: libre durante al menos 30 minutos en al menos 3 días de la semana (FR-016). */
class HuecosQueSeRepitenTest {

    private static final LocalDate LUNES = LocalDate.of(2026, 10, 26);

    private static TramoDelDia tramo(int horaDesde, int horaHasta) {
        return new TramoDelDia(horaDesde * 60, horaHasta * 60);
    }

    /** Un día de la semana (0 = lunes) con un solo bloque ocupado, que deja libre lo demás. */
    private static DiaDeHuecos conLibre(int dia, TramoDelDia... libres) {
        return new DiaDeHuecos(LUNES.plusDays(dia), VentanaDelDia.diaEntero(), List.of(), List.of(libres), true);
    }

    private static DiaDeHuecos enBlanco(int dia) {
        return new DiaDeHuecos(LUNES.plusDays(dia), VentanaDelDia.diaEntero(), List.of(),
                List.of(tramo(0, 24)), false);
    }

    @Test
    @DisplayName("el ejemplo del contrato: 15:00 a 17:00 libre lunes, miercoles y jueves")
    void ejemploDelContrato() {
        List<DiaDeHuecos> semana = List.of(conLibre(0, tramo(15, 17)), conLibre(1, tramo(8, 9)),
                conLibre(2, tramo(14, 18)), conLibre(3, tramo(15, 17)), conLibre(4), conLibre(5), conLibre(6));

        List<HuecoQueSeRepite> repetidos = HuecosQueSeRepiten.de(semana);

        assertThat(repetidos).containsExactly(new HuecoQueSeRepite(tramo(15, 17), List.of(1, 3, 4)));
    }

    @Test
    @DisplayName("con menos de 3 dias con plan no sale ninguno, aunque haya huecos iguales")
    void pocosDiasConPlan() {
        List<DiaDeHuecos> semana = List.of(conLibre(0, tramo(15, 17)), conLibre(1, tramo(15, 17)), enBlanco(2),
                enBlanco(3), enBlanco(4), enBlanco(5), enBlanco(6));

        assertThat(HuecosQueSeRepiten.de(semana)).isEmpty();
    }

    @Test
    @DisplayName("un dia en blanco no cuenta como libre: solo los dias con plan se apoyan")
    void diaEnBlancoNoCuenta() {
        List<DiaDeHuecos> semana = List.of(conLibre(0, tramo(15, 17)), conLibre(1, tramo(15, 17)),
                conLibre(2, tramo(15, 17)), enBlanco(3), enBlanco(4), enBlanco(5), enBlanco(6));

        assertThat(HuecosQueSeRepiten.de(semana))
                .containsExactly(new HuecoQueSeRepite(tramo(15, 17), List.of(1, 2, 3)));
    }

    @Test
    @DisplayName("solo en dos dias no se repite")
    void enDosDias() {
        List<DiaDeHuecos> semana = List.of(conLibre(0, tramo(15, 17)), conLibre(1, tramo(15, 17)),
                conLibre(2, tramo(8, 9)), conLibre(3), conLibre(4), conLibre(5), conLibre(6));

        assertThat(HuecosQueSeRepiten.de(semana)).isEmpty();
    }

    @Test
    @DisplayName("el tramo comun es la interseccion: libre 14-18, 15-17 y 15-19 da 15-17 en los tres")
    void interseccion() {
        List<DiaDeHuecos> semana = List.of(conLibre(0, tramo(14, 18)), conLibre(2, tramo(15, 17)),
                conLibre(4, tramo(15, 19)), conLibre(1), conLibre(3), conLibre(5), conLibre(6));

        List<HuecoQueSeRepite> repetidos = HuecosQueSeRepiten.de(semana);

        assertThat(repetidos).containsExactly(new HuecoQueSeRepite(tramo(15, 17), List.of(1, 3, 5)));
    }

    @Test
    @DisplayName("cada tramo que sale esta libre entero en todos los dias que nombra y mide al menos 30 minutos")
    void cadaTramoEstaLibreEnSusDias() {
        List<DiaDeHuecos> semana = new ArrayList<>();
        for (int dia = 0; dia < 7; dia++) {
            semana.add(conLibre(dia, tramo(6 + dia % 3, 9 + dia % 4), tramo(14, 15 + dia % 2)));
        }

        for (HuecoQueSeRepite repetido : HuecosQueSeRepiten.de(semana)) {
            assertThat(repetido.tramo().minutos()).isGreaterThanOrEqualTo(ReglasDeHuecos.MINUTOS_DEL_HUECO_UTIL);
            assertThat(repetido.dias()).hasSizeGreaterThanOrEqualTo(ReglasDeHuecos.DIAS_PARA_QUE_SE_REPITA);
            for (int diaIso : repetido.dias()) {
                DiaDeHuecos dia = semana.get(diaIso - 1);
                assertThat(dia.huecos()).anyMatch(hueco -> hueco.desde() <= repetido.tramo().desde()
                        && hueco.hasta() >= repetido.tramo().hasta());
            }
        }
        assertThat(HuecosQueSeRepiten.de(semana)).isNotEmpty();
    }

    @Test
    @DisplayName("un tramo menor al minimo no sale aunque se repita")
    void tramoMuyCorto() {
        TramoDelDia corto = new TramoDelDia(LocalTime.of(15, 0).toSecondOfDay() / 60, 15 * 60 + 20);
        List<DiaDeHuecos> semana = List.of(conLibre(0, corto), conLibre(1, corto), conLibre(2, corto), conLibre(3),
                conLibre(4), conLibre(5), conLibre(6));

        assertThat(HuecosQueSeRepiten.de(semana)).isEmpty();
    }
}

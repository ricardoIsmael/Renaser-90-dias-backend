package com.renaser.os.habits.application.politica;

import com.renaser.os.habits.domain.model.medicion.DispositivoDeMedicion;
import com.renaser.os.habits.domain.model.medicion.LecturaAutomatica;
import com.renaser.os.habits.domain.model.medicion.MedicionDiaria;
import com.renaser.os.habits.domain.model.politica.DecisionPolitica;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/** Spec 037 (D-310): lo que decide el servidor sobre una lectura de Health Connect, sin Spring ni base. */
class PoliticaKilometrosAutomaticaTest {

    private static final Instant LEIDA = Instant.parse("2026-11-04T22:10:00Z");
    private static final BigDecimal META = PoliticaKilometros.META_POR_DEFECTO_KM;

    private final PoliticaKilometros politica = new PoliticaKilometros();

    private static MedicionDiaria lectura(String km, Integer pasos) {
        return MedicionDiaria.automatica(new BigDecimal(km),
                new LecturaAutomatica(pasos, "Galaxy Watch", DispositivoDeMedicion.RELOJ, LEIDA, false));
    }

    private String motivoDe(DecisionPolitica decision) {
        assertThat(decision).isInstanceOf(DecisionPolitica.NoProcede.class);
        return ((DecisionPolitica.NoProcede) decision).motivo();
    }

    // spec 037 FR-012
    @Test
    @DisplayName("la meta por defecto son 5 km y una meta fijada por Administración manda sobre ella")
    void laMetaPorDefectoSonCincoKm() {
        assertThat(PoliticaKilometros.metaEfectiva(null)).isEqualByComparingTo("5");
        assertThat(PoliticaKilometros.metaEfectiva(new BigDecimal("3.5"))).isEqualByComparingTo("3.5");
        assertThat(politica.metaPorDefectoDeLaMedicion()).contains(META);
    }

    @Test
    @DisplayName("una meta cabe en la columna si está entre 0,5 y 50 km")
    void laMetaTieneRango() {
        assertThat(PoliticaKilometros.metaValida(new BigDecimal("0.5"))).isTrue();
        assertThat(PoliticaKilometros.metaValida(new BigDecimal("50"))).isTrue();
        assertThat(PoliticaKilometros.metaValida(new BigDecimal("0.49"))).isFalse();
        assertThat(PoliticaKilometros.metaValida(new BigDecimal("50.01"))).isFalse();
        assertThat(PoliticaKilometros.metaValida(null)).isFalse();
    }

    @Test
    @DisplayName("llegar justo a la meta procede; faltar un poco dice cuánto es la meta")
    void laMetaSeExige() {
        assertThat(politica.validarMedicionAutomatica(lectura("5.00", 7000), META))
                .isInstanceOf(DecisionPolitica.Procede.class);
        assertThat(politica.validarMedicionAutomatica(lectura("5.40", 7120), META))
                .isInstanceOf(DecisionPolitica.Procede.class);
        assertThat(motivoDe(politica.validarMedicionAutomatica(lectura("4.99", 7000), META)))
                .isEqualTo("Todavía no llegas a tu meta de 5 km.");
    }

    @Test
    @DisplayName("la meta de 6,5 km se lee con coma, como se escribe en el teléfono")
    void laMetaSeLeeConComa() {
        assertThat(motivoDe(politica.validarMedicionAutomatica(lectura("6.00", 8000), new BigDecimal("6.50"))))
                .isEqualTo("Todavía no llegas a tu meta de 6,5 km.");
    }

    // spec 037 FR-008
    @Test
    @DisplayName("más de 60 km o de 150 000 pasos en un día no se registra solo: se sube la captura")
    void losTopes() {
        assertThat(motivoDe(politica.validarMedicionAutomatica(lectura("60.01", 80000), META)))
                .contains("más de 60 km").contains("Sube tu captura");
        assertThat(politica.validarMedicionAutomatica(lectura("60.00", 80000), META))
                .isInstanceOf(DecisionPolitica.Procede.class);
        assertThat(motivoDe(politica.validarMedicionAutomatica(lectura("40.00", 150_001), META)))
                .contains("Sube tu captura");
        assertThat(politica.validarMedicionAutomatica(lectura("40.00", 150_000), META))
                .isInstanceOf(DecisionPolitica.Procede.class);
    }

    @Test
    @DisplayName("cero km no procede, y una medición escrita a mano no entra por este camino")
    void ceroYManual() {
        assertThat(motivoDe(politica.validarMedicionAutomatica(lectura("0.00", 0), new BigDecimal("0.5"))))
                .contains("más que cero");
        assertThat(motivoDe(politica.validarMedicionAutomatica(MedicionDiaria.manualSiHay(new BigDecimal("6")), META)))
                .contains("no viene de tus pasos");
    }

    @Test
    @DisplayName("un reloj que da solo la distancia (sin pasos) procede")
    void sinPasos() {
        assertThat(politica.validarMedicionAutomatica(lectura("7.00", null), META))
                .isInstanceOf(DecisionPolitica.Procede.class);
    }

    @Test
    @DisplayName("lo normal no queda por revisar: 5,4 km con 7 120 pasos son 76 cm por paso")
    void loNormalNoSeRevisa() {
        assertThat(politica.conRevision(lectura("5.40", 7120)).lectura().porRevisar()).isFalse();
        assertThat(politica.conRevision(lectura("7.00", null)).lectura().porRevisar()).isFalse();
        assertThat(politica.conRevision(lectura("30.00", 40000)).lectura().porRevisar()).isFalse();
    }

    // spec 037 FR-010
    @Test
    @DisplayName("más de 30 km, o una zancada imposible, se acepta pero queda por revisar")
    void loDudosoSeMarca() {
        assertThat(politica.conRevision(lectura("30.01", 40000)).lectura().porRevisar()).isTrue();
        // 5 km con 100 pasos: 50 m por paso (una bici, un auto o datos de otra app)
        assertThat(politica.conRevision(lectura("5.00", 100)).lectura().porRevisar()).isTrue();
        // 5 km con 20 000 pasos: 25 cm por paso
        assertThat(politica.conRevision(lectura("5.00", 20000)).lectura().porRevisar()).isTrue();
    }

    @Test
    @DisplayName("marcar por revisar conserva el resto de la lectura y el valor")
    void conservaLoDemas() {
        MedicionDiaria revisada = politica.conRevision(lectura("5.00", 100));

        assertThat(revisada.valor()).isEqualByComparingTo("5.00");
        assertThat(revisada.lectura().fuente()).isEqualTo("Galaxy Watch");
        assertThat(revisada.lectura().dispositivo()).isEqualTo(DispositivoDeMedicion.RELOJ);
        assertThat(revisada.lectura().leidaEn()).isEqualTo(LEIDA);
        assertThat(revisada.lectura().pasos()).isEqualTo(100);
    }
}

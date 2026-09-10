package com.renaser.os.community.domain.model.celula;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * El periodo de un grupo armado por el administrador.
 *
 * <p>Casi todo lo que se prueba aquí es el extremo de ARRIBA, porque es donde este periodo se
 * comporta al revés que {@code PeriodoAsignacion} — el otro "periodo" del mismo módulo, que cierra
 * exclusivo. Dos convenios opuestos conviviendo es exactamente el sitio donde alguien va a asumir
 * el que no toca.
 */
class PeriodoGrupoTest {

    private static final PeriodoGrupo SEPTIEMBRE = new PeriodoGrupo(
            LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30));

    @Test
    @DisplayName("El ULTIMO dia todavia es del grupo: 'del 1 al 30' incluye el 30 entero")
    void elUltimoDiaCuenta() {
        assertThat(SEPTIEMBRE.contiene(LocalDate.of(2026, 9, 30))).isTrue();
        assertThat(SEPTIEMBRE.vencidoEn(LocalDate.of(2026, 9, 30)))
                .as("el 30 NO esta vencido; con el convenio de PeriodoAsignacion lo estaria")
                .isFalse();
        assertThat(SEPTIEMBRE.vencidoEn(LocalDate.of(2026, 10, 1))).isTrue();
    }

    @Test
    @DisplayName("El primer dia tambien: el grupo arranca el 1, no el 2")
    void elPrimerDiaCuenta() {
        assertThat(SEPTIEMBRE.contiene(LocalDate.of(2026, 9, 1))).isTrue();
        assertThat(SEPTIEMBRE.futuroEn(LocalDate.of(2026, 9, 1))).isFalse();
        assertThat(SEPTIEMBRE.futuroEn(LocalDate.of(2026, 8, 31))).isTrue();
    }

    /**
     * Un grupo programado para octubre no es un grupo vencido, y confundirlos lo escondería de la
     * app del alumno antes de existir. Son dos preguntas distintas y por eso hay dos métodos.
     */
    @Test
    @DisplayName("Futuro y vencido no son lo mismo")
    void futuroNoEsVencido() {
        LocalDate hoy = LocalDate.of(2026, 8, 15);
        assertThat(SEPTIEMBRE.futuroEn(hoy)).isTrue();
        assertThat(SEPTIEMBRE.vencidoEn(hoy)).isFalse();
    }

    @Test
    @DisplayName("Siete dias desde el lunes terminan el DOMINGO, no el lunes siguiente")
    void sieteDiasSonSiete() {
        PeriodoGrupo recepcion = PeriodoGrupo.recepcionDesde(LocalDate.of(2026, 9, 7)); // lunes

        assertThat(recepcion.fin())
                .as("el primer dia ya cuenta: sin el ajuste el grupo duraria ocho")
                .isEqualTo(LocalDate.of(2026, 9, 13));
        assertThat(recepcion.duracionEnDias()).isEqualTo(7);
    }

    @Test
    @DisplayName("El mes se toma entero, y febrero tambien es un mes")
    void elMesSeTomaEntero() {
        assertThat(PeriodoGrupo.mesDe(LocalDate.of(2026, 2, 14)).fin())
                .as("28 en un anio normal, no 30")
                .isEqualTo(LocalDate.of(2026, 2, 28));
        assertThat(PeriodoGrupo.mesDe(LocalDate.of(2024, 2, 14)).fin())
                .as("29 en bisiesto")
                .isEqualTo(LocalDate.of(2024, 2, 29));
        assertThat(PeriodoGrupo.mesDe(LocalDate.of(2026, 9, 14)).duracionEnDias()).isEqualTo(30);
    }

    /**
     * Lo que lee el administrador en el aviso. El ultimo dia tiene que decir "queda 1", no "quedan
     * 0": el grupo todavia tiene la jornada entera por delante y un cero suena a que ya se acabo.
     */
    @Test
    @DisplayName("Los dias restantes cuentan HOY: el ultimo dia queda 1, no 0")
    void losDiasRestantesCuentanHoy() {
        assertThat(SEPTIEMBRE.diasRestantesEn(LocalDate.of(2026, 9, 30))).isEqualTo(1);
        assertThat(SEPTIEMBRE.diasRestantesEn(LocalDate.of(2026, 9, 26))).isEqualTo(5);
        assertThat(SEPTIEMBRE.diasRestantesEn(LocalDate.of(2026, 10, 2)))
                .as("ya vencido: negativo, no cero")
                .isNegative();
    }

    @Test
    @DisplayName("Un periodo que termina antes de empezar no se construye")
    void noSeConstruyeAlReves() {
        assertThatThrownBy(() -> new PeriodoGrupo(LocalDate.of(2026, 9, 30), LocalDate.of(2026, 9, 1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("termina antes de empezar");
        assertThatThrownBy(() -> PeriodoGrupo.desde(LocalDate.of(2026, 9, 1), 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Un grupo de un solo dia es valido: empieza y termina el mismo dia")
    void unSoloDiaEsValido() {
        PeriodoGrupo unDia = PeriodoGrupo.desde(LocalDate.of(2026, 9, 5), 1);

        assertThat(unDia.inicio()).isEqualTo(unDia.fin());
        assertThat(unDia.contiene(LocalDate.of(2026, 9, 5))).isTrue();
        assertThat(unDia.duracionEnDias()).isEqualTo(1);
    }
}

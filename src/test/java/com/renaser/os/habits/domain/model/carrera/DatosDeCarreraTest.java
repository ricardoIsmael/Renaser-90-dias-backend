package com.renaser.os.habits.domain.model.carrera;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import static com.renaser.os.habits.domain.model.carrera.CarrerasDePrueba.INICIO;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Spec 042: los topes de entrada de una carrera (FR-026), su huella (FR-019) y su día en la zona de la persona (FR-021). */
class DatosDeCarreraTest {

    private static final UUID CLAVE = UUID.fromString("6f1c2c1e-3b1a-4c0a-9a55-2f0f8d1d7a10");
    private static final ZoneId LIMA = ZoneId.of("America/Lima");

    private static DatosDeCarrera con(Instant inicio, Instant fin, int activa, String metros, Integer subida, Integer pasos,
                                      Integer calorias, List<Tramo> tramos, Integer sinGps) {
        return new DatosDeCarrera(CLAVE, inicio, fin, activa, new BigDecimal(metros), subida, pasos, calorias, tramos, sinGps, null,
                null);
    }

    // spec 042 FR-026
    @Test
    @DisplayName("una carrera de menos de 100 m o de menos de 1 minuto activo no se acepta (decisión del dueño)")
    void carreraMuyCorta() {
        assertThatThrownBy(() -> con(INICIO, INICIO.plusSeconds(600), 300, "99.9", null, null, null, List.of(), null))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("menos de 100 m");
        assertThatThrownBy(() -> con(INICIO, INICIO.plusSeconds(600), 59, "500", null, null, null, List.of(), null))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("menos de 1 minuto");
        assertThat(con(INICIO, INICIO.plusSeconds(600), 60, "100", null, null, null, List.of(), null).kilometros())
                .isEqualByComparingTo("0.10");
    }

    // spec 042 FR-026
    @Test
    @DisplayName("los topes de entrada: 12 horas, 100 km, pasos, calorías, subida, metros sin GPS y tramos seguidos")
    void topesDeEntrada() {
        Instant fin = INICIO.plusSeconds(3600);
        assertThatThrownBy(() -> con(INICIO, INICIO.plusSeconds(12 * 3600 + 1), 3600, "5000", null, null, null, List.of(), null))
                .hasMessageContaining("12 horas");
        assertThatThrownBy(() -> con(INICIO, INICIO, 60, "5000", null, null, null, List.of(), null))
                .hasMessageContaining("terminar después");
        assertThatThrownBy(() -> con(INICIO, fin, 3700, "5000", null, null, null, List.of(), null))
                .hasMessageContaining("tiempo activo");
        assertThatThrownBy(() -> con(INICIO, fin, 3600, "100000.1", null, null, null, List.of(), null))
                .hasMessageContaining("más de 100 km");
        assertThatThrownBy(() -> con(INICIO, fin, 3600, "5000", null, 150_001, null, List.of(), null))
                .hasMessageContaining("pasos");
        assertThatThrownBy(() -> con(INICIO, fin, 3600, "5000", null, null, 6001, List.of(), null))
                .hasMessageContaining("calorías");
        assertThatThrownBy(() -> con(INICIO, fin, 3600, "5000", 5001, null, null, List.of(), null))
                .hasMessageContaining("subida");
        assertThatThrownBy(() -> con(INICIO, fin, 3600, "5000", null, null, null, List.of(), 5001))
                .hasMessageContaining("metros sin GPS");
        assertThatThrownBy(() -> con(INICIO, fin, 3600, "5000", null, null, null, List.of(new Tramo(2, 300)), null))
                .hasMessageContaining("seguidos desde el km 1");
        assertThatThrownBy(() -> new Tramo(1, 0)).hasMessageContaining("más de cero segundos");
    }

    // spec 042 FR-026
    @Test
    @DisplayName("la ruta: entre 2 y 3000 puntos, hasta 100 KB y legible")
    void topesDeLaRuta() {
        assertThatThrownBy(() -> new RutaDeCarrera("_p~iF~ps|U", 3001)).hasMessageContaining("3000 puntos");
        assertThatThrownBy(() -> new RutaDeCarrera("x".repeat(RutaDeCarrera.BYTES_MAXIMOS + 1), 10))
                .hasMessageContaining("demasiado larga");
        assertThatThrownBy(() -> new RutaDeCarrera("_p~iF~ps|U_ulL", 2)).hasMessageContaining("no se puede leer");
        assertThat(new RutaDeCarrera("_p~iF~ps|U_ulLnnqC_mqNvxq`@", 3).largoEnMetros()).isBetween(700_000.0, 800_000.0);
    }

    // spec 042 FR-019
    @Test
    @DisplayName("la huella es la misma para el mismo contenido y cambia con cualquier dato")
    void huella() {
        DatosDeCarrera una = CarrerasDePrueba.de(CLAVE, "5000", 1800);
        DatosDeCarrera igual = CarrerasDePrueba.de(CLAVE, "5000.04", 1800);
        DatosDeCarrera otraDistancia = CarrerasDePrueba.de(CLAVE, "5100", 1800);
        DatosDeCarrera otraClave = CarrerasDePrueba.de(UUID.randomUUID(), "5000", 1800);

        assertThat(una.huella()).hasSize(64).matches("[0-9a-f]{64}").isEqualTo(igual.huella());
        assertThat(una.huella()).isNotEqualTo(otraDistancia.huella()).isNotEqualTo(otraClave.huella());
    }

    // spec 042 FR-021
    @Test
    @DisplayName("el día es el del inicio en la zona de la persona, aunque termine pasada la medianoche")
    void elDiaEsElDelInicio() {
        Instant once50DeLaNoche = Instant.parse("2026-11-06T04:50:00Z"); // 23:50 del 5 en Lima
        DatosDeCarrera cruzaLaMedianoche = con(once50DeLaNoche, once50DeLaNoche.plusSeconds(1800), 1700, "4000", null, null,
                null, List.of(), null);

        assertThat(cruzaLaMedianoche.fechaEn(LIMA)).isEqualTo(LocalDate.of(2026, 11, 5));
        assertThat(cruzaLaMedianoche.fechaEn(ZoneId.of("UTC"))).isEqualTo(LocalDate.of(2026, 11, 6));
    }
}

package com.renaser.os.habits.application.politica;

import com.renaser.os.habits.domain.model.carrera.CarrerasDePrueba;
import com.renaser.os.habits.domain.model.carrera.DatosDeCarrera;
import com.renaser.os.habits.domain.model.carrera.EvaluacionDeCarrera;
import com.renaser.os.habits.domain.model.carrera.RutaDeCarrera;
import com.renaser.os.habits.domain.model.carrera.Tramo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static com.renaser.os.habits.domain.model.carrera.CarrerasDePrueba.INICIO;
import static org.assertj.core.api.Assertions.assertThat;

/** Spec 042: si una carrera cuenta para el hábito (FR-024) y si queda «por revisar» (FR-025), con las decisiones del dueño. */
class PoliticaKilometrosCarreraTest {

    private final PoliticaKilometros politica = new PoliticaKilometros();
    private final Instant ahora = INICIO.plusSeconds(4 * 3600);

    private DatosDeCarrera carrera(String metros, int segundos, Integer pasos, List<Tramo> tramos, RutaDeCarrera ruta,
                                   Integer sinGps) {
        return new DatosDeCarrera(UUID.randomUUID(), INICIO, INICIO.plusSeconds(segundos + 30L), segundos, new BigDecimal(metros),
                null, pasos, null, tramos, sinGps, ruta, null);
    }

    // spec 042 FR-023, FR-024
    @Test
    @DisplayName("una carrera normal cuenta, sin meta: 300 m caminando también cuentan (decisión del dueño)")
    void cuentaSinMeta() {
        EvaluacionDeCarrera cinco = politica.evaluarCarrera(CarrerasDePrueba.cincoKm(), ahora);
        EvaluacionDeCarrera corta = politica.evaluarCarrera(carrera("300", 240, 400, List.of(), null, null), ahora);

        assertThat(cinco.cuenta()).isTrue();
        assertThat(cinco.porRevisar()).isFalse();
        assertThat(corta.cuenta()).isTrue();
    }

    // spec 042 FR-024
    @Test
    @DisplayName("no cuenta con más de 25 km/h de media, con más de 60 km ni si termina en el futuro")
    void loQueNoCuenta() {
        EvaluacionDeCarrera rapida = politica.evaluarCarrera(carrera("10000", 1400, null, List.of(), null, null), ahora);
        EvaluacionDeCarrera justo = politica.evaluarCarrera(carrera("10000", 1440, null, List.of(), null, null), ahora);
        EvaluacionDeCarrera larga = politica.evaluarCarrera(carrera("60010", 30000, null, List.of(), null, null),
                INICIO.plusSeconds(40_000));
        EvaluacionDeCarrera futura = politica.evaluarCarrera(CarrerasDePrueba.cincoKm(), INICIO.plusSeconds(1800 + 60 - 301));

        assertThat(rapida.cuenta()).isFalse();
        assertThat(rapida.motivoNoCuenta()).contains("25 km/h");
        assertThat(justo.cuenta()).as("25 km/h justos cuentan").isTrue();
        assertThat(larga.cuenta()).isFalse();
        assertThat(larga.motivoNoCuenta()).contains("60 km");
        assertThat(futura.cuenta()).isFalse();
        assertThat(futura.motivoNoCuenta()).contains("hora de tu teléfono");
    }

    // spec 042 FR-025
    @Test
    @DisplayName("un km a más de 22 km/h la deja por revisar; el último tramo parcial no se mide")
    void tramoMuyRapido() {
        List<Tramo> conUnoRapido = List.of(new Tramo(1, 360), new Tramo(2, 160), new Tramo(3, 360));
        List<Tramo> parcialRapido = List.of(new Tramo(1, 360), new Tramo(2, 360), new Tramo(3, 20));

        EvaluacionDeCarrera dudosa = politica.evaluarCarrera(carrera("3000", 880, null, conUnoRapido, null, null), ahora);
        EvaluacionDeCarrera normal = politica.evaluarCarrera(carrera("2100", 740, null, parcialRapido, null, null), ahora);

        assertThat(dudosa.cuenta()).isTrue();
        assertThat(dudosa.porRevisar()).isTrue();
        assertThat(dudosa.motivoRevision()).contains("22 km/h");
        assertThat(normal.porRevisar()).isFalse();
    }

    // spec 042 FR-025
    @Test
    @DisplayName("una zancada imposible o una ruta que no cuadra con la distancia la dejan por revisar, sin quitarle nada")
    void zancadaYRuta() {
        EvaluacionDeCarrera zancada = politica.evaluarCarrera(carrera("5000", 1800, 1000, List.of(), null, null), ahora);
        EvaluacionDeCarrera zancadaBuena = politica.evaluarCarrera(carrera("5000", 1800, 5500, List.of(), null, null), ahora);
        // ~795 km de ruta para una carrera de 5 km: no cuadra.
        RutaDeCarrera rutaLejana = new RutaDeCarrera("_p~iF~ps|U_ulLnnqC_mqNvxq`@", 3);
        EvaluacionDeCarrera ruta = politica.evaluarCarrera(carrera("5000", 1800, null, List.of(), rutaLejana, null), ahora);

        assertThat(zancada.porRevisar()).isTrue();
        assertThat(zancada.motivoRevision()).contains("pasos");
        assertThat(zancadaBuena.porRevisar()).isFalse();
        assertThat(ruta.cuenta()).isTrue();
        assertThat(ruta.porRevisar()).isTrue();
        assertThat(ruta.motivoRevision()).contains("ruta");
    }

    // spec 042 FR-024, FR-025
    @Test
    @DisplayName("lo que no cuenta puede además quedar por revisar: se guarda con los dos motivos")
    void noCuentaYPorRevisar() {
        List<Tramo> rapidos = List.of(new Tramo(1, 100), new Tramo(2, 100));
        EvaluacionDeCarrera auto = politica.evaluarCarrera(carrera("2000", 200, null, rapidos, null, null), ahora);

        assertThat(auto.cuenta()).isFalse();
        assertThat(auto.porRevisar()).isTrue();
    }
}

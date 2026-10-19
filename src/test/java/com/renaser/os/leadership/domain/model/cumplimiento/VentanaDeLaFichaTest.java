package com.renaser.os.leadership.domain.model.cumplimiento;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * La ventana de la ficha es una semana del semáforo, de sábado a viernes (R-2). Sin fecha, la lista abre en la última
 * semana cerrada y la ficha en la semana en curso; nunca una semana que todavía no empezó. El «hoy» es el de la
 * persona (regla 02).
 */
class VentanaDeLaFichaTest {

    private static final ZoneId LIMA = ZoneId.of("America/Lima");
    private static final LocalDate LUNES_19 = LocalDate.of(2026, 10, 19);

    private static LocalDate hoyEn(String instante) {
        return Instant.parse(instante).atZone(LIMA).toLocalDate();
    }

    @Test
    @DisplayName("la lista sin fecha abre en la ultima semana cerrada, de sabado a viernes")
    void listaSinFecha() {
        VentanaDeLaFicha ventana = VentanaDeLaFicha.paraLaLista(null, LUNES_19);

        assertThat(ventana.desde()).isEqualTo(LocalDate.of(2026, 10, 10));
        assertThat(ventana.hasta()).isEqualTo(LocalDate.of(2026, 10, 16));
        assertThat(ventana.cerrada()).isTrue();
    }

    @Test
    @DisplayName("la ultima semana cerrada cambia el sabado y no antes: el viernes todavia es la de hace siete dias")
    void listaEnElBordeDeLaSemana() {
        // El viernes 16 la semana de hoy todavia corre: la ultima cerrada es la anterior.
        assertThat(VentanaDeLaFicha.paraLaLista(null, LocalDate.of(2026, 10, 16)).hasta())
                .isEqualTo(LocalDate.of(2026, 10, 9));
        // El sabado 17 acaba de cerrar la que termino ayer.
        assertThat(VentanaDeLaFicha.paraLaLista(null, LocalDate.of(2026, 10, 17)).hasta())
                .isEqualTo(LocalDate.of(2026, 10, 16));
    }

    @Test
    @DisplayName("la ficha sin fecha abre en la semana en curso: la que contiene a hoy, todavia sin cerrar")
    void fichaSinFecha() {
        VentanaDeLaFicha ventana = VentanaDeLaFicha.paraLaFicha(null, LUNES_19);

        assertThat(ventana.desde()).isEqualTo(LocalDate.of(2026, 10, 17));
        assertThat(ventana.hasta()).isEqualTo(LocalDate.of(2026, 10, 23));
        assertThat(ventana.cerrada()).isFalse();
        assertThat(ventana.hoyLocal()).isEqualTo(LUNES_19);
    }

    @Test
    @DisplayName("la semana en curso: un sabado empieza ese dia, un viernes termina ese dia")
    void fichaEnLosBordes() {
        assertThat(VentanaDeLaFicha.paraLaFicha(null, LocalDate.of(2026, 10, 17)).desde())
                .isEqualTo(LocalDate.of(2026, 10, 17));
        assertThat(VentanaDeLaFicha.paraLaFicha(null, LocalDate.of(2026, 10, 23)).hasta())
                .isEqualTo(LocalDate.of(2026, 10, 23));
        assertThat(VentanaDeLaFicha.paraLaFicha(null, LocalDate.of(2026, 10, 16)).desde())
                .isEqualTo(LocalDate.of(2026, 10, 10));
    }

    @Test
    @DisplayName("regla 02: el «hoy» de la persona manda; con el reloj en la madrugada UTC la semana en curso es la de Lima")
    void madrugadaUtc() {
        // 03:00 UTC del sabado 17 = 22:00 del viernes 16 en Lima: para ella todavia corre la semana del 10 al 16.
        LocalDate hoy = hoyEn("2026-10-17T03:00:00Z");

        VentanaDeLaFicha ventana = VentanaDeLaFicha.paraLaFicha(null, hoy);

        assertThat(hoy).isEqualTo(LocalDate.of(2026, 10, 16));
        assertThat(ventana.desde()).isEqualTo(LocalDate.of(2026, 10, 10));
        assertThat(ventana.hasta()).isEqualTo(LocalDate.of(2026, 10, 16));
        assertThat(ventana.esHoy(LocalDate.of(2026, 10, 16))).isTrue();
        assertThat(ventana.esFutura(LocalDate.of(2026, 10, 17))).isTrue();
    }

    @Test
    @DisplayName("con un viernes es esa semana; una pasada esta cerrada")
    void semanaPedida() {
        VentanaDeLaFicha ventana = VentanaDeLaFicha.paraLaFicha(LocalDate.of(2026, 10, 9), LUNES_19);

        assertThat(ventana.desde()).isEqualTo(LocalDate.of(2026, 10, 3));
        assertThat(ventana.hasta()).isEqualTo(LocalDate.of(2026, 10, 9));
        assertThat(ventana.cerrada()).isTrue();
    }

    @Test
    @DisplayName("la semana que todavia corre se puede pedir y no esta cerrada; la siguiente es futura")
    void semanaEnCursoYFutura() {
        VentanaDeLaFicha enCurso = VentanaDeLaFicha.paraLaFicha(LocalDate.of(2026, 10, 23), LUNES_19);
        assertThat(enCurso.desde()).isEqualTo(LocalDate.of(2026, 10, 17));
        assertThat(enCurso.cerrada()).isFalse();

        assertThatThrownBy(() -> VentanaDeLaFicha.paraLaFicha(LocalDate.of(2026, 10, 30), LUNES_19))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("futura");
        assertThatThrownBy(() -> VentanaDeLaFicha.paraLaLista(LocalDate.of(2026, 10, 30), LUNES_19))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("futura");
    }

    @Test
    @DisplayName("un dia que no es viernes se rechaza, como en las otras vistas del semaforo")
    void soloViernes() {
        assertThatThrownBy(() -> VentanaDeLaFicha.paraLaFicha(LocalDate.of(2026, 10, 10), LUNES_19))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> VentanaDeLaFicha.paraLaLista(LocalDate.of(2026, 10, 15), LUNES_19))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("hoy y los dias que vienen se distinguen de los que ya pasaron")
    void hoyYFuturos() {
        VentanaDeLaFicha ventana = VentanaDeLaFicha.paraLaFicha(null, LUNES_19);

        assertThat(ventana.esHoy(LUNES_19)).isTrue();
        assertThat(ventana.esFutura(LUNES_19)).isFalse();
        assertThat(ventana.esFutura(LocalDate.of(2026, 10, 20))).isTrue();
        assertThat(ventana.esFutura(LocalDate.of(2026, 10, 18))).isFalse();
        assertThat(ventana.esHoy(LocalDate.of(2026, 10, 18))).isFalse();
    }

    @Test
    @DisplayName("el sabado de la semana de un dia: lo que se usa para saber cual fue la primera semana")
    void sabadoQueAbreLaSemanaDe() {
        // El Dia 1 un miercoles: su primera semana abre el sabado anterior.
        assertThat(VentanaDeLaFicha.sabadoQueAbreLaSemanaDe(LocalDate.of(2026, 10, 7)))
                .isEqualTo(LocalDate.of(2026, 10, 3));
        assertThat(VentanaDeLaFicha.sabadoQueAbreLaSemanaDe(LocalDate.of(2026, 10, 3)))
                .isEqualTo(LocalDate.of(2026, 10, 3));
        assertThat(VentanaDeLaFicha.sabadoQueAbreLaSemanaDe(LocalDate.of(2026, 10, 9)))
                .isEqualTo(LocalDate.of(2026, 10, 3));
    }
}

package com.renaser.os.rocks.domain.model.rocadiaria;

import com.renaser.os.rocks.domain.model.rocamaestra.EjeObjetivo;
import com.renaser.os.shared.domain.FixedClock;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Las acciones del objetivo diario (V61).
 *
 * <p>Antes vivian colgando de la semana y eran <b>exactamente tres, obligatorias</b>. El dueno
 * describio la cadena de otra manera —<i>"objetivo de los 90 dias, luego mensual, luego semanal, y
 * luego objetivo diario, y estos objetivos diarios tienen acciones para hacerlo"</i>— y con el
 * cambio de lugar viene un cambio de regla: <b>cero es valido</b>. Obligar a inventar tres para
 * poder guardar es lo que llevaba a escribir relleno.
 */
class AccionDiariaTest {

    private static final FixedClock CLOCK = FixedClock.at(Instant.parse("2026-08-24T15:00:00Z"));

    private static RocaDiaria conAcciones(List<AccionDiaria> acciones) {
        return RocaDiaria.planificar(RocaDiariaId.of(UUID.randomUUID()), UserId.of(UUID.randomUUID()),
                LocalDate.of(2026, 8, 24), 1, "Pesarme en ayunas", null, 5, false, EjeObjetivo.CUERPO,
                null, null, null, acciones, CLOCK);
    }

    @Test
    @DisplayName("un objetivo del dia puede no tener desglose: cero acciones es valido")
    void sinAccionesEsValido() {
        assertThat(conAcciones(List.of()).acciones()).isEmpty();
        assertThat(conAcciones(null).acciones()).isEmpty();
    }

    @Test
    @DisplayName("hasta tres, y llegan ordenadas aunque se pasen al reves")
    void hastaTresYOrdenadas() {
        RocaDiaria roca = conAcciones(List.of(new AccionDiaria(3, "c"), new AccionDiaria(1, "a"),
                new AccionDiaria(2, "b")));

        assertThat(roca.acciones()).extracting(AccionDiaria::descripcion).containsExactly("a", "b", "c");
    }

    @Test
    @DisplayName("la cuarta no entra: si son mas de tres, ninguna es critica")
    void masDeTresSeRechaza() {
        assertThatThrownBy(() -> AccionDiaria.requireListaValida(List.of(new AccionDiaria(1, "a"),
                new AccionDiaria(2, "b"), new AccionDiaria(3, "c"), new AccionDiaria(3, "d"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("hasta 3");
    }

    @Test
    @DisplayName("un hueco en la numeracion no: 1 y 3 sin la 2 se lee como 'falta una'")
    void sinHuecos() {
        assertThatThrownBy(() -> AccionDiaria.requireListaValida(
                List.of(new AccionDiaria(1, "a"), new AccionDiaria(3, "c"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("sin huecos");
        assertThatCode(() -> AccionDiaria.requireListaValida(
                List.of(new AccionDiaria(1, "a"), new AccionDiaria(2, "b")))).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("una accion sin texto no es una accion")
    void descripcionObligatoria() {
        assertThatThrownBy(() -> new AccionDiaria(1, "   ")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new AccionDiaria(1, null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("el texto se guarda sin espacios de sobra en las puntas")
    void seLimpiaElTexto() {
        assertThat(new AccionDiaria(1, "  caminar 40 minutos  ").descripcion()).isEqualTo("caminar 40 minutos");
    }

    @Test
    @DisplayName("el orden vive entre 1 y 3")
    void ordenAcotado() {
        assertThatThrownBy(() -> new AccionDiaria(0, "a")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new AccionDiaria(4, "a")).isInstanceOf(IllegalArgumentException.class);
    }
}

package com.renaser.os.rocks.domain.model.rocadiaria;

import com.renaser.os.rocks.domain.model.cierre.CierreDeAccion;
import com.renaser.os.rocks.domain.model.cierre.MotivoDeAccionPendiente;
import com.renaser.os.rocks.domain.model.cierre.PlanDeManana;
import com.renaser.os.shared.domain.FixedClock;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

import static com.renaser.os.rocks.domain.model.cierre.RocasDeCierreDePrueba.cumplida;
import static com.renaser.os.rocks.domain.model.cierre.RocasDeCierreDePrueba.pendiente;
import static com.renaser.os.rocks.domain.model.cierre.RocasDeCierreDePrueba.respuesta;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Lo que el agregado {@link RocaDiaria} hace con la respuesta de cierre (spec 017). */
class RocaDiariaCierreTest {

    private static final LocalDate HOY = LocalDate.of(2026, 10, 28);
    private static final Instant AHORA = Instant.parse("2026-10-29T03:00:00Z");

    @Test
    @DisplayName("una roca recien planificada o releida de antes no trae respuesta de cierre")
    void nacesinCierre() {
        assertThat(pendiente(HOY, 1, "Algo", LocalTime.NOON).cierre()).isNull();
    }

    @Test
    @DisplayName("cerrar una pendiente guarda la respuesta y deja la roca pendiente, sin puntos")
    void cerrarGuardaYNoCumple() {
        RocaDiaria roca = pendiente(HOY, 1, "Algo", null);

        roca.cerrarConRespuesta(respuesta(MotivoDeAccionPendiente.PARA_DESPUES, PlanDeManana.DEJARLA, "mañana sin falta"), AHORA);

        assertThat(roca.cierre().respuesta().nota()).isEqualTo("mañana sin falta");
        assertThat(roca.cierre().cerradoEn()).isEqualTo(AHORA);
        assertThat(roca.completada()).isFalse();
        assertThat(roca.completadaEn()).isNull();
        assertThat(roca.puedeOtorgarPuntos()).isTrue();
        assertThat(roca.puntosOtorgados()).isZero();
    }

    @Test
    @DisplayName("una accion ya cumplida no lleva respuesta de cierre")
    void laCumplidaNoLleva() {
        RocaDiaria roca = cumplida(HOY, 1, "Algo", null, 8);
        assertThatThrownBy(() -> roca.cerrarConRespuesta(
                respuesta(MotivoDeAccionPendiente.SIN_TIEMPO, PlanDeManana.DEJARLA, null), AHORA))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("cumplir la accion despues de cerrar no borra ni inventa nada: la respuesta queda y la lectura la oculta")
    void cumplirDespuesConservaLaRespuestaDelAgregado() {
        RocaDiaria roca = pendiente(HOY, 1, "Algo", null);
        roca.cerrarConRespuesta(respuesta(MotivoDeAccionPendiente.SIN_TIEMPO, PlanDeManana.OTRA_HORA, null), AHORA);

        roca.completar(AHORA.plusSeconds(60), FixedClock.at(AHORA.plusSeconds(60)));

        assertThat(roca.completada()).isTrue();
        assertThat(roca.cierre()).isEqualTo(new CierreDeAccion(
                respuesta(MotivoDeAccionPendiente.SIN_TIEMPO, PlanDeManana.OTRA_HORA, null), AHORA));
    }

    @Test
    @DisplayName("quitar el cierre lo deja en null")
    void quitar() {
        RocaDiaria roca = pendiente(HOY, 1, "Algo", null);
        roca.cerrarConRespuesta(respuesta(MotivoDeAccionPendiente.SIN_TIEMPO, PlanDeManana.OTRA_HORA, null), AHORA);
        roca.quitarCierre();
        assertThat(roca.cierre()).isNull();
    }
}

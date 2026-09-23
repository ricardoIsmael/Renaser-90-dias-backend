package com.renaser.os.rag.domain.model.propuesta;

import com.renaser.os.rag.domain.model.herramienta.InvocacionHerramienta;
import com.renaser.os.rag.domain.model.herramienta.ResultadoHerramienta;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PropuestaAccionTest {

    /** 02:00 UTC: en Lima todavia es el dia anterior. La propuesta no depende del dia local (regla 02). */
    private static final Instant AHORA = Instant.parse("2026-09-23T02:00:00Z");
    private static final Duration VIGENCIA = Duration.ofMinutes(10);
    private static final PropuestaAccionId ID = PropuestaAccionId.of(
            UUID.fromString("11111111-1111-1111-1111-111111111111"));
    private static final UserId DUENO = UserId.of(UUID.fromString("22222222-2222-2222-2222-222222222222"));
    private static final InvocacionHerramienta INVOCACION = new InvocacionHerramienta("marcar_habito_completado",
            Map.of("registro_id", "33333333-3333-3333-3333-333333333333"));

    private static PropuestaAccion pendiente() {
        return PropuestaAccion.crear(ID, DUENO, INVOCACION, "Marcar Meditar como hecho", AHORA, VIGENCIA);
    }

    @Test
    @DisplayName("nace PENDIENTE, vence despues de la vigencia y con los argumentos integros")
    void naceCorrecta() {
        PropuestaAccion propuesta = pendiente();

        assertThat(propuesta.estado()).isEqualTo(EstadoPropuesta.PENDIENTE);
        assertThat(propuesta.venceEn()).isEqualTo(AHORA.plus(VIGENCIA));
        assertThat(propuesta.resueltaEn()).isNull();
        assertThat(propuesta.argumentosIntegros()).isTrue();
        assertThat(propuesta.perteneceA(DUENO)).isTrue();
        assertThat(propuesta.perteneceA(UserId.of(UUID.randomUUID()))).isFalse();
    }

    @Test
    @DisplayName("no se crea sin resumen, sin herramienta o con vigencia no positiva")
    void invariantesDeCreacion() {
        assertThatThrownBy(() -> PropuestaAccion.crear(ID, DUENO, INVOCACION, "  ", AHORA, VIGENCIA))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PropuestaAccion.crear(ID, DUENO, InvocacionHerramienta.sinArgumentos(" "),
                "Resumen", AHORA, VIGENCIA)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PropuestaAccion.crear(ID, DUENO, INVOCACION, "Resumen", AHORA, Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("el vencimiento se deriva del reloj: vigente un instante antes, vencida desde venceEn")
    void vencimientoDerivado() {
        PropuestaAccion propuesta = pendiente();

        assertThat(propuesta.estaVencidaEn(AHORA.plus(VIGENCIA).minusMillis(1))).isFalse();
        assertThat(propuesta.estaVencidaEn(AHORA.plus(VIGENCIA))).isTrue();
    }

    @Test
    @DisplayName("confirmar una vigente la pasa a CONFIRMADA en ejecucion, sin resultado todavia")
    void confirmarVigente() {
        PropuestaAccion propuesta = pendiente();

        propuesta.confirmar(AHORA.plusSeconds(30));

        assertThat(propuesta.estado()).isEqualTo(EstadoPropuesta.CONFIRMADA);
        assertThat(propuesta.resueltaEn()).isEqualTo(AHORA.plusSeconds(30));
        assertThat(propuesta.enEjecucion()).isTrue();
        assertThat(propuesta.resultadoRegistrado()).isEmpty();
    }

    @Test
    @DisplayName("una vencida no se confirma, con un mensaje legible")
    void vencidaNoSeConfirma() {
        PropuestaAccion propuesta = pendiente();

        assertThatThrownBy(() -> propuesta.confirmar(AHORA.plus(VIGENCIA)))
                .isInstanceOf(PropuestaNoDisponibleException.class)
                .hasMessageContaining("vencio");
        assertThat(propuesta.estado()).isEqualTo(EstadoPropuesta.PENDIENTE);
    }

    @Test
    @DisplayName("una cancelada no se confirma, y una ya confirmada tampoco se vuelve a confirmar")
    void soloSeConfirmaUnaPendiente() {
        PropuestaAccion cancelada = pendiente();
        cancelada.cancelar(AHORA);
        PropuestaAccion confirmada = pendiente();
        confirmada.confirmar(AHORA);

        assertThatThrownBy(() -> cancelada.confirmar(AHORA)).isInstanceOf(PropuestaNoDisponibleException.class)
                .hasMessageContaining("cancelada");
        assertThatThrownBy(() -> confirmada.confirmar(AHORA)).isInstanceOf(PropuestaNoDisponibleException.class);
    }

    @Test
    @DisplayName("registrar el resultado deja la confirmacion resuelta como Exito")
    void registrarResultado() {
        PropuestaAccion propuesta = pendiente();
        propuesta.confirmar(AHORA);

        propuesta.registrarResultado("Listo, marque Meditar.");

        assertThat(propuesta.enEjecucion()).isFalse();
        assertThat(propuesta.resultadoRegistrado()).contains(ResultadoHerramienta.exito("Listo, marque Meditar."));
        assertThatThrownBy(() -> propuesta.registrarResultado("otra vez")).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("marcar fallida desde la ejecucion guarda el motivo y conserva cuando se confirmo")
    void marcarFallidaDesdeEjecucion() {
        PropuestaAccion propuesta = pendiente();
        propuesta.confirmar(AHORA);

        propuesta.marcarFallida("Ese habito ya vencio.", AHORA.plusSeconds(5));

        assertThat(propuesta.estado()).isEqualTo(EstadoPropuesta.FALLIDA);
        assertThat(propuesta.resueltaEn()).isEqualTo(AHORA);
        assertThat(propuesta.resultadoRegistrado()).contains(ResultadoHerramienta.fallo("Ese habito ya vencio."));
    }

    @Test
    @DisplayName("una pendiente tambien puede fallar sin ejecutarse, pero una cancelada no")
    void marcarFallidaSoloDesdePendienteOEjecucion() {
        PropuestaAccion pendiente = pendiente();
        pendiente.marcarFallida("No disponible.", AHORA);
        PropuestaAccion cancelada = pendiente();
        cancelada.cancelar(AHORA);

        assertThat(pendiente.estado()).isEqualTo(EstadoPropuesta.FALLIDA);
        assertThat(pendiente.resueltaEn()).isEqualTo(AHORA);
        assertThatThrownBy(() -> cancelada.marcarFallida("x", AHORA)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("cancelar es idempotente y se permite aunque haya vencido")
    void cancelarIdempotente() {
        PropuestaAccion propuesta = pendiente();

        propuesta.cancelar(AHORA.plus(VIGENCIA).plusSeconds(1));
        propuesta.cancelar(AHORA.plus(VIGENCIA).plusSeconds(99));

        assertThat(propuesta.estado()).isEqualTo(EstadoPropuesta.CANCELADA);
        assertThat(propuesta.resueltaEn()).isEqualTo(AHORA.plus(VIGENCIA).plusSeconds(1));
        assertThat(propuesta.estaVencidaEn(AHORA.plus(VIGENCIA))).isFalse();
    }

    @Test
    @DisplayName("no se cancela lo que ya se ejecuto")
    void noSeCancelaLoEjecutado() {
        PropuestaAccion propuesta = pendiente();
        propuesta.confirmar(AHORA);

        assertThatThrownBy(() -> propuesta.cancelar(AHORA)).isInstanceOf(PropuestaNoDisponibleException.class)
                .hasMessageContaining("ya se resolvio");
    }

    @Test
    @DisplayName("si los argumentos guardados no coinciden con la huella, la propuesta no es integra")
    void argumentosAlterados() {
        PropuestaAccion original = pendiente();
        PropuestaAccion alterada = PropuestaAccion.rehidratar(ID, DUENO,
                new InvocacionHerramienta("marcar_habito_completado", Map.of("registro_id", "otro")),
                original.huella(), original.resumen(), EstadoPropuesta.PENDIENTE, original.creadaEn(),
                original.venceEn(), null, null, 0L);

        assertThat(alterada.argumentosIntegros()).isFalse();
    }
}

package com.renaser.os.rag.application.services.herramientas;

import com.renaser.os.rag.application.ports.in.propuesta.ConsultarPropuestasDelTurnoUseCase.PedidoDeEvidencia;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Las tarjetas de camara del turno (D-171): se leen por instante, una por registro, y no se acumulan. */
class PedidosDeEvidenciaDelTurnoTest {

    private static final UserId ANA = UserId.of(UUID.randomUUID());
    private static final UserId LUIS = UserId.of(UUID.randomUUID());
    private static final Instant INICIO = Instant.parse("2026-09-26T03:00:00Z");
    private static final Instant FIN_DEL_DIA = Instant.parse("2026-09-26T05:00:00Z");

    private final RelojMovible reloj = new RelojMovible();
    private final PedidosDeEvidenciaDelTurno pedidos = new PedidosDeEvidenciaDelTurno(reloj);

    private static PedidoDeEvidencia pedido(UUID registro, String titulo, Instant en) {
        return new PedidoDeEvidencia(registro, titulo, en, FIN_DEL_DIA, false);
    }

    @Test
    @DisplayName("solo los del actor pedidos desde el inicio del turno, y uno por habito")
    void soloLosDelTurno() {
        UUID jugo = UUID.randomUUID();
        UUID ritual = UUID.randomUUID();
        pedidos.pedir(ANA, pedido(ritual, "RITUAL", INICIO.minusSeconds(60)));
        pedidos.pedir(ANA, pedido(jugo, "JUGO VERDE", INICIO));
        pedidos.pedir(ANA, pedido(jugo, "JUGO VERDE", INICIO.plusSeconds(5)));
        pedidos.pedir(LUIS, pedido(UUID.randomUUID(), "AGUA TIBIA", INICIO));

        assertThat(pedidos.pedidasDesde(ANA, INICIO)).containsExactly(pedido(jugo, "JUGO VERDE", INICIO));
    }

    @Test
    @DisplayName("lo viejo se borra al pedir de nuevo: la memoria no crece con el uso")
    void descartaLoViejo() {
        pedidos.pedir(ANA, pedido(UUID.randomUUID(), "RITUAL", INICIO));
        reloj.ahora = INICIO.plus(PedidosDeEvidenciaDelTurno.RETENCION).plus(Duration.ofSeconds(1));
        UUID jugo = UUID.randomUUID();
        pedidos.pedir(ANA, pedido(jugo, "JUGO VERDE", reloj.ahora));

        assertThat(pedidos.pedidasDesde(ANA, Instant.EPOCH)).extracting(PedidoDeEvidencia::registroId)
                .containsExactly(jugo);
    }

    private static final class RelojMovible implements Clock {
        private Instant ahora = INICIO;

        @Override
        public Instant now() {
            return ahora;
        }

        @Override
        public LocalDate today() {
            return LocalDate.ofInstant(ahora, java.time.ZoneOffset.UTC);
        }
    }
}

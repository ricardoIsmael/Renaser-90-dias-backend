package com.renaser.os.support.domain.model.ticketsoporte;

import com.renaser.os.shared.domain.FixedClock;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TicketSoporteTest {

    private static final FixedClock CLOCK = FixedClock.at(Instant.parse("2026-08-24T10:00:00Z"));

    private static UserId nuevoUsuario() {
        return UserId.of(UUID.randomUUID());
    }

    private static TicketSoporte ticketAbierto() {
        return TicketSoporte.abrir(nuevoUsuario(), CategoriaSoporte.TECNICO, "La app se cierra",
                "Se cierra sola al abrir el modulo de habitos", null, null, CLOCK);
    }

    @Test
    @DisplayName("abrir() nace ABIERTO, sin notas de admin y sin resolver")
    void abrirNaceAbierto() {
        TicketSoporte ticket = ticketAbierto();

        assertThat(ticket.estado()).isEqualTo(EstadoTicketSoporte.ABIERTO);
        assertThat(ticket.notasAdmin()).isNull();
        assertThat(ticket.resueltoEn()).isNull();
        assertThat(ticket.creadoEn()).isEqualTo(CLOCK.now());
    }

    @Test
    @DisplayName("el mensaje exige minimo 10 caracteres (docs/FEATURE_SUPPORT.md: 'min 10 chars')")
    void mensajeExigeMinimoDiezCaracteres() {
        UserId usuario = nuevoUsuario();

        assertThatThrownBy(() -> TicketSoporte.abrir(usuario, CategoriaSoporte.OTRO, "Asunto", "corto", null, null,
                CLOCK)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("el asunto es obligatorio")
    void asuntoEsObligatorio() {
        UserId usuario = nuevoUsuario();

        assertThatThrownBy(() -> TicketSoporte.abrir(usuario, CategoriaSoporte.OTRO, "   ",
                "Un mensaje con longitud suficiente", null, null, CLOCK))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("la categoria es obligatoria en el dominio (el default a OTRO es responsabilidad del caso de uso)")
    void categoriaEsObligatoriaEnElDominio() {
        UserId usuario = nuevoUsuario();

        assertThatThrownBy(() -> TicketSoporte.abrir(usuario, null, "Asunto",
                "Un mensaje con longitud suficiente", null, null, CLOCK)).isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("resolver() transiciona a RESUELTO y registra las notas")
    void resolverTransicionaAResuelto() {
        TicketSoporte ticket = ticketAbierto();

        ticket.resolver("Reinstalar la app soluciona el problema", CLOCK);

        assertThat(ticket.estado()).isEqualTo(EstadoTicketSoporte.RESUELTO);
        assertThat(ticket.notasAdmin()).isEqualTo("Reinstalar la app soluciona el problema");
        assertThat(ticket.resueltoEn()).isEqualTo(CLOCK.now());
    }

    @Test
    @DisplayName("resolver() dos veces es idempotente: la segunda llamada no cambia nada (docs/FEATURE_SUPPORT.md)")
    void resolverEsIdempotente() {
        TicketSoporte ticket = ticketAbierto();
        ticket.resolver("Primera nota", CLOCK);
        Instant primeraResolucion = ticket.resueltoEn();

        ticket.resolver("Segunda nota, no deberia aplicarse", CLOCK);

        assertThat(ticket.notasAdmin()).isEqualTo("Primera nota");
        assertThat(ticket.resueltoEn()).isEqualTo(primeraResolucion);
    }

    @Test
    @DisplayName("AdjuntoSoporte exige bucket y ruta no vacios (D-34: nunca una URL persistida)")
    void adjuntoExigeBucketYRuta() {
        assertThatThrownBy(() -> new AdjuntoSoporte(null, "soporte/x/1.png"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new AdjuntoSoporte("renaser-files", " "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("rehydrate() reconstruye un ticket ya existente tal cual (para el adaptador de persistencia)")
    void rehydrateReconstruyeSinValidar() {
        TicketSoporteId id = TicketSoporteId.newId();
        UserId usuario = nuevoUsuario();
        AdjuntoSoporte adjunto = new AdjuntoSoporte("renaser-files", "soporte/x/1.png");

        TicketSoporte ticket = TicketSoporte.rehydrate(id, usuario, CategoriaSoporte.FACTURACION, "asunto",
                "mensaje largo de verdad", "log", adjunto, EstadoTicketSoporte.RESUELTO, "notas", CLOCK.now(),
                CLOCK.now(), CLOCK.now());

        assertThat(ticket.id()).isEqualTo(id);
        assertThat(ticket.adjunto()).isEqualTo(adjunto);
        assertThat(ticket.estado()).isEqualTo(EstadoTicketSoporte.RESUELTO);
    }
}

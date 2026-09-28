package com.renaser.os.calendar.domain.model.evento;

import com.renaser.os.shared.domain.FixedClock;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * SEG-13 (E-364): el link de un evento se abre al tocar «Unirme», asi que solo puede ser una direccion web.
 * Contra el codigo anterior fallan todos los casos de rechazo: el servidor guardo {@code javascript:alert(1)}
 * con un 201.
 */
class LinkDelEventoTest {

    private static final FixedClock CLOCK = FixedClock.at(Instant.parse("2026-09-27T03:00:00Z"));
    private static final Instant INICIA_EN = Instant.parse("2026-10-01T00:30:00Z");
    private static final ZoneId LIMA = ZoneId.of("America/Lima");
    private static final UserId CREADOR = UserId.of(UUID.randomUUID());

    @ParameterizedTest
    @ValueSource(strings = {
            "javascript:alert(1)", "JavaScript:alert(document.cookie)", "data:text/html,<script>alert(1)</script>",
            "intent://meet#Intent;scheme=https;end", "file:///etc/passwd", "ftp://example.com/sala",
            "meet.google.com/abc-defg-hij", "https://", "https:///sin-servidor", "https://a b.com"})
    @DisplayName("E-364: un link que no es una direccion web http/https se rechaza con un mensaje claro")
    void rechazaLoQueNoEsUnaDireccionWeb(String link) {
        assertThatThrownBy(() -> evento(TipoUbicacion.ENLACE, link))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("El link tiene que empezar con https:// o http://");
    }

    @ParameterizedTest
    @EnumSource(value = TipoUbicacion.class, names = {"ZOOM", "MEET", "ENLACE"})
    @DisplayName("E-364: la regla vale para los tres tipos de ubicacion que llevan link")
    void laReglaValeParaLosTresTiposConLink(TipoUbicacion tipo) {
        assertThatThrownBy(() -> evento(tipo, "javascript:alert(1)")).isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "https://meet.google.com/abc-defg-hij", "https://us02web.zoom.us/j/123?pwd=x",
            "https://drive.google.com/file/d/1/view", "http://example.com/sala-e2e", "HTTPS://MEET.GOOGLE.COM/X",
            "  https://meet.google.com/con-espacios  "})
    @DisplayName("E-364: https y http con servidor pasan (http no se cambia: es regla de negocio aparte)")
    void aceptaHttpsYHttp(String link) {
        assertThat(evento(TipoUbicacion.ENLACE, link).valorUbicacion()).isEqualTo(link.trim());
    }

    @Test
    @DisplayName("E-364: una direccion fisica sigue siendo texto libre")
    void laDireccionNoEsUnLink() {
        assertThat(evento(TipoUbicacion.DIRECCION, "Av. Larco 123, Miraflores").valorUbicacion())
                .isEqualTo("Av. Larco 123, Miraflores");
    }

    @Test
    @DisplayName("E-364: editar un evento tambien valida el link")
    void editarTambienValida() {
        Evento evento = evento(TipoUbicacion.MEET, "https://meet.google.com/abc");

        assertThatThrownBy(() -> evento.actualizar("Clase", null, INICIA_EN, 30, LIMA, TipoUbicacion.MEET,
                "javascript:alert(1)", TipoAudiencia.TODOS, null, null, null, false, false, false, null, Set.of(),
                List.of(), CLOCK)).isInstanceOf(IllegalArgumentException.class);
    }

    private static Evento evento(TipoUbicacion tipo, String link) {
        return Evento.crear(EventoId.of(UUID.randomUUID()), "Evento link", null, INICIA_EN, 30, LIMA, tipo, link,
                TipoAudiencia.TODOS, null, null, null, TipoEvento.SESION_ESPECIAL, false, false, false, null,
                Set.of(), List.of(), CREADOR, CLOCK);
    }
}

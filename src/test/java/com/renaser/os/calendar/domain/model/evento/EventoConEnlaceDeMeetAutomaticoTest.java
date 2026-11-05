package com.renaser.os.calendar.domain.model.evento;

import com.renaser.os.calendar.domain.model.meet.AccesoSalaMeet;
import com.renaser.os.shared.domain.FixedClock;
import com.renaser.os.shared.domain.Formacion;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** El agregado {@link Evento} con el enlace de Meet que crea el servidor (spec 040, FR-001, FR-003, FR-004). */
class EventoConEnlaceDeMeetAutomaticoTest {

    private static final FixedClock RELOJ = FixedClock.at(Instant.parse("2026-11-05T15:00:00Z"));
    private static final Instant INICIA = Instant.parse("2026-11-11T00:00:00Z");

    private static Evento crear(TipoUbicacion tipo, String valor, AccesoSalaMeet acceso) {
        return Evento.crear(EventoId.of(UUID.randomUUID()), "Clase", null, INICIA, 60, ZoneId.of("America/Lima"), tipo, valor, TipoAudiencia.TODOS,
                null, null, null, TipoEvento.SESION_ESPECIAL, false, false, false, null, Set.of(), List.of(), UserId.of(UUID.randomUUID()), RELOJ,
                Formacion.ESCUELA, acceso);
    }

    // spec 040 FR-001
    @Test
    @DisplayName("FR-001: un evento que pide el enlace automático es de Meet y todavía no tiene link: lo pone el servidor cuando Google crea la sala")
    void nacerSinLink() {
        Evento evento = crear(TipoUbicacion.MEET, null, AccesoSalaMeet.ABIERTA);

        assertThat(evento.usaSalaDeMeetAutomatica()).isTrue();
        assertThat(evento.accesoSalaMeet()).isEqualTo(AccesoSalaMeet.ABIERTA);
        assertThat(evento.valorUbicacion()).isNull();
    }

    // spec 040 FR-004
    @Test
    @DisplayName("FR-004: con el enlace automático el evento solo puede ser de Meet")
    void soloMeet() {
        for (TipoUbicacion otro : List.of(TipoUbicacion.ZOOM, TipoUbicacion.ENLACE, TipoUbicacion.DIRECCION, TipoUbicacion.WEBINAR,
                TipoUbicacion.LLAMADA_INTERNA)) {
            assertThatThrownBy(() -> crear(otro, null, AccesoSalaMeet.ABIERTA))
                    .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("tiene que ser de Meet");
        }
    }

    // spec 040 FR-003
    @Test
    @DisplayName("FR-003: el enlace de la sala se guarda con la misma regla que un link pegado a mano: tiene que ser web")
    void elEnlaceSeValidaComoCualquierLink() {
        Evento evento = crear(TipoUbicacion.MEET, null, AccesoSalaMeet.CON_PERMISO);

        evento.fijarEnlaceDeMeet("https://meet.google.com/abc-defg-hij");
        assertThat(evento.valorUbicacion()).isEqualTo("https://meet.google.com/abc-defg-hij");

        assertThatThrownBy(() -> evento.fijarEnlaceDeMeet("javascript:alert(1)")).isInstanceOf(IllegalArgumentException.class)
                .hasMessage(Evento.LINK_NO_WEB);

        evento.fijarEnlaceDeMeet(null);
        assertThat(evento.valorUbicacion()).isNull();
    }

    // spec 040 FR-004
    @Test
    @DisplayName("FR-004: un evento con el link pegado a mano no recibe el enlace del servidor")
    void unEventoConLinkPegadoNoRecibeElEnlaceDelServidor() {
        Evento evento = crear(TipoUbicacion.MEET, "https://meet.google.com/abc-defg-hij", null);

        assertThat(evento.usaSalaDeMeetAutomatica()).isFalse();
        assertThat(evento.accesoSalaMeet()).isNull();
        assertThatThrownBy(() -> evento.fijarEnlaceDeMeet("https://meet.google.com/zzz-zzzz-zzz")).isInstanceOf(IllegalStateException.class);
    }

    // spec 040 FR-004
    @Test
    @DisplayName("FR-004: sin el enlace automático un evento de Meet sigue exigiendo su link, como siempre")
    void sinEnlaceAutomaticoElLinkSigueSiendoObligatorio() {
        assertThatThrownBy(() -> crear(TipoUbicacion.MEET, null, null)).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("La URL es obligatoria para este tipo de ubicacion");
        assertThatThrownBy(() -> crear(TipoUbicacion.MEET, "javascript:alert(1)", null)).isInstanceOf(IllegalArgumentException.class);
    }

    // spec 040 FR-005, FR-023
    @Test
    @DisplayName("FR-023 (corregido 2026-11-05): la sala es siempre ABIERTA aunque llegue CON_PERMISO; editar puede dejar de pedir el enlace automático")
    void editarElAcceso() {
        Evento evento = crear(TipoUbicacion.MEET, null, AccesoSalaMeet.CON_PERMISO);
        assertThat(evento.accesoSalaMeet()).isEqualTo(AccesoSalaMeet.ABIERTA);

        evento.actualizar("Clase", null, INICIA, 60, ZoneId.of("America/Lima"), TipoUbicacion.MEET, null, TipoAudiencia.TODOS, null, null, null,
                false, false, false, null, Set.of(), List.of(), RELOJ, AccesoSalaMeet.CON_PERMISO);
        assertThat(evento.accesoSalaMeet()).isEqualTo(AccesoSalaMeet.ABIERTA);

        evento.actualizar("Clase", null, INICIA, 60, ZoneId.of("America/Lima"), TipoUbicacion.ZOOM, "https://zoom.us/j/1", TipoAudiencia.TODOS,
                null, null, null, false, false, false, null, Set.of(), List.of(), RELOJ, null);
        assertThat(evento.usaSalaDeMeetAutomatica()).isFalse();
        assertThat(evento.valorUbicacion()).isEqualTo("https://zoom.us/j/1");
    }

    // spec 040 SC-007
    @Test
    @DisplayName("SC-007: los eventos de siempre (sin el enlace automático) no cambian: la firma anterior de crear y de actualizar sigue igual")
    void losEventosDeSiempreNoCambian() {
        Evento evento = Evento.crear(EventoId.of(UUID.randomUUID()), "Clase", null, INICIA, 60, ZoneId.of("America/Lima"), TipoUbicacion.MEET,
                "https://meet.google.com/abc-defg-hij", TipoAudiencia.TODOS, null, null, null, TipoEvento.SESION_ESPECIAL, false, false, false,
                null, Set.of(), List.of(), UserId.of(UUID.randomUUID()), RELOJ);

        assertThat(evento.usaSalaDeMeetAutomatica()).isFalse();
        assertThat(evento.valorUbicacion()).isEqualTo("https://meet.google.com/abc-defg-hij");
    }
}

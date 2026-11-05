package com.renaser.os.calendar.application.services;

import com.renaser.os.calendar.application.ports.in.evento.ActualizarEventoUseCase.ActualizarEventoCommand;
import com.renaser.os.calendar.application.ports.in.evento.CrearEventoUseCase.CrearEventoCommand;
import com.renaser.os.calendar.application.ports.in.meet.EventoConMeetVista;
import com.renaser.os.calendar.application.ports.out.meet.CuentaGoogleNoConectadaException;
import com.renaser.os.calendar.application.ports.out.meet.GoogleNoRespondeException;
import com.renaser.os.calendar.application.ports.out.meet.PedidoDeSala;
import com.renaser.os.calendar.domain.model.evento.Evento;
import com.renaser.os.calendar.domain.model.evento.TipoAudiencia;
import com.renaser.os.calendar.domain.model.evento.TipoEvento;
import com.renaser.os.calendar.domain.model.evento.TipoUbicacion;
import com.renaser.os.calendar.domain.model.meet.AccesoSalaMeet;
import com.renaser.os.calendar.domain.model.meet.EstadoSalaMeet;
import com.renaser.os.calendar.domain.model.meet.SalaMeet;
import com.renaser.os.shared.domain.FuncionalidadNoDisponibleException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static com.renaser.os.calendar.application.services.MundoDeMeet.ADMIN;
import static com.renaser.os.calendar.application.services.MundoDeMeet.LIMA;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Lo que pasa al guardar un evento que pide el enlace de Meet automático (spec 040): primero se guarda, después se le pide la sala a Google, y
 * nada de lo que haga Google puede perder el evento. Con el Google simulado y la base en memoria: sin red, sin credenciales.
 */
class EnlaceDeMeetAlGuardarUnEventoTest {

    private static final Instant HOY_19H_LIMA = Instant.parse("2026-11-06T00:00:00Z");

    private MundoDeMeet m;

    @BeforeEach
    void setUp() {
        m = new MundoDeMeet();
        m.conectarLaCuenta();
    }

    // spec 040 FR-001, FR-002, FR-003, SC-002
    @Test
    @DisplayName("crear con «Crear enlace de Meet»: el evento se guarda, Google crea la sala y el link queda en el evento como cualquier link de Meet")
    void crearConEnlaceAutomaticoDejaElLinkEnElEvento() {
        List<Boolean> elEventoYaEstabaGuardadoAlLlamarAGoogle = new ArrayList<>();
        m.google.alLlamar = () -> elEventoYaEstabaGuardadoAlLlamarAGoogle.add(!m.eventos.porId.isEmpty());

        EventoConMeetVista creado = m.servicio.crear(comandoCrear(AccesoSalaMeet.ABIERTA, null, TipoUbicacion.MEET));

        Evento evento = creado.evento().evento();
        assertThat(evento.tipoUbicacion()).isEqualTo(TipoUbicacion.MEET);
        assertThat(evento.valorUbicacion()).startsWith("https://meet.google.com/");
        assertThat(creado.meet().estado()).isEqualTo(EstadoSalaMeet.CREADO);
        assertThat(elEventoYaEstabaGuardadoAlLlamarAGoogle).isNotEmpty().containsOnly(true);
        // El id del evento de Google sale del de Renaser: el uuid sin guiones.
        assertThat(m.google.eventos).containsOnlyKeys(evento.id().value().toString().replace("-", ""));
    }

    // spec 040 FR-001
    @Test
    @DisplayName("FR-001: ni el servicio ni quien habla con Google abren una transacción: nunca se espera a Google con una conexión de la base tomada")
    void ningunaTransaccionRodeaALaLlamadaAGoogle() {
        assertThat(EventoConSalaDeMeetService.class.isAnnotationPresent(Transactional.class)).isFalse();
        assertThat(SincronizadorDeSalas.class.isAnnotationPresent(Transactional.class)).isFalse();
        assertThat(PasosConGoogle.class.isAnnotationPresent(Transactional.class)).isFalse();
        for (Class<?> clase : List.of(EventoConSalaDeMeetService.class, SincronizadorDeSalas.class, PasosConGoogle.class)) {
            assertThat(List.of(clase.getDeclaredMethods())).noneMatch(metodo -> metodo.isAnnotationPresent(Transactional.class));
        }
    }

    // spec 040 FR-022
    @Test
    @DisplayName("FR-022: a Google solo le llegan el id, el título, el inicio, el fin, la zona y el correo de quien organiza (decisión 6); nada de audiencia ni descripción")
    void aGoogleSoloLlegaLoQueElCalendarioNecesita() {
        assertThat(List.of(PedidoDeSala.class.getRecordComponents()))
                .extracting(c -> c.getName()).containsExactlyInAnyOrder("idGoogle", "titulo", "inicio", "fin", "zona", "invitados");

        m.servicio.crear(comandoCrear(AccesoSalaMeet.ABIERTA, null, TipoUbicacion.MEET));

        PedidoDeSala pedido = m.google.ultimoPedido.values().iterator().next();
        assertThat(pedido.titulo()).isEqualTo("Clase en vivo");
        assertThat(pedido.inicio()).isEqualTo(HOY_19H_LIMA);
        assertThat(pedido.fin()).isEqualTo(HOY_19H_LIMA.plus(Duration.ofMinutes(90)));
        assertThat(pedido.zona()).isEqualTo(LIMA);
    }

    // spec 040 FR-004
    @Test
    @DisplayName("FR-004: pedir el enlace automático y pegar un link a la vez es un 400, y no se crea nada")
    void noSePuedePedirElEnlaceYPegarUnoALaVez() {
        assertThatThrownBy(() -> m.servicio.crear(comandoCrear(AccesoSalaMeet.ABIERTA, "https://meet.google.com/abc-defg-hij", TipoUbicacion.MEET)))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("Elige: crear el enlace de Meet o pegar uno.");

        assertThat(m.eventos.porId).isEmpty();
        assertThat(m.google.llamadas).isEmpty();
    }

    // spec 040 FR-004
    @Test
    @DisplayName("FR-004: el enlace automático solo va con ubicación de Meet; con cualquier otro tipo es un 400")
    void elEnlaceAutomaticoSoloVaConMeet() {
        assertThatThrownBy(() -> m.servicio.crear(comandoCrear(AccesoSalaMeet.ABIERTA, null, TipoUbicacion.ZOOM)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("tiene que ser Meet");

        assertThat(m.eventos.porId).isEmpty();
    }

    // spec 040 FR-014
    @Test
    @DisplayName("FR-014: sin cuenta de Google conectada, pedir el enlace automático es un 409 con su frase, y el evento no se crea")
    void sinCuentaConectadaEsUn409() {
        m.cuenta.borrar();

        assertThatThrownBy(() -> m.servicio.crear(comandoCrear(AccesoSalaMeet.ABIERTA, null, TipoUbicacion.MEET)))
                .isInstanceOf(CuentaGoogleNoConectadaException.class)
                .isInstanceOf(IllegalStateException.class) // GlobalExceptionHandler: 409
                .hasMessage("Todavía no hay una cuenta de Google conectada.");

        assertThat(m.eventos.porId).isEmpty();
    }

    // spec 040 FR-014, SC-009
    @Test
    @DisplayName("SC-009: con el interruptor eventos.meet apagado, pedir el enlace automático es un 404 FUNCIONALIDAD_NO_DISPONIBLE")
    void conElInterruptorApagadoEsUn404() {
        m.interruptores.prendida = false;

        assertThatThrownBy(() -> m.servicio.crear(comandoCrear(AccesoSalaMeet.ABIERTA, null, TipoUbicacion.MEET)))
                .isInstanceOf(FuncionalidadNoDisponibleException.class);

        assertThat(m.eventos.porId).isEmpty();
        assertThat(m.google.llamadas).isEmpty();
    }

    // spec 040 FR-014
    @Test
    @DisplayName("un evento que NO pide el enlace automático se guarda como siempre y no toca a Google ni al interruptor")
    void sinPedirElEnlaceTodoEsComoSiempre() {
        m.interruptores.prendida = false;
        m.cuenta.borrar();

        EventoConMeetVista creado = m.servicio.crear(new CrearEventoCommand(ADMIN, "Zoom a mano", null, HOY_19H_LIMA, 60, LIMA,
                TipoUbicacion.ZOOM, "https://zoom.us/j/123", TipoAudiencia.TODOS, null, null, null, TipoEvento.SESION_ESPECIAL, false,
                false, false, null, Set.of(), List.of()));

        assertThat(creado.meet()).isNull();
        assertThat(creado.evento().evento().valorUbicacion()).isEqualTo("https://zoom.us/j/123");
        assertThat(m.google.llamadas).isEmpty();
    }

    // spec 040 FR-006, FR-008
    @Test
    @DisplayName("FR-006: si Google todavía no terminó la sala, el evento queda PENDIENTE y el proceso de fondo completa el link")
    void unaSalaPendienteLaCompletaElProcesoDeFondo() {
        m.google.tardaEnCrearLaSala = true;

        EventoConMeetVista creado = m.servicio.crear(comandoCrear(AccesoSalaMeet.ABIERTA, null, TipoUbicacion.MEET));

        assertThat(creado.meet().estado()).isEqualTo(EstadoSalaMeet.PENDIENTE);
        assertThat(creado.evento().evento().valorUbicacion()).isNull();

        m.reloj.avanzar(Duration.ofSeconds(61));
        m.trabajo.reintentarLoPendiente(m.reloj.now());

        Evento evento = m.eventos.byId(creado.evento().evento().id()).orElseThrow();
        assertThat(evento.valorUbicacion()).startsWith("https://meet.google.com/");
        assertThat(m.salas.todas()).singleElement().extracting(SalaMeet::estado).isEqualTo(EstadoSalaMeet.CREADO);
    }

    // spec 040 FR-008, SC-003
    @Test
    @DisplayName("SC-003: si Google está caído el evento se crea igual, con la sala FALLIDO y una frase clara; no se pierde nada")
    void sinGoogleElEventoSeCreaIgual() {
        m.google.fallaSiempre = new GoogleNoRespondeException("Google no pudo atender ahora (código 503). Se reintenta solo.", 503);

        EventoConMeetVista creado = m.servicio.crear(comandoCrear(AccesoSalaMeet.ABIERTA, null, TipoUbicacion.MEET));

        assertThat(m.eventos.porId).hasSize(1);
        assertThat(creado.evento().evento().valorUbicacion()).isNull();
        assertThat(creado.meet().estado()).isEqualTo(EstadoSalaMeet.FALLIDO);
        assertThat(creado.meet().mensaje()).isEqualTo("No pudimos crear el enlace de Meet.");
        SalaMeet sala = m.salas.todas().get(0);
        assertThat(sala.intentos()).isEqualTo(1);
        assertThat(sala.proximoIntento()).isAfter(m.reloj.now());
        assertThat(sala.ultimoError()).doesNotContain("meet.google.com");
    }

    // spec 040 FR-008, SC-004
    @Test
    @DisplayName("FR-008: lo que falló se reintenta con espera exponencial, y al volver Google el evento queda con su link")
    void loQueFalloSeReintentaYSeCompleta() {
        m.google.fallaSiempre = new GoogleNoRespondeException("Google no pudo atender ahora (código 503).", 503);
        m.servicio.crear(comandoCrear(AccesoSalaMeet.ABIERTA, null, TipoUbicacion.MEET));

        m.reloj.avanzar(Duration.ofSeconds(30));
        m.trabajo.reintentarLoPendiente(m.reloj.now());
        assertThat(m.google.llamadasDe("crear:")).as("todavía no le tocaba").isEqualTo(1);

        m.reloj.avanzar(Duration.ofMinutes(2));
        m.trabajo.reintentarLoPendiente(m.reloj.now());
        assertThat(m.google.llamadasDe("crear:")).isEqualTo(2);
        assertThat(m.salas.todas().get(0).proximoIntento()).as("la espera se duplica").isAfter(m.reloj.now().plus(Duration.ofMinutes(1)));

        m.google.fallaSiempre = null;
        m.reloj.avanzar(Duration.ofMinutes(5));
        m.trabajo.reintentarLoPendiente(m.reloj.now());

        assertThat(m.salas.todas().get(0).estado()).isEqualTo(EstadoSalaMeet.CREADO);
        assertThat(m.eventos.porId.values().iterator().next().valorUbicacion()).startsWith("https://meet.google.com/");
    }

    // spec 040 FR-008
    @Test
    @DisplayName("FR-008: tras 8 intentos fallidos el proceso de fondo se rinde; solo un «Reintentar» a mano lo vuelve a intentar")
    void seRindeTrasOchoIntentos() {
        m.google.fallaSiempre = new GoogleNoRespondeException("Google no pudo atender ahora (código 503).", 503);
        m.servicio.crear(comandoCrear(AccesoSalaMeet.ABIERTA, null, TipoUbicacion.MEET));

        for (int vez = 0; vez < 12; vez++) {
            m.reloj.avanzar(Duration.ofMinutes(30));
            m.trabajo.reintentarLoPendiente(m.reloj.now());
        }

        SalaMeet sala = m.salas.todas().get(0);
        assertThat(sala.intentos()).isEqualTo(8);
        assertThat(sala.proximoIntento()).isNull();
        assertThat(m.google.llamadasDe("crear:")).isEqualTo(8);
    }

    // spec 040 FR-005
    @Test
    @DisplayName("FR-005: editar el título, la hora o la duración actualiza el evento de Google y el link no cambia")
    void editarActualizaGoogleSinCambiarElLink() {
        EventoConMeetVista creado = m.servicio.crear(comandoCrear(AccesoSalaMeet.ABIERTA, null, TipoUbicacion.MEET));
        String enlace = creado.evento().evento().valorUbicacion();

        EventoConMeetVista editado = m.servicio.actualizar(comandoActualizar(creado, "Clase nueva", Instant.parse("2026-11-06T01:00:00Z"), AccesoSalaMeet.ABIERTA));

        assertThat(editado.evento().evento().valorUbicacion()).isEqualTo(enlace);
        assertThat(m.google.llamadasDe("actualizar:")).isEqualTo(1);
        PedidoDeSala pedido = m.google.ultimoPedido.values().iterator().next();
        assertThat(pedido.titulo()).isEqualTo("Clase nueva");
        assertThat(pedido.inicio()).isEqualTo(Instant.parse("2026-11-06T01:00:00Z"));
        assertThat(m.salas.todas()).singleElement().extracting(SalaMeet::estado).isEqualTo(EstadoSalaMeet.CREADO);
    }

    // spec 040 FR-005, FR-008, SC-004
    @Test
    @DisplayName("FR-008: si Google falla al actualizar, la edición se completa igual y queda PENDIENTE_DE_SINCRONIZAR hasta que el proceso de fondo lo termine")
    void editarConGoogleCaidoNoBloqueaLaEdicion() {
        EventoConMeetVista creado = m.servicio.crear(comandoCrear(AccesoSalaMeet.ABIERTA, null, TipoUbicacion.MEET));
        m.google.fallaSiempre = new GoogleNoRespondeException("Google no pudo atender ahora (código 503).", 503);

        EventoConMeetVista editado = m.servicio.actualizar(comandoActualizar(creado, "Clase nueva", HOY_19H_LIMA, AccesoSalaMeet.ABIERTA));

        assertThat(editado.evento().evento().titulo()).isEqualTo("Clase nueva");
        assertThat(editado.meet().estado()).isEqualTo(EstadoSalaMeet.PENDIENTE_DE_SINCRONIZAR);
        assertThat(editado.meet().mensaje()).isEqualTo("Los cambios todavía no llegaron a Google.");

        m.google.fallaSiempre = null;
        m.reloj.avanzar(Duration.ofMinutes(3));
        m.trabajo.reintentarLoPendiente(m.reloj.now());

        assertThat(m.salas.todas()).singleElement().extracting(SalaMeet::estado).isEqualTo(EstadoSalaMeet.CREADO);
        assertThat(m.google.ultimoPedido.values().iterator().next().titulo()).isEqualTo("Clase nueva");
    }

    // spec 040 FR-005
    @Test
    @DisplayName("FR-005: cambiar a otra ubicación (un link pegado) borra el evento de Google y limpia la asociación")
    void cambiarAOtraUbicacionBorraLaSala() {
        EventoConMeetVista creado = m.servicio.crear(comandoCrear(AccesoSalaMeet.ABIERTA, null, TipoUbicacion.MEET));

        m.servicio.actualizar(new ActualizarEventoCommand(ADMIN, creado.evento().evento().id(), "Clase en vivo", null, HOY_19H_LIMA, 90, LIMA,
                TipoUbicacion.ZOOM, "https://zoom.us/j/999", TipoAudiencia.TODOS, null, null, null, false, false, false, null, Set.of(),
                List.of()));

        assertThat(m.google.eventos).isEmpty();
        assertThat(m.salas.todas()).singleElement().extracting(SalaMeet::estado).isEqualTo(EstadoSalaMeet.BORRADO);
        Evento evento = m.eventos.byId(creado.evento().evento().id()).orElseThrow();
        assertThat(evento.usaSalaDeMeetAutomatica()).isFalse();
        assertThat(evento.valorUbicacion()).isEqualTo("https://zoom.us/j/999");
    }

    // spec 040 FR-005, FR-008
    @Test
    @DisplayName("FR-005: borrar el evento borra su sala en Google; si Google falla, la lápida espera y el proceso de fondo la termina aunque el evento ya no exista")
    void borrarElEventoBorraLaSalaAunqueGoogleFalle() {
        EventoConMeetVista creado = m.servicio.crear(comandoCrear(AccesoSalaMeet.ABIERTA, null, TipoUbicacion.MEET));
        m.google.fallaSiempre = new GoogleNoRespondeException("Google no pudo atender ahora (código 503).", 503);

        m.servicio.eliminar(ADMIN, creado.evento().evento().id());

        assertThat(m.eventos.porId).isEmpty();
        assertThat(m.google.eventos).hasSize(1);
        assertThat(m.salas.todas()).singleElement().extracting(SalaMeet::estado).isEqualTo(EstadoSalaMeet.PENDIENTE_DE_SINCRONIZAR);

        m.google.fallaSiempre = null;
        m.reloj.avanzar(Duration.ofMinutes(3));
        m.trabajo.reintentarLoPendiente(m.reloj.now());

        assertThat(m.google.eventos).isEmpty();
        assertThat(m.salas.todas()).singleElement().extracting(SalaMeet::estado).isEqualTo(EstadoSalaMeet.BORRADO);
    }

    // ─── Armado ─────────────────────────────────────────────────────────────────

    private static CrearEventoCommand comandoCrear(AccesoSalaMeet acceso, String valorUbicacion, TipoUbicacion tipo) {
        return new CrearEventoCommand(ADMIN, "Clase en vivo", null, HOY_19H_LIMA, 90, ZoneId.of("America/Lima"), tipo, valorUbicacion,
                TipoAudiencia.TODOS, null, null, null, TipoEvento.SESION_ESPECIAL, false, false, false, null, Set.of(), List.of(), null,
                acceso);
    }

    private static ActualizarEventoCommand comandoActualizar(EventoConMeetVista previo, String titulo, Instant iniciaEn, AccesoSalaMeet acceso) {
        Evento e = previo.evento().evento();
        return new ActualizarEventoCommand(ADMIN, e.id(), titulo, null, iniciaEn, 90, LIMA, TipoUbicacion.MEET, e.valorUbicacion(),
                TipoAudiencia.TODOS, null, null, null, false, false, false, null, Set.of(), List.of(), acceso);
    }
}

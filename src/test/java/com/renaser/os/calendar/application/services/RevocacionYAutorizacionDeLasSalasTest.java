package com.renaser.os.calendar.application.services;

import com.renaser.os.calendar.api.CuentaDeGoogleRevocadaEvent;
import com.renaser.os.calendar.application.ports.in.evento.CrearEventoUseCase.CrearEventoCommand;
import com.renaser.os.calendar.application.ports.in.meet.EventoConMeetVista;
import com.renaser.os.calendar.application.ports.out.meet.CuentaGoogleRevocadaException;
import com.renaser.os.calendar.domain.model.evento.Evento;
import com.renaser.os.calendar.domain.model.evento.EventoId;
import com.renaser.os.calendar.domain.model.evento.RolUsuario;
import com.renaser.os.calendar.domain.model.evento.TipoAudiencia;
import com.renaser.os.calendar.domain.model.evento.TipoEvento;
import com.renaser.os.calendar.domain.model.evento.TipoUbicacion;
import com.renaser.os.calendar.domain.model.meet.AccesoSalaMeet;
import com.renaser.os.calendar.domain.model.meet.AccionDeAuditoriaGoogle;
import com.renaser.os.calendar.domain.model.meet.EstadoDeLaCuenta;
import com.renaser.os.calendar.domain.model.meet.EstadoSalaMeet;
import com.renaser.os.calendar.domain.model.meet.RegistroDeAuditoriaGoogle;
import com.renaser.os.calendar.domain.model.meet.SalaMeet;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static com.renaser.os.calendar.application.services.MundoDeMeet.ADMIN;
import static com.renaser.os.calendar.application.services.MundoDeMeet.ALQUIMISTA;
import static com.renaser.os.calendar.application.services.MundoDeMeet.LIMA;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * La cuenta de Google que se vence (spec 040, FR-009, SC-005), quién puede usar todo esto (FR-015, SC-008, SC-007) y lo que queda en la auditoría
 * (FR-016, SC-010).
 */
class RevocacionYAutorizacionDeLasSalasTest {

    private static final Instant HOY_19H_LIMA = Instant.parse("2026-11-06T00:00:00Z");

    private MundoDeMeet m;

    @BeforeEach
    void setUp() {
        m = new MundoDeMeet();
        m.conectarLaCuenta();
    }

    // spec 040 FR-009, SC-005
    @Test
    @DisplayName("SC-005: si Google rechaza el permiso, la cuenta pasa a REVOCADA, lo pendiente se detiene y se avisa UNA vez, sin reintentos en bucle")
    void unPermisoVencidoSeDetectaAlPrimerUso() {
        m.google.fallaSiempre = new CuentaGoogleRevocadaException("Google ya no acepta el permiso de la cuenta (invalid_grant).");

        EventoConMeetVista creado = crear(AccesoSalaMeet.ABIERTA);

        assertThat(m.eventos.porId).as("el evento se crea igual").hasSize(1);
        assertThat(m.cuenta.actual.estado()).isEqualTo(EstadoDeLaCuenta.REVOCADA);
        assertThat(m.cuenta.actual.ultimoError()).isEqualTo("Hay que volver a conectar la cuenta de Google");
        assertThat(creado.meet().estado()).isEqualTo(EstadoSalaMeet.FALLIDO);
        SalaMeet sala = m.salas.todas().get(0);
        assertThat(sala.ultimoError()).isEqualTo("Hay que volver a conectar la cuenta de Google");
        assertThat(sala.proximoIntento()).as("reintentar sería inútil").isNull();
        assertThat(m.eventosPublicados).hasSize(1).first().isInstanceOf(CuentaDeGoogleRevocadaEvent.class);

        // Pasa el tiempo: el proceso de fondo no hace ninguna llamada más a Google.
        long llamadas = m.google.llamadas.size();
        m.reloj.avanzar(Duration.ofHours(3));
        m.trabajo.reintentarLoPendiente(m.reloj.now());
        assertThat(m.google.llamadas).hasSize((int) llamadas);
        // Y un fallo más el mismo día no manda otro aviso.
        m.revocacion.alRevocarse(null, "otra");
        assertThat(m.eventosPublicados).hasSize(1);
    }

    // spec 040 FR-009
    @Test
    @DisplayName("FR-009: el aviso se repite una vez por día mientras la cuenta siga revocada, con un identificador distinto cada día")
    void unAvisoPorDia() {
        m.google.fallaSiempre = new CuentaGoogleRevocadaException("Google ya no acepta el permiso de la cuenta (invalid_grant).");
        crear(AccesoSalaMeet.ABIERTA);
        assertThat(m.eventosPublicados).hasSize(1);

        m.reloj.avanzar(Duration.ofHours(2));
        m.trabajo.mantenimiento(m.reloj.now());
        assertThat(m.eventosPublicados).as("mismo día: ninguno más").hasSize(1);

        m.reloj.avanzar(Duration.ofDays(1));
        m.trabajo.mantenimiento(m.reloj.now());
        assertThat(m.eventosPublicados).hasSize(2);
        assertThat(m.eventosPublicados.stream().map(e -> ((CuentaDeGoogleRevocadaEvent) e).avisoId()).distinct()).hasSize(2);
    }

    // spec 040 FR-009
    @Test
    @DisplayName("FR-009: reconectar la cuenta reencola lo pendiente de las fechas que no pasaron, y el link se crea")
    void reconectarReencolaLoPendiente() {
        m.google.fallaSiempre = new CuentaGoogleRevocadaException("Google ya no acepta el permiso de la cuenta (invalid_grant).");
        EventoConMeetVista creado = crear(AccesoSalaMeet.ABIERTA);
        m.google.fallaSiempre = null;

        m.conectarLaCuenta();
        m.salas.reencolarLoPendiente(m.reloj.now());
        m.trabajo.reintentarLoPendiente(m.reloj.now());

        Evento evento = m.eventos.byId(creado.evento().evento().id()).orElseThrow();
        assertThat(evento.valorUbicacion()).startsWith("https://meet.google.com/");
        assertThat(m.salas.todas()).singleElement().extracting(SalaMeet::estado).isEqualTo(EstadoSalaMeet.CREADO);
    }

    // spec 040 FR-015, SC-008
    @ParameterizedTest(name = "{0}")
    @EnumSource(value = RolUsuario.class, names = {"TRAINEE", "MENTOR", "MENTOR_LEAD"})
    @DisplayName("SC-008: aprendiz, mentor y líder no crean, editan, borran, cancelan ni reintentan con Meet (403)")
    void soloAdministracionUsaElEnlaceAutomatico(RolUsuario rol) {
        UserId persona = UserId.of(UUID.randomUUID());
        m.roles.put(persona, rol);
        Evento existente = m.eventoDeUnDia(AccesoSalaMeet.ABIERTA, HOY_19H_LIMA);

        assertThatThrownBy(() -> m.servicio.crear(comando(persona, AccesoSalaMeet.ABIERTA))).isInstanceOf(NotAuthorizedException.class);
        assertThatThrownBy(() -> m.servicio.eliminar(persona, existente.id())).isInstanceOf(NotAuthorizedException.class);
        assertThatThrownBy(() -> m.servicio.cancelarOcurrencia(persona, existente.id(), HOY_19H_LIMA)).isInstanceOf(NotAuthorizedException.class);
        assertThatThrownBy(() -> m.reintento.reintentar(persona, existente.id())).isInstanceOf(NotAuthorizedException.class);
        assertThatThrownBy(() -> new DisponibilidadDeMeetService(m.acceso, m.disponibilidad).consultar(persona))
                .isInstanceOf(NotAuthorizedException.class);

        assertThat(m.eventos.porId).hasSize(1);
        assertThat(m.google.llamadas).isEmpty();
    }

    // spec 040 FR-015
    @Test
    @DisplayName("FR-015: ADMIN y ALCHEMIST sí; un ADMIN suspendido recibe 403 aunque su token sea válido")
    void adminYAlquimistaSiUnSuspendidoNo() {
        assertThat(crearComo(ADMIN).evento().evento().valorUbicacion()).startsWith("https://meet.google.com/");
        assertThat(crearComo(ALQUIMISTA).evento().evento().valorUbicacion()).startsWith("https://meet.google.com/");

        m.suspendidos.add(ADMIN);

        assertThatThrownBy(() -> crearComo(ADMIN)).isInstanceOf(NotAuthorizedException.class);
    }

    // spec 040 SC-007
    @Test
    @DisplayName("SC-007: quien no administra ve el evento como siempre: sin resumen de Meet y con el link de siempre")
    void elAprendizNoVeNadaNuevo() {
        EventoConMeetVista creado = crear(AccesoSalaMeet.ABIERTA);
        EventoId id = creado.evento().evento().id();

        EventoConMeetVista queVeElAprendiz = m.servicio.obtener(MundoDeMeet.APRENDIZ, id);
        EventoConMeetVista queVeAdmin = m.servicio.obtener(ADMIN, id);

        assertThat(queVeElAprendiz.meet()).isNull();
        assertThat(queVeElAprendiz.evento().evento().valorUbicacion()).startsWith("https://meet.google.com/");
        assertThat(queVeAdmin.meet()).isNotNull();
    }

    // spec 040 FR-016, SC-010, FR-036
    @Test
    @DisplayName("SC-010: cada acción con Google deja su fila: qué, quién, sobre qué evento, cómo salió y el código HTTP; sin enlaces ni tokens")
    void cadaAccionDejaSuFilaDeAuditoria() {
        m.google.fallaProxima = new com.renaser.os.calendar.application.ports.out.meet.GoogleNoRespondeException("Google no pudo atender ahora (código 503).", 503);
        EventoConMeetVista creado = crear(AccesoSalaMeet.ABIERTA);
        m.reloj.avanzar(Duration.ofMinutes(2));
        m.trabajo.reintentarLoPendiente(m.reloj.now());

        List<RegistroDeAuditoriaGoogle> filas = m.auditoriaDeGoogle.filas;
        EventoId id = creado.evento().evento().id();
        assertThat(filas).extracting(RegistroDeAuditoriaGoogle::accion)
                .containsExactly(AccionDeAuditoriaGoogle.CREAR_SALA, AccionDeAuditoriaGoogle.CREAR_SALA, AccionDeAuditoriaGoogle.AJUSTAR_ACCESO,
                        AccionDeAuditoriaGoogle.AJUSTAR_GRABACION);
        RegistroDeAuditoriaGoogle fallida = filas.get(0);
        assertThat(fallida.exito()).isFalse();
        assertThat(fallida.codigoHttp()).isEqualTo(503);
        assertThat(fallida.actor()).isEqualTo(ADMIN);
        assertThat(fallida.eventoId()).isEqualTo(id.value());
        assertThat(fallida.correlacion()).isNotBlank();
        assertThat(filas.get(1).actor()).as("el reintento automático lo hace el sistema").isNull();
        assertThat(filas).allSatisfy(f -> assertThat(f.toString()).doesNotContain("meet.google.com", "refresh-token"));
        assertThat(java.util.Arrays.stream(RegistroDeAuditoriaGoogle.class.getRecordComponents()).map(c -> c.getName()))
                .containsExactly("id", "en", "accion", "actor", "eventoId", "exito", "codigoHttp", "correlacion");
    }

    // ─── Armado ─────────────────────────────────────────────────────────────────

    private EventoConMeetVista crear(AccesoSalaMeet acceso) {
        return m.servicio.crear(comando(ADMIN, acceso));
    }

    private EventoConMeetVista crearComo(UserId actor) {
        return m.servicio.crear(comando(actor, AccesoSalaMeet.ABIERTA));
    }

    private static CrearEventoCommand comando(UserId actor, AccesoSalaMeet acceso) {
        return new CrearEventoCommand(actor, "Clase en vivo", null, HOY_19H_LIMA, 90, LIMA, TipoUbicacion.MEET, null, TipoAudiencia.TODOS, null,
                null, null, TipoEvento.SESION_ESPECIAL, false, false, false, null, Set.of(), List.of(), null, acceso);
    }
}

package com.renaser.os.calendar.application.services;

import com.renaser.os.calendar.application.ports.in.evento.EventoVista;
import com.renaser.os.calendar.application.ports.in.evento.ListarEventosParaVisorUseCase.OcurrenciaVista;
import com.renaser.os.calendar.application.ports.out.meet.ConfiguracionDeGooglePort;
import com.renaser.os.calendar.application.ports.out.participante.ConsultarProgresoParticipanteCalendarPort.ProgresoParticipanteCalendar;
import com.renaser.os.calendar.domain.model.confirmacion.EstadoConfirmacion;
import com.renaser.os.calendar.domain.model.evento.Evento;
import com.renaser.os.calendar.domain.model.evento.EventoId;
import com.renaser.os.calendar.domain.model.evento.Excepcion;
import com.renaser.os.calendar.domain.model.evento.ExpansorOcurrencias;
import com.renaser.os.calendar.domain.model.evento.FrecuenciaRecurrencia;
import com.renaser.os.calendar.domain.model.evento.Ocurrencia;
import com.renaser.os.calendar.domain.model.evento.Recurrencia;
import com.renaser.os.calendar.domain.model.evento.RolUsuario;
import com.renaser.os.calendar.domain.model.evento.TipoAudiencia;
import com.renaser.os.calendar.domain.model.evento.TipoEvento;
import com.renaser.os.calendar.domain.model.evento.TipoUbicacion;
import com.renaser.os.calendar.domain.model.meet.AccesoSalaMeet;
import com.renaser.os.calendar.domain.model.meet.CuentaDeGoogle;
import com.renaser.os.shared.application.Interruptores;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.FuncionalidadNoDisponibleException;
import com.renaser.os.shared.domain.IdGenerator;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.AlcanceDeFormacionSoloEscuela;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.mockito.Mockito.mock;

/**
 * El mundo de las pruebas del enlace de Meet automático (spec 040): un Google simulado, la base en memoria, un reloj que se mueve y todo el cableado
 * real de los servicios de aplicación. Cada prueba cuenta una historia —«creo un evento, Google falla, reintento»— sin repetir el armado.
 */
final class MundoDeMeet {

    static final ZoneId LIMA = ZoneId.of("America/Lima");
    static final UserId ADMIN = UserId.of(UUID.fromString("00000000-0000-4000-8000-0000000000a1"));
    static final UserId ALQUIMISTA = UserId.of(UUID.fromString("00000000-0000-4000-8000-0000000000a2"));
    static final UserId MENTOR = UserId.of(UUID.fromString("00000000-0000-4000-8000-0000000000b1"));
    static final UserId APRENDIZ = UserId.of(UUID.fromString("00000000-0000-4000-8000-0000000000c1"));
    static final String CORREO = "renasercorporation@gmail.com";

    /** Un reloj que se mueve: las esperas y los reintentos dependen de la hora. */
    static final class Reloj implements Clock {
        private Instant ahora;

        Reloj(Instant ahora) {
            this.ahora = ahora;
        }

        @Override
        public Instant now() {
            return ahora;
        }

        @Override
        public LocalDate today() {
            return ahora.atZone(ZoneOffset.UTC).toLocalDate();
        }

        void avanzar(Duration duracion) {
            ahora = ahora.plus(duracion);
        }
    }

    /** Los interruptores de Soporte TI, a mano. */
    static final class InterruptoresFalsos implements Interruptores {
        boolean prendida = true;

        @Override
        public boolean prendida(String clave) {
            return prendida;
        }

        @Override
        public boolean prendidaPara(String clave, UserId actor) {
            return prendida;
        }

        @Override
        public void exigirPrendidaPara(String clave, UserId actor) {
            if (!prendida) {
                throw new FuncionalidadNoDisponibleException(clave);
            }
        }
    }

    /** 10:00 en Lima del jueves 5 de noviembre de 2026. */
    final Reloj reloj = new Reloj(Instant.parse("2026-11-05T15:00:00Z"));
    final GoogleSimulado google = new GoogleSimulado();
    final EnMemoria.Eventos eventos = new EnMemoria.Eventos();
    final EnMemoria.Salas salas = new EnMemoria.Salas(eventos);
    final EnMemoria.Cuenta cuenta = new EnMemoria.Cuenta();
    final EnMemoria.Auditoria auditoriaDeGoogle = new EnMemoria.Auditoria();
    final InterruptoresFalsos interruptores = new InterruptoresFalsos();
    final List<Object> eventosPublicados = new ArrayList<>();
    final ConfiguracionDeGooglePort configuracion = new ConfiguracionDeGooglePort() {
        @Override
        public boolean estaConfigurada() {
            return true;
        }

        @Override
        public String correoEsperado() {
            return "";
        }

        @Override
        public int ventanaEnDias() {
            return 60;
        }
    };

    final Map<UserId, RolUsuario> roles = new HashMap<>(Map.of(ADMIN, RolUsuario.ADMIN, ALQUIMISTA, RolUsuario.ALCHEMIST,
            MENTOR, RolUsuario.MENTOR, APRENDIZ, RolUsuario.TRAINEE));
    final Set<UserId> suspendidos = new HashSet<>();
    final AtomicLong contador = new AtomicLong(0x1000);
    final IdGenerator ids = () -> new UUID(0L, contador.incrementAndGet());
    final AuditoriaDeGoogle auditor = new AuditoriaDeGoogle(auditoriaDeGoogle, reloj, ids);
    final RevocacionDeLaCuenta revocacion;
    final PasosConGoogle pasos;
    final SincronizadorDeSalas sincronizador;
    final AccesoEventoService acceso;
    final DisponibilidadDeLasSalas disponibilidad;
    final EventoConSalaDeMeetService servicio;
    final TrabajoEnSegundoPlanDeMeetService trabajo;
    final ReintentoDeSalaDeMeetService reintento;

    MundoDeMeet() {
        ApplicationEventPublisher publicador = eventosPublicados::add;
        revocacion = new RevocacionDeLaCuenta(cuenta, cuenta, salas, auditor, publicador, mock(PlatformTransactionManager.class), reloj);
        pasos = new PasosConGoogle(google, salas, auditor, revocacion, reloj, () -> 0.0);
        sincronizador = new SincronizadorDeSalas(eventos, eventos, eventos, salas, salas, configuracion, pasos, reloj, ids);
        acceso = new AccesoEventoService(this::progreso, null, null, (usuario, tipo) -> false, (celula, usuario) -> false,
                new AlcanceDeFormacionSoloEscuela());
        disponibilidad = new DisponibilidadDeLasSalas(interruptores, configuracion, cuenta);
        trabajo = new TrabajoEnSegundoPlanDeMeetService(cuenta, salas, salas, auditoriaDeGoogle, sincronizador, revocacion, auditor);
        reintento = new ReintentoDeSalaDeMeetService(acceso, disponibilidad, sincronizador, eventos, this::obtener, auditor, reloj);
        servicio = new EventoConSalaDeMeetService(this::listar, this::obtener, this::crear, this::actualizar, this::eliminar,
                this::cancelar, eventos, salas, salas, acceso, disponibilidad, sincronizador, auditor, reloj);
    }

    // ─── Escenario ──────────────────────────────────────────────────────────────

    /** La cuenta de Google ya conectada y activa. */
    void conectarLaCuenta() {
        CuentaDeGoogle conectada = CuentaDeGoogle.conectar(CORREO, "refresh-token-de-prueba-que-no-es-real",
                Set.of(CuentaDeGoogle.PERMISO_CALENDARIO_PROPIO, CuentaDeGoogle.PERMISO_AJUSTES_DE_LA_SALA), ADMIN, reloj.now());
        conectada.fijarCalendario("calendario-renaser");
        cuenta.guardar(conectada);
    }

    /** Un evento de un solo día que pide el enlace automático, ya guardado, sin sala todavía. */
    Evento eventoDeUnDia(AccesoSalaMeet acceso, Instant iniciaEn) {
        Evento evento = Evento.crear(EventoId.of(ids.newId()), "Clase en vivo", null, iniciaEn, 90, LIMA, TipoUbicacion.MEET, null,
                TipoAudiencia.TODOS, null, null, null, TipoEvento.SESION_ESPECIAL, false, false, false, null, Set.of(), List.of(),
                ADMIN, reloj, com.renaser.os.shared.domain.Formacion.ESCUELA, acceso);
        eventos.guardar(evento);
        return evento;
    }

    /** Un evento que se repite cada semana en esos días, desde {@code iniciaEn}. */
    Evento eventoSemanal(AccesoSalaMeet acceso, Instant iniciaEn, DayOfWeek... dias) {
        Recurrencia semanal = new Recurrencia(FrecuenciaRecurrencia.SEMANAL, 1, null, null, Set.of(dias));
        Evento evento = Evento.crear(EventoId.of(ids.newId()), "Mentoría semanal", null, iniciaEn, 60, LIMA, TipoUbicacion.MEET, null,
                TipoAudiencia.TODOS, null, null, null, TipoEvento.SESION_ESPECIAL, false, false, false, semanal, Set.of(), List.of(),
                ADMIN, reloj, com.renaser.os.shared.domain.Formacion.ESCUELA, acceso);
        eventos.guardar(evento);
        return evento;
    }

    // ─── Los casos de uso de siempre, mínimos, sobre la base en memoria ─────────

    private Optional<ProgresoParticipanteCalendar> progreso(UserId usuario) {
        return Optional.of(new ProgresoParticipanteCalendar(10, LIMA, roles.getOrDefault(usuario, RolUsuario.TRAINEE), suspendidos.contains(usuario), null));
    }

    private EventoVista crear(com.renaser.os.calendar.application.ports.in.evento.CrearEventoUseCase.CrearEventoCommand c) {
        Evento evento = Evento.crear(EventoId.of(ids.newId()), c.titulo(), c.descripcion(), c.iniciaEn(), c.duracionMinutos(), c.timezone(),
                c.tipoUbicacion(), c.valorUbicacion(), c.tipoAudiencia(), c.nivelMinimoId(), c.cursoId(), c.celulaDestinoId(),
                c.tipoEvento(), c.notificarAlCrear(), c.recordarPorEmail(), c.recordatoriosPersonalizados(), c.recurrencia(),
                c.rolesDestino(), c.reglasRecordatorio(), c.actorId(), reloj, com.renaser.os.shared.domain.Formacion.ESCUELA,
                c.accesoSalaMeet());
        return new EventoVista(eventos.guardar(evento), null);
    }

    private EventoVista actualizar(com.renaser.os.calendar.application.ports.in.evento.ActualizarEventoUseCase.ActualizarEventoCommand c) {
        Evento evento = eventos.byId(c.eventoId()).orElseThrow();
        evento.actualizar(c.titulo(), c.descripcion(), c.iniciaEn(), c.duracionMinutos(), c.timezone(), c.tipoUbicacion(),
                c.valorUbicacion(), c.tipoAudiencia(), c.nivelMinimoId(), c.cursoId(), c.celulaDestinoId(), c.notificarAlCrear(),
                c.recordarPorEmail(), c.recordatoriosPersonalizados(), c.recurrencia(), c.rolesDestino(), c.reglasRecordatorio(),
                reloj, c.accesoSalaMeet());
        return new EventoVista(eventos.guardar(evento), null);
    }

    private void eliminar(UserId actor, EventoId id) {
        eventos.eliminar(id);
    }

    private void cancelar(UserId actor, EventoId id, Instant inicioOcurrencia) {
        eventos.excepciones.computeIfAbsent(id, k -> new ArrayList<>()).add(Excepcion.cancelar(ids.newId(), id, inicioOcurrencia));
    }

    private EventoVista obtener(UserId actor, EventoId id) {
        return new EventoVista(eventos.byId(id).orElseThrow(), null);
    }

    private List<OcurrenciaVista> listar(UserId actor, Instant desde, Instant hasta, com.renaser.os.shared.domain.Formacion formacion) {
        List<OcurrenciaVista> lista = new ArrayList<>();
        for (Evento evento : eventos.porId.values()) {
            List<Ocurrencia> ocurrencias = ExpansorOcurrencias.expandir(evento.iniciaEn(), evento.duracionMinutos(), evento.timezone(),
                    evento.recurrencia(), desde, hasta, eventos.porEvento(evento.id()));
            for (Ocurrencia o : ocurrencias) {
                lista.add(new OcurrenciaVista(evento, null, o.inicioOcurrencia(), o.iniciaEn(), o.duracionMinutos(), evento.titulo(),
                        (EstadoConfirmacion) null));
            }
        }
        return lista;
    }
}

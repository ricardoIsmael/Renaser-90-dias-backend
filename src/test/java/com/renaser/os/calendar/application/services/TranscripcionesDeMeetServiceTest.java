package com.renaser.os.calendar.application.services;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.renaser.os.calendar.application.ports.in.meet.TranscripcionesDeMeetUseCase.TranscripcionDescargada;
import com.renaser.os.calendar.application.ports.out.evento.LoadEventoPort;
import com.renaser.os.calendar.application.ports.out.meet.AuditoriaGooglePort;
import com.renaser.os.calendar.application.ports.out.meet.CifradoDeSecretosPort;
import com.renaser.os.calendar.application.ports.out.meet.CuentaGoogleRevocadaException;
import com.renaser.os.calendar.application.ports.out.meet.GoogleNoRespondeException;
import com.renaser.os.calendar.application.ports.out.meet.GoogleRechazoException;
import com.renaser.os.calendar.application.ports.out.meet.LoadCuentaGooglePort;
import com.renaser.os.calendar.application.ports.out.meet.ReunionesDeMeetPort;
import com.renaser.os.calendar.application.ports.out.meet.TranscripcionesDeMeetPort;
import com.renaser.os.calendar.domain.model.evento.Evento;
import com.renaser.os.calendar.domain.model.evento.EventoId;
import com.renaser.os.calendar.domain.model.meet.AccionDeAuditoriaGoogle;
import com.renaser.os.calendar.domain.model.meet.CuentaDeGoogle;
import com.renaser.os.calendar.domain.model.meet.RegistroDeAuditoriaGoogle;
import com.renaser.os.calendar.domain.model.meet.transcripcion.EstadoDeTranscripcion;
import com.renaser.os.calendar.domain.model.meet.transcripcion.LecturaDeReunion;
import com.renaser.os.calendar.domain.model.meet.transcripcion.PendienteDeTranscripcion;
import com.renaser.os.calendar.domain.model.meet.transcripcion.TranscripcionDeSesion;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TranscripcionesDeMeetServiceTest {

    private static final Instant AHORA = Instant.parse("2026-11-06T03:00:00Z");
    private static final Instant FIN = AHORA.minus(Duration.ofMinutes(30));
    private static final String TEXTO = "Ana: Buenas noches, hoy hablamos del cierre de la semana.\nLuis: Me costó la constancia.";
    private static final String GRABACION = "https://drive.google.com/file/d/grabacion-secreta/view";
    private static final UserId ADMIN = UserId.of(UUID.randomUUID());
    private static final UserId MENTOR = UserId.of(UUID.randomUUID());

    private final Puerto puerto = new Puerto();
    private final List<RegistroDeAuditoriaGoogle> auditados = new ArrayList<>();
    private final ReunionesDeMeetPort reuniones = mock(ReunionesDeMeetPort.class);
    private final LoadEventoPort eventos = mock(LoadEventoPort.class);
    private final AccesoEventoService acceso = mock(AccesoEventoService.class);
    private final Cifrado cifrado = new Cifrado();
    private CuentaDeGoogle cuenta;
    private TranscripcionesDeMeetService servicio;
    private final ListAppender<ILoggingEvent> registro = new ListAppender<>();
    private Level nivelAnterior;
    private Logger raiz;

    @BeforeEach
    void armar() {
        cuenta = CuentaDeGoogle.conectar("renasercorporation@gmail.com", "1//x",
                Set.of(CuentaDeGoogle.PERMISO_CALENDARIO_PROPIO, CuentaDeGoogle.PERMISO_LEER_REUNIONES), ADMIN, AHORA.minus(Duration.ofDays(3)));
        LoadCuentaGooglePort cuentas = () -> Optional.ofNullable(cuenta);
        Clock reloj = new Clock() {
            @Override
            public Instant now() {
                return AHORA;
            }

            @Override
            public LocalDate today() {
                return AHORA.atZone(ZoneOffset.UTC).toLocalDate();
            }
        };
        AuditoriaGooglePort auditoriaPort = new AuditoriaGooglePort() {
            @Override
            public void registrar(RegistroDeAuditoriaGoogle r) {
                auditados.add(r);
            }

            @Override
            public List<RegistroDeAuditoriaGoogle> ultimos(int cuantos) {
                return auditados;
            }

            @Override
            public int purgarAnterioresA(Instant limite) {
                return 0;
            }
        };
        AuditoriaDeGoogle auditoria = new AuditoriaDeGoogle(auditoriaPort, reloj, UUID::randomUUID);
        servicio = new TranscripcionesDeMeetService(puerto, reuniones, cuentas, cifrado, eventos, acceso, auditoria, reloj, UUID::randomUUID,
                180, 5);
        when(eventos.byId(any())).thenReturn(Optional.of(mock(Evento.class)));
        doThrow(new NotAuthorizedException("No tienes permiso para administrar el calendario")).when(acceso).requireAdministraElCalendario(MENTOR);
        raiz = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        nivelAnterior = raiz.getLevel();
        raiz.setLevel(Level.ALL);
        registro.start();
        raiz.addAppender(registro);
    }

    @AfterEach
    void soltar() {
        raiz.detachAppender(registro);
        raiz.setLevel(nivelAnterior);
    }

    private PendienteDeTranscripcion pendiente(Boolean transcripcionConfirmada) {
        PendienteDeTranscripcion p = new PendienteDeTranscripcion(UUID.randomUUID(), EventoId.of(UUID.randomUUID()), LocalDate.of(2026, 11, 5),
                "https://meet.google.com/abc-defg-hij", FIN.minus(Duration.ofHours(1)), FIN, transcripcionConfirmada, null);
        puerto.pendientes.add(p);
        return p;
    }

    // spec 047 FR-002, FR-003, FR-006, FR-013
    @Test
    @DisplayName("FR-002: una sesión terminada se lee de Google y queda LISTA, con su constancia, en la ventana de esa fecha")
    void guardaLaTranscripcion() {
        PendienteDeTranscripcion p = pendiente(true);
        when(reuniones.leer(p.enlace(), p.desde(), p.hasta())).thenReturn(new LecturaDeReunion(1, 2, TEXTO, GRABACION, false));

        assertThat(servicio.buscarPendientes(AHORA)).isEqualTo(1);

        TranscripcionDeSesion t = puerto.guardadas.get(p.salaId());
        assertThat(t.estado()).isEqualTo(EstadoDeTranscripcion.LISTA);
        assertThat(t.texto()).isEqualTo(TEXTO);
        assertThat(t.borrarDespuesDe()).as("P3-B: 6 meses").isEqualTo(AHORA.plus(Duration.ofDays(180)));
        assertThat(auditados).extracting(RegistroDeAuditoriaGoogle::accion).containsExactly(AccionDeAuditoriaGoogle.TRANSCRIPCION_GUARDADA);
    }

    // spec 047 FR-009
    @Test
    @DisplayName("FR-009: si la 040 confirmó que la cuenta no deja transcribir, no se le pregunta a Google")
    void sinPlanNoSeLlamaAGoogle() {
        PendienteDeTranscripcion p = pendiente(false);

        servicio.buscarPendientes(AHORA);

        assertThat(puerto.guardadas.get(p.salaId()).estado()).isEqualTo(EstadoDeTranscripcion.SIN_TRANSCRIPCION);
        org.mockito.Mockito.verifyNoInteractions(reuniones);
    }

    // spec 047 FR-019
    @Test
    @DisplayName("FR-019: sin el permiso de leer reuniones la fila queda SIN_PERMISO y no se llama a Google; un 403 de Google da lo mismo")
    void sinPermiso() {
        cuenta = CuentaDeGoogle.conectar("renasercorporation@gmail.com", "1//x", Set.of(CuentaDeGoogle.PERMISO_CALENDARIO_PROPIO), ADMIN, AHORA);
        PendienteDeTranscripcion p = pendiente(true);
        servicio.buscarPendientes(AHORA);
        assertThat(puerto.guardadas.get(p.salaId()).estado()).isEqualTo(EstadoDeTranscripcion.SIN_PERMISO);
        org.mockito.Mockito.verifyNoInteractions(reuniones);

        cuenta = CuentaDeGoogle.conectar("renasercorporation@gmail.com", "1//x",
                Set.of(CuentaDeGoogle.PERMISO_CALENDARIO_PROPIO, CuentaDeGoogle.PERMISO_LEER_REUNIONES), ADMIN, AHORA);
        puerto.pendientes.clear();
        PendienteDeTranscripcion otra = pendiente(true);
        when(reuniones.leer(any(), any(), any())).thenThrow(new GoogleRechazoException("Google no dio permiso", 403));
        servicio.buscarPendientes(AHORA);
        assertThat(puerto.guardadas.get(otra.salaId()).estado()).isEqualTo(EstadoDeTranscripcion.SIN_PERMISO);
    }

    // spec 047 FR-008
    @Test
    @DisplayName("FR-008: si Google no responde se reintenta con espera; si el permiso se revocó, se corta la vuelta sin tocar nada")
    void reintentos() {
        PendienteDeTranscripcion p = pendiente(true);
        when(reuniones.leer(any(), any(), any())).thenThrow(new GoogleNoRespondeException("cuota", 429));
        servicio.buscarPendientes(AHORA);
        TranscripcionDeSesion t = puerto.guardadas.get(p.salaId());
        assertThat(t.estado()).isEqualTo(EstadoDeTranscripcion.ESPERANDO);
        assertThat(t.proximoIntento()).isAfter(AHORA);

        puerto.pendientes.clear();
        puerto.guardadas.clear();
        pendiente(true);
        org.mockito.Mockito.reset(reuniones);
        when(reuniones.leer(any(), any(), any())).thenThrow(new CuentaGoogleRevocadaException("revocada"));
        assertThat(servicio.buscarPendientes(AHORA)).isZero();
        assertThat(puerto.guardadas).isEmpty();
    }

    // spec 047 FR-010, SC-004
    @Test
    @DisplayName("FR-010: sin clave de cifrado no se busca nada; y ni el texto ni la grabación llegan nunca al registro")
    void sinClaveYSinRegistros() {
        PendienteDeTranscripcion p = pendiente(true);
        cifrado.configurado = false;
        assertThat(servicio.buscarPendientes(AHORA)).isZero();
        org.mockito.Mockito.verifyNoInteractions(reuniones);

        cifrado.configurado = true;
        when(reuniones.leer(any(), any(), any())).thenReturn(new LecturaDeReunion(1, 2, TEXTO, GRABACION, false));
        servicio.buscarPendientes(AHORA);
        servicio.descargar(ADMIN, p.eventoId(), p.fecha(), "Revisión pedida por la dirección");
        String todo = registro.list.stream().map(ILoggingEvent::getFormattedMessage).reduce("", (a, b) -> a + "\n" + b);
        assertThat(todo).doesNotContain("Buenas noches").doesNotContain("grabacion-secreta");
    }

    // spec 047 FR-011, FR-013
    @Test
    @DisplayName("FR-011: solo ADMIN y ALCHEMIST sacan el texto, con un motivo de 10 a 500 caracteres, y queda constancia de la lectura")
    void descargar() {
        PendienteDeTranscripcion p = pendiente(true);
        when(reuniones.leer(any(), any(), any())).thenReturn(new LecturaDeReunion(1, 2, TEXTO, GRABACION, false));
        servicio.buscarPendientes(AHORA);

        assertThatThrownBy(() -> servicio.descargar(MENTOR, p.eventoId(), p.fecha(), "Quiero leerla por curiosidad"))
                .isInstanceOf(NotAuthorizedException.class);
        assertThatThrownBy(() -> servicio.descargar(ADMIN, p.eventoId(), p.fecha(), "corto")).isInstanceOf(IllegalArgumentException.class);
        assertThat(puerto.lecturas).isEmpty();

        TranscripcionDescargada d = servicio.descargar(ADMIN, p.eventoId(), p.fecha(), "Reclamo de una alumna sobre la sesión");
        assertThat(d.texto()).isEqualTo(TEXTO);
        assertThat(d.enlaceDeLaGrabacion()).isEqualTo(GRABACION);
        assertThat(puerto.lecturas).containsExactly("LECTURA: Reclamo de una alumna sobre la sesión");
        assertThat(auditados).extracting(RegistroDeAuditoriaGoogle::accion).contains(AccionDeAuditoriaGoogle.TRANSCRIPCION_LEIDA);
        assertThat(d.toString()).doesNotContain("Buenas noches");
    }

    // spec 047 FR-012
    @Test
    @DisplayName("FR-012: el estado por fecha es solo de Administración")
    void estados() {
        assertThatThrownBy(() -> servicio.estados(MENTOR, EventoId.of(UUID.randomUUID()))).isInstanceOf(NotAuthorizedException.class);
        assertThat(servicio.estados(ADMIN, EventoId.of(UUID.randomUUID()))).isEmpty();
    }

    // spec 047 FR-014, FR-015, SC-006
    @Test
    @DisplayName("FR-014: la retención borra lo vencido y deja la constancia TRANSCRIPCION_BORRADA")
    void retencion() {
        UUID id = UUID.randomUUID();
        EventoId evento = EventoId.of(UUID.randomUUID());
        puerto.paraBorrar.add(new TranscripcionesDeMeetPort.ParaBorrar(id, evento, LocalDate.of(2026, 5, 1)));

        assertThat(servicio.aplicarRetencion(AHORA)).isEqualTo(1);
        assertThat(puerto.borradas).containsExactly(id);
        assertThat(auditados).extracting(RegistroDeAuditoriaGoogle::accion).containsExactly(AccionDeAuditoriaGoogle.TRANSCRIPCION_BORRADA);
        assertThat(puerto.purgadoAntesDe).isEqualTo(AHORA.minus(Duration.ofDays(365)));
    }

    // spec 047 FR-016
    @Test
    @DisplayName("FR-016: ADMIN borra a pedido la de una fecha, con motivo, y queda la constancia; el mentor no puede")
    void borrarAPedido() {
        PendienteDeTranscripcion p = pendiente(true);
        when(reuniones.leer(any(), any(), any())).thenReturn(new LecturaDeReunion(1, 2, TEXTO, GRABACION, false));
        servicio.buscarPendientes(AHORA);

        assertThatThrownBy(() -> servicio.borrarAPedido(MENTOR, p.eventoId(), p.fecha(), "Lo pidió la alumna por la Ley 29733"))
                .isInstanceOf(NotAuthorizedException.class);
        servicio.borrarAPedido(ADMIN, p.eventoId(), p.fecha(), "Lo pidió la alumna por la Ley 29733");

        assertThat(puerto.borradas).containsExactly(puerto.guardadas.get(p.salaId()).id());
        assertThat(puerto.lecturas).containsExactly("BORRADO: Lo pidió la alumna por la Ley 29733");
        assertThat(auditados).extracting(RegistroDeAuditoriaGoogle::accion).contains(AccionDeAuditoriaGoogle.TRANSCRIPCION_BORRADA);
    }

    private static final class Cifrado implements CifradoDeSecretosPort {
        boolean configurado = true;

        @Override
        public boolean estaConfigurado() {
            return configurado;
        }

        @Override
        public String cifrar(String textoPlano) {
            return textoPlano;
        }

        @Override
        public String descifrar(String textoCifrado) {
            return textoCifrado;
        }

        @Override
        public int versionDeLaClave() {
            return 1;
        }
    }

    private static final class Puerto implements TranscripcionesDeMeetPort {
        final List<PendienteDeTranscripcion> pendientes = new ArrayList<>();
        final Map<UUID, TranscripcionDeSesion> guardadas = new HashMap<>();
        final List<ParaBorrar> paraBorrar = new ArrayList<>();
        final List<UUID> borradas = new ArrayList<>();
        final List<String> lecturas = new ArrayList<>();
        Instant purgadoAntesDe;

        @Override
        public List<PendienteDeTranscripcion> pendientes(Instant ahora, int limite) {
            return List.copyOf(pendientes);
        }

        @Override
        public void guardar(TranscripcionDeSesion t) {
            guardadas.put(t.salaId(), t);
        }

        @Override
        public Optional<TranscripcionDeSesion> deLaFecha(EventoId eventoId, LocalDate fecha) {
            return pendientes.stream().filter(p -> p.eventoId().equals(eventoId) && p.fecha().equals(fecha))
                    .map(p -> guardadas.get(p.salaId())).filter(java.util.Objects::nonNull).findFirst();
        }

        @Override
        public List<EstadoPorFecha> estadosDelEvento(EventoId eventoId) {
            return List.of();
        }

        @Override
        public List<ParaBorrar> paraBorrar(Instant ahora, int limite) {
            return List.copyOf(paraBorrar);
        }

        @Override
        public void borrar(UUID transcripcionId, Instant ahora) {
            borradas.add(transcripcionId);
        }

        @Override
        public void registrarAcceso(UserId actor, EventoId eventoId, LocalDate fecha, String accion, String motivo, Instant en) {
            lecturas.add(accion + ": " + motivo);
        }

        @Override
        public int purgarLecturasAnterioresA(Instant limite) {
            purgadoAntesDe = limite;
            return 0;
        }
    }
}

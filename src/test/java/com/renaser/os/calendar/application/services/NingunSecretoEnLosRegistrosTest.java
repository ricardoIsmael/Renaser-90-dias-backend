package com.renaser.os.calendar.application.services;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.ThrowableProxy;
import ch.qos.logback.core.read.ListAppender;
import com.renaser.os.calendar.application.ports.in.evento.CrearEventoUseCase.CrearEventoCommand;
import com.renaser.os.calendar.application.ports.in.meet.EventoConMeetVista;
import com.renaser.os.calendar.application.ports.out.meet.CuentaGoogleRevocadaException;
import com.renaser.os.calendar.application.ports.out.meet.GoogleRechazoException;
import com.renaser.os.calendar.domain.model.evento.TipoAudiencia;
import com.renaser.os.calendar.domain.model.evento.TipoEvento;
import com.renaser.os.calendar.domain.model.evento.TipoUbicacion;
import com.renaser.os.calendar.domain.model.meet.AccesoSalaMeet;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;

import static com.renaser.os.calendar.application.services.MundoDeMeet.ADMIN;
import static com.renaser.os.calendar.application.services.MundoDeMeet.LIMA;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * SC-006 (spec 040): ni el permiso permanente, ni un token de acceso, ni el {@code state}, ni el enlace de una sala aparecen en un registro, aunque Google
 * falle diciendo justo eso. Se ejecutan las rutas que más hablan (crear con fallo, revocar, reintentar) con secretos puestos a propósito dentro de los
 * mensajes de las excepciones, y se recorre todo lo que los registros escribieron.
 */
class NingunSecretoEnLosRegistrosTest {

    private static final String TOKEN = "1//permiso-permanente-SECRETO-123";
    private static final String ACCESO = "ya29.token-de-acceso-SECRETO-456";
    private static final String STATE = "state-SECRETO-789";

    private final ListAppender<ILoggingEvent> registro = new ListAppender<>();
    private Logger raiz;
    private Level nivelAnterior;
    private MundoDeMeet m;

    @BeforeEach
    void capturar() {
        raiz = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        nivelAnterior = raiz.getLevel();
        raiz.setLevel(Level.ALL);
        registro.start();
        raiz.addAppender(registro);
        m = new MundoDeMeet();
        m.conectarLaCuenta();
    }

    @AfterEach
    void soltar() {
        raiz.detachAppender(registro);
        raiz.setLevel(nivelAnterior);
    }

    private String todoLoQueSeEscribio() {
        StringBuilder texto = new StringBuilder();
        for (ILoggingEvent e : registro.list) {
            texto.append(e.getFormattedMessage()).append('\n');
            if (e.getThrowableProxy() != null) {
                texto.append(ThrowableProxy.class.cast(e.getThrowableProxy()).getThrowable()).append('\n');
            }
        }
        return texto.toString();
    }

    // spec 040 FR-011, SC-006
    @Test
    @DisplayName("SC-006: aunque Google falle con un mensaje que trae secretos, ninguno llega a un registro")
    void unFalloDeGoogleNoDejaSecretosEnLosRegistros() {
        m.google.fallaSiempre = new GoogleRechazoException("Google dijo: " + TOKEN + " " + ACCESO + " " + STATE, 400);

        EventoConMeetVista creado = m.servicio.crear(new CrearEventoCommand(ADMIN, "Clase en vivo", null, Instant.parse("2026-11-06T00:00:00Z"), 90,
                LIMA, TipoUbicacion.MEET, null, TipoAudiencia.TODOS, null, null, null, TipoEvento.SESION_ESPECIAL, false, false, false, null,
                Set.of(), List.of(), null, AccesoSalaMeet.ABIERTA));
        m.reloj.avanzar(Duration.ofMinutes(3));
        m.trabajo.reintentarLoPendiente(m.reloj.now());
        m.google.fallaSiempre = new CuentaGoogleRevocadaException("invalid_grant " + TOKEN);
        m.reloj.avanzar(Duration.ofMinutes(10));
        m.trabajo.reintentarLoPendiente(m.reloj.now());

        assertThat(creado.evento().evento().id()).isNotNull();
        assertThat(registro.list).as("las rutas con fallo sí escriben avisos: la prueba no es vacía").isNotEmpty();
        assertThat(todoLoQueSeEscribio()).doesNotContain(TOKEN, ACCESO, STATE, "SECRETO").doesNotContain("meet.google.com");
    }

    // spec 040 FR-011, SC-006
    @Test
    @DisplayName("SC-006: lo que la persona lee como motivo del fallo no repite lo que dijo Google")
    void elMotivoQueSeGuardaNoRepiteAGoogle() {
        m.google.fallaSiempre = new GoogleRechazoException("Google dijo: " + TOKEN + " " + ACCESO, 400);

        m.servicio.crear(new CrearEventoCommand(ADMIN, "Clase en vivo", null, Instant.parse("2026-11-06T00:00:00Z"), 90, LIMA, TipoUbicacion.MEET,
                null, TipoAudiencia.TODOS, null, null, null, TipoEvento.SESION_ESPECIAL, false, false, false, null, Set.of(), List.of(), null,
                AccesoSalaMeet.ABIERTA));

        assertThat(m.salas.todas()).singleElement().satisfies(sala -> assertThat(String.valueOf(sala.ultimoError())).doesNotContain(TOKEN, ACCESO));
        assertThat(m.auditoriaDeGoogle.filas.toString()).doesNotContain(TOKEN, ACCESO);
    }
}

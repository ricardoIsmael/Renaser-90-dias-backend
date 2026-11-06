package com.renaser.os.calendar.application.services;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.renaser.os.calendar.application.ports.out.meet.AuditoriaGooglePort;
import com.renaser.os.calendar.application.ports.out.meet.LoadCuentaGooglePort;
import com.renaser.os.calendar.application.ports.out.meet.LoadSalaMeetPort;
import com.renaser.os.calendar.application.ports.out.meet.SaveSalaMeetPort;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * #98: sin {@code EVENTOS_GOOGLE_CLAVE_DE_CIFRADO} y con una cuenta de Google guardada, leer la cuenta lanza {@link IllegalStateException}. El
 * proceso de Meet la trata como no disponible, no se cae ni llama a Google, y lo registra UNA sola vez en vez de cada minuto.
 */
@DisplayName("Meet sin la clave de cifrado de Google (#98)")
class MeetSinClaveDeCifradoTest {

    private static final Instant AHORA = Instant.parse("2026-11-06T15:00:00Z");

    private final LoadCuentaGooglePort cuenta = mock(LoadCuentaGooglePort.class);
    private final LoadSalaMeetPort salas = mock(LoadSalaMeetPort.class);
    private final SaveSalaMeetPort guardarSalas = mock(SaveSalaMeetPort.class);
    private final AuditoriaGooglePort auditoriaPort = mock(AuditoriaGooglePort.class);
    private final SincronizadorDeSalas sincronizador = mock(SincronizadorDeSalas.class);
    private final RevocacionDeLaCuenta revocacion = mock(RevocacionDeLaCuenta.class);
    private final AuditoriaDeGoogle auditoria = mock(AuditoriaDeGoogle.class);
    private final TrabajoEnSegundoPlanDeMeetService trabajo =
            new TrabajoEnSegundoPlanDeMeetService(cuenta, salas, guardarSalas, auditoriaPort, sincronizador, revocacion, auditoria);

    private final ListAppender<ILoggingEvent> registro = new ListAppender<>();
    private final Logger logger = (Logger) LoggerFactory.getLogger(TrabajoEnSegundoPlanDeMeetService.class);

    @BeforeEach
    void escucharElRegistro() {
        registro.start();
        logger.addAppender(registro);
    }

    @AfterEach
    void dejarDeEscuchar() {
        logger.detachAppender(registro);
    }

    private long avisos() {
        return registro.list.stream().filter(e -> e.getLevel() == Level.WARN).count();
    }

    @Test
    @DisplayName("la cuenta ilegible cuenta como no disponible y se avisa una sola vez")
    void sinClaveNoSeCaeYAvisaUnaVez() {
        when(cuenta.cargar()).thenThrow(new IllegalStateException("Falta la clave de cifrado con que se guardó el secreto de la cuenta de Google."));

        for (int minuto = 0; minuto < 5; minuto++) {
            assertThatCode(() -> assertThat(trabajo.reintentarLoPendiente(AHORA)).isZero()).doesNotThrowAnyException();
        }
        assertThatCode(() -> trabajo.mantenimiento(AHORA)).doesNotThrowAnyException();

        assertThat(avisos()).isEqualTo(1);
        verify(salas, never()).eventosConTrabajoPendiente(any(), anyInt());
        verify(salas, never()).eventosConSalaAutomatica(any());
        verify(sincronizador, never()).reconciliar(any(), any(), any(), org.mockito.ArgumentMatchers.anyBoolean(), any());
    }

    @Test
    @DisplayName("si la cuenta se vuelve a poder leer y falla otra vez, se avisa de nuevo")
    void trasRecuperarseVuelveAAvisar() {
        IllegalStateException sinClave = new IllegalStateException("Falta la clave de cifrado de la cuenta de Google.");
        when(cuenta.cargar()).thenThrow(sinClave).thenThrow(sinClave).thenReturn(Optional.empty()).thenThrow(sinClave);

        for (int minuto = 0; minuto < 4; minuto++) {
            trabajo.reintentarLoPendiente(AHORA);
        }

        assertThat(avisos()).isEqualTo(2);
    }
}

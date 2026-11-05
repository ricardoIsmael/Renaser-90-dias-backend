package com.renaser.os.calendar.application.services;

import com.renaser.os.calendar.api.CuentaDeGoogleRevocadaEvent;
import com.renaser.os.calendar.application.ports.out.meet.LoadCuentaGooglePort;
import com.renaser.os.calendar.application.ports.out.meet.SaveCuentaGooglePort;
import com.renaser.os.calendar.application.ports.out.meet.SaveSalaMeetPort;
import com.renaser.os.calendar.domain.model.meet.AccionDeAuditoriaGoogle;
import com.renaser.os.calendar.domain.model.meet.CuentaDeGoogle;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.UserId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;

/**
 * Qué pasa cuando Google deja de aceptar el permiso de la cuenta (spec 040, FR-009): la cuenta pasa a {@code REVOCADA}, lo pendiente se detiene
 * (reintentar sería inútil), queda la auditoría y se avisa a Administración y Alquimista. Un solo aviso por día mientras siga revocada: ni el
 * aviso se repite en el día ni hay reintentos en bucle.
 */
@Component
class RevocacionDeLaCuenta {

    private static final Logger log = LoggerFactory.getLogger(RevocacionDeLaCuenta.class);

    static final String MOTIVO = "Hay que volver a conectar la cuenta de Google";

    private final LoadCuentaGooglePort loadCuentaPort;
    private final SaveCuentaGooglePort saveCuentaPort;
    private final SaveSalaMeetPort saveSalaPort;
    private final AuditoriaDeGoogle auditoria;
    private final ApplicationEventPublisher eventos;
    private final TransactionTemplate transaccion;
    private final Clock clock;

    RevocacionDeLaCuenta(LoadCuentaGooglePort loadCuentaPort, SaveCuentaGooglePort saveCuentaPort,
                         SaveSalaMeetPort saveSalaPort, AuditoriaDeGoogle auditoria, ApplicationEventPublisher eventos,
                         PlatformTransactionManager transactionManager, Clock clock) {
        this.loadCuentaPort = loadCuentaPort;
        this.saveCuentaPort = saveCuentaPort;
        this.saveSalaPort = saveSalaPort;
        this.auditoria = auditoria;
        this.eventos = eventos;
        this.transaccion = new TransactionTemplate(transactionManager);
        this.clock = clock;
    }

    /** Google acaba de rechazar el permiso. Es idempotente: una cuenta ya revocada no vuelve a registrarse ni a avisar el mismo día. */
    void alRevocarse(UserId actor, String correlacion) {
        Instant ahora = clock.now();
        try {
            CuentaDeGoogle cuenta = loadCuentaPort.cargar().orElse(null);
            if (cuenta == null) {
                return;
            }
            boolean eraActiva = cuenta.activa();
            if (eraActiva) {
                cuenta.revocar(MOTIVO, ahora);
                saveCuentaPort.guardar(cuenta);
                saveSalaPort.detenerLoPendiente(MOTIVO, ahora);
                auditoria.registrar(AccionDeAuditoriaGoogle.CUENTA_REVOCADA, actor, null, false, null, correlacion);
            }
            avisarUnaVezPorDia(cuenta, ahora);
        } catch (RuntimeException ex) {
            log.warn("[calendar.RevocacionDeLaCuenta] no se pudo registrar la cuenta revocada: {}", ex.getClass().getSimpleName());
        }
    }

    /** Todos los días mientras siga revocada (el proceso diario). */
    void avisarSiSigueRevocada() {
        try {
            loadCuentaPort.cargar().filter(cuenta -> !cuenta.activa()).ifPresent(cuenta -> avisarUnaVezPorDia(cuenta, clock.now()));
        } catch (RuntimeException ex) {
            log.warn("[calendar.RevocacionDeLaCuenta] no se pudo avisar de la cuenta revocada: {}", ex.getClass().getSimpleName());
        }
    }

    private void avisarUnaVezPorDia(CuentaDeGoogle cuenta, Instant ahora) {
        LocalDate hoy = ahora.atZone(ZoneOffset.UTC).toLocalDate();
        boolean yaAvisoHoy = cuenta.ultimoAvisoEn() != null && cuenta.ultimoAvisoEn().atZone(ZoneOffset.UTC).toLocalDate().equals(hoy);
        if (yaAvisoHoy) {
            return;
        }
        UUID avisoId = UUID.nameUUIDFromBytes(("google-revocada:" + hoy).getBytes(StandardCharsets.UTF_8));
        // Dentro de una transaccion: sin ella el evento no llega al outbox de Spring Modulith y nadie lo escucha.
        transaccion.executeWithoutResult(status -> eventos.publishEvent(new CuentaDeGoogleRevocadaEvent(avisoId, ahora)));
        cuenta.avisadaEl(ahora);
        saveCuentaPort.guardar(cuenta);
    }
}

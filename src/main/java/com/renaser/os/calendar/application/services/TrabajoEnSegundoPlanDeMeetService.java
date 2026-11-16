package com.renaser.os.calendar.application.services;

import com.renaser.os.calendar.application.ports.in.meet.SincronizarSalasDeMeetUseCase;
import com.renaser.os.calendar.application.ports.out.meet.AuditoriaGooglePort;
import com.renaser.os.calendar.application.ports.out.meet.ConfiguracionDeGooglePort;
import com.renaser.os.calendar.application.ports.out.meet.LoadCuentaGooglePort;
import com.renaser.os.calendar.application.ports.out.meet.LoadSalaMeetPort;
import com.renaser.os.calendar.application.ports.out.meet.SaveSalaMeetPort;
import com.renaser.os.calendar.domain.model.evento.EventoId;
import com.renaser.os.calendar.domain.model.meet.CuentaDeGoogle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Lo que hace el proceso en segundo plano con Google (spec 040, FR-008, FR-009, FR-017). Sin {@code @Transactional}: cada paso con Google va fuera de
 * toda transacción. Con la cuenta sin conectar o revocada no hace ninguna llamada: reintentar sería inútil (SC-005).
 */
@Service
class TrabajoEnSegundoPlanDeMeetService implements SincronizarSalasDeMeetUseCase {

    private static final Logger log = LoggerFactory.getLogger(TrabajoEnSegundoPlanDeMeetService.class);

    /** Eventos por pasada; lo que quede sale en la del minuto siguiente. */
    static final int EVENTOS_POR_PASADA = 50;
    /** Cuánto espera cada evento a Google en el proceso de fondo: más que una petición, porque nadie está esperando. */
    static final Duration PRESUPUESTO_POR_EVENTO = Duration.ofSeconds(20);
    /** Eventos por pasada del mantenimiento a los que se les migran las salas a la Meet API (FR-041): por tandas, lo que quede sale en la hora siguiente. */
    static final int MIGRACIONES_POR_PASADA = 25;
    /** Retención de la auditoría y de las lápidas (FR-016). */
    static final Duration RETENCION = Duration.ofDays(365);

    private final LoadCuentaGooglePort loadCuentaPort;
    private final LoadSalaMeetPort loadSalaPort;
    private final SaveSalaMeetPort saveSalaPort;
    private final AuditoriaGooglePort auditoriaPort;
    private final SincronizadorDeSalas sincronizador;
    private final RevocacionDeLaCuenta revocacion;
    private final AuditoriaDeGoogle auditoria;
    private final ConfiguracionDeGooglePort configuracion;
    /** Si ya se avisó que la cuenta no se puede leer (#98): el aviso sale una sola vez hasta que se vuelva a poder leer. */
    private final AtomicBoolean avisoDeCuentaIlegible = new AtomicBoolean(false);

    TrabajoEnSegundoPlanDeMeetService(LoadCuentaGooglePort loadCuentaPort, LoadSalaMeetPort loadSalaPort,
                                      SaveSalaMeetPort saveSalaPort, AuditoriaGooglePort auditoriaPort,
                                      SincronizadorDeSalas sincronizador, RevocacionDeLaCuenta revocacion,
                                      AuditoriaDeGoogle auditoria, ConfiguracionDeGooglePort configuracion) {
        this.configuracion = configuracion;
        this.loadCuentaPort = loadCuentaPort;
        this.loadSalaPort = loadSalaPort;
        this.saveSalaPort = saveSalaPort;
        this.auditoriaPort = auditoriaPort;
        this.sincronizador = sincronizador;
        this.revocacion = revocacion;
        this.auditoria = auditoria;
    }

    @Override
    public int reintentarLoPendiente(Instant ahora) {
        if (!hayCuentaActiva()) {
            return 0;
        }
        List<EventoId> eventos = loadSalaPort.eventosConTrabajoPendiente(ahora, EVENTOS_POR_PASADA);
        int tocados = 0;
        for (EventoId evento : eventos) {
            if (!hayCuentaActiva()) {
                break; // Google acaba de revocar el permiso: no se sigue (SC-005).
            }
            sincronizar(evento);
            tocados++;
        }
        return tocados;
    }

    @Override
    public void mantenimiento(Instant ahora) {
        revocacion.avisarSiSigueRevocada();
        if (hayCuentaActiva() && configuracion.salasConMeetApi()) {
            migrarSalasViejas(ahora);
        }
        if (hayCuentaActiva()) {
            // Derivar, no incrementar (regla 02): se recalcula cada evento con Meet y las fechas que entraron a la ventana reciben su sala.
            for (EventoId evento : loadSalaPort.eventosConSalaAutomatica(ahora).stream().limit(EVENTOS_POR_PASADA * 4L).toList()) {
                if (!hayCuentaActiva()) {
                    break;
                }
                sincronizar(evento);
            }
        }
        try {
            int lapidas = saveSalaPort.purgarLapidasAnterioresA(ahora.minus(RETENCION));
            int filas = auditoriaPort.purgarAnterioresA(ahora.minus(RETENCION));
            if (lapidas + filas > 0) {
                log.info("[calendar.TrabajoEnSegundoPlanDeMeet] purgadas {} lapida(s) y {} fila(s) de auditoria de mas de 12 meses", lapidas, filas);
            }
        } catch (RuntimeException ex) {
            log.warn("[calendar.TrabajoEnSegundoPlanDeMeet] no se pudo purgar: {}", ex.getClass().getSimpleName());
        }
    }

    /**
     * Spec 040 FR-041: las salas de Calendar de hoy en adelante pasan a una sala de la Meet API y las viejas se cierran, por tandas. Idempotente: lo
     * que ya se migró no vuelve a aparecer; lo que falló espera su turno (espera exponencial, máximo 8 intentos) y nunca rompe el evento.
     */
    private void migrarSalasViejas(Instant ahora) {
        // Un día de margen por la zona de cada evento; el plan descarta por zona las fechas pasadas y las sesiones que ya empezaron.
        List<EventoId> eventos = loadSalaPort.eventosConSalasPorMigrar(ahora.atZone(ZoneOffset.UTC).toLocalDate().minusDays(1), MIGRACIONES_POR_PASADA);
        for (EventoId evento : eventos) {
            if (!hayCuentaActiva()) {
                break;
            }
            sincronizar(evento);
        }
    }

    /**
     * Si la cuenta no se puede leer —por ejemplo, falta {@code EVENTOS_GOOGLE_CLAVE_DE_CIFRADO} y el secreto guardado no se puede descifrar—
     * se trata como no disponible: no se llama a Google y la excepción no llega al scheduler (#98). Se registra UNA vez; si después se
     * vuelve a poder leer, el siguiente fallo se avisa otra vez.
     */
    private boolean hayCuentaActiva() {
        try {
            boolean activa = loadCuentaPort.cargar().filter(CuentaDeGoogle::activa).isPresent();
            avisoDeCuentaIlegible.set(false);
            return activa;
        } catch (IllegalStateException ex) {
            if (avisoDeCuentaIlegible.compareAndSet(false, true)) {
                log.warn("[calendar.TrabajoEnSegundoPlanDeMeet] la cuenta de Google no se puede leer; se trata como no disponible hasta que se pueda: {}",
                        ex.getMessage());
            }
            return false;
        }
    }

    private void sincronizar(EventoId eventoId) {
        sincronizador.reconciliar(eventoId, null, auditoria.nuevaCorrelacion(), false, PRESUPUESTO_POR_EVENTO);
    }
}

package com.renaser.os.calendar.application.services;

import com.renaser.os.calendar.application.ports.in.meet.SincronizarSalasDeMeetUseCase;
import com.renaser.os.calendar.application.ports.out.meet.AuditoriaGooglePort;
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
import java.util.List;

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
    /** Retención de la auditoría y de las lápidas (FR-016). */
    static final Duration RETENCION = Duration.ofDays(365);

    private final LoadCuentaGooglePort loadCuentaPort;
    private final LoadSalaMeetPort loadSalaPort;
    private final SaveSalaMeetPort saveSalaPort;
    private final AuditoriaGooglePort auditoriaPort;
    private final SincronizadorDeSalas sincronizador;
    private final RevocacionDeLaCuenta revocacion;
    private final AuditoriaDeGoogle auditoria;

    TrabajoEnSegundoPlanDeMeetService(LoadCuentaGooglePort loadCuentaPort, LoadSalaMeetPort loadSalaPort,
                                      SaveSalaMeetPort saveSalaPort, AuditoriaGooglePort auditoriaPort,
                                      SincronizadorDeSalas sincronizador, RevocacionDeLaCuenta revocacion,
                                      AuditoriaDeGoogle auditoria) {
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

    private boolean hayCuentaActiva() {
        return loadCuentaPort.cargar().filter(CuentaDeGoogle::activa).isPresent();
    }

    private void sincronizar(EventoId eventoId) {
        sincronizador.reconciliar(eventoId, null, auditoria.nuevaCorrelacion(), false, PRESUPUESTO_POR_EVENTO);
    }
}

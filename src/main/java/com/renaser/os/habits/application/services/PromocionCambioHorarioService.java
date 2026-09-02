package com.renaser.os.habits.application.services;

import com.renaser.os.habits.application.ports.in.preferencia.PromoverCambiosHorarioProgramadosUseCase;
import com.renaser.os.habits.application.ports.out.preferencia.HistorialCambioHorarioPort;
import com.renaser.os.habits.application.ports.out.preferencia.LoadCambioHorarioPendientePort;
import com.renaser.os.habits.application.ports.out.preferencia.LoadPreferenciaHorarioPort;
import com.renaser.os.habits.application.ports.out.preferencia.SaveCambioHorarioPendientePort;
import com.renaser.os.habits.application.ports.out.preferencia.SavePreferenciaHorarioPort;
import com.renaser.os.habits.domain.model.preferencia.CambioHorarioPendiente;
import com.renaser.os.habits.domain.model.preferencia.PreferenciaHorario;
import com.renaser.os.shared.domain.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Cierra E-53: hasta ahora {@link CambioHorarioPendiente} se escribia y no lo leia nadie, asi
 * que el cambio "programado para manana" que la API confirmaba no llegaba a regir nunca.
 *
 * <p><b>Decision — el cambio diferido SI cobra cupo, pero el dia que empieza a regir, no el dia
 * que se pide.</b> {@code PreferenciaHorarioService.requireCupoDisponible} deliberadamente no
 * cobra cuando el cambio es diferido (el pedido nunca se rechaza: "no se improvisa el dia" no
 * puede volverse "perdiste la decision"). Si ademas la promocion no cobrara, diferir seria la
 * via para saltarse la cuota semanal entera: bastaria pedir todos los cambios con la ventana ya
 * arrancada. Cobrar al promover mantiene la invariante que le da sentido al contador —
 * <em>una fila de {@code historial_cambios_horario} = un cambio de horario que efectivamente
 * rigio</em> — y cobra en la semana correcta, la de la fecha efectiva. La promocion nunca
 * rechaza por falta de cupo: puede dejar la cuota de esa semana al tope (o pasada), lo que
 * simplemente impide reacomodar habitos NUEVOS hasta la semana siguiente.
 */
@Service
public class PromocionCambioHorarioService implements PromoverCambiosHorarioProgramadosUseCase {

    private static final Logger log = LoggerFactory.getLogger(PromocionCambioHorarioService.class);

    private final LoadCambioHorarioPendientePort loadCambioPendientePort;
    private final SaveCambioHorarioPendientePort saveCambioPendientePort;
    private final LoadPreferenciaHorarioPort loadPreferenciaPort;
    private final SavePreferenciaHorarioPort savePreferenciaPort;
    private final HistorialCambioHorarioPort historialPort;
    private final Clock clock;
    /**
     * Transaccion PROPIA (REQUIRES_NEW) para el barrido nocturno: cada pendiente se promueve
     * en su propia transaccion, aislada de las demas (C-6, docs/informes/
     * auditoria-seguridad-concurrencia-2026-09-01.html) — antes todo el barrido era una
     * unica transaccion y un pendiente corrupto revertia los ya promovidos esa noche.
     */
    private final TransactionTemplate transaccionPropia;

    public PromocionCambioHorarioService(LoadCambioHorarioPendientePort loadCambioPendientePort,
                                          SaveCambioHorarioPendientePort saveCambioPendientePort,
                                          LoadPreferenciaHorarioPort loadPreferenciaPort,
                                          SavePreferenciaHorarioPort savePreferenciaPort,
                                          HistorialCambioHorarioPort historialPort, Clock clock,
                                          PlatformTransactionManager transactionManager) {
        this.loadCambioPendientePort = loadCambioPendientePort;
        this.saveCambioPendientePort = saveCambioPendientePort;
        this.loadPreferenciaPort = loadPreferenciaPort;
        this.savePreferenciaPort = savePreferenciaPort;
        this.historialPort = historialPort;
        this.clock = clock;
        this.transaccionPropia = new TransactionTemplate(transactionManager);
        this.transaccionPropia.setPropagationBehavior(Propagation.REQUIRES_NEW.value());
    }

    @Override
    public int promoverLosQueRigenEn(LocalDate fecha) {
        List<CambioHorarioPendiente> vencidos = loadCambioPendientePort.queYaRigenEn(fecha);
        Instant ahora = clock.now();
        int promovidos = 0;
        int fallidos = 0;
        for (CambioHorarioPendiente pendiente : vencidos) {
            try {
                transaccionPropia.executeWithoutResult(status -> promover(pendiente, ahora));
                promovidos++;
            } catch (RuntimeException ex) {
                fallidos++;
                log.warn("[habits] no se pudo promover el cambio de horario pendiente de {} para el habito {}: {}",
                        pendiente.participanteId(), pendiente.habitoId(), ex.toString());
            }
        }
        if (!vencidos.isEmpty()) {
            log.info(
                    "[habits] promocion de cambios de horario con fecha efectiva <= {}: {} promovido(s), {} fallido(s) de {} candidato(s)",
                    fecha, promovidos, fallidos, vencidos.size());
        }
        return promovidos;
    }

    private void promover(CambioHorarioPendiente pendiente, Instant ahora) {
        PreferenciaHorario preferencia = preferenciaDestino(pendiente, ahora);
        preferencia.aplicarAhora(pendiente.horaDisparo(), pendiente.horaLimite(), ahora);
        aplicarRecordatorioSiVino(preferencia, pendiente, ahora);
        savePreferenciaPort.save(preferencia);
        historialPort.registrar(pendiente.participanteId(), pendiente.habitoId(), pendiente.fechaEfectiva(),
                pendiente.horaDisparo(), pendiente.horaLimite(), ahora);
        saveCambioPendientePort.borrar(pendiente.participanteId(), pendiente.habitoId());
    }

    /**
     * La FK compuesta de {@code cambios_horario_pendientes} garantiza que la fila padre exista
     * (E-54), pero el {@code orElseGet} se queda igual: si alguna vez se borrara la preferencia
     * sin arrastrar el pendiente, promover tiene que seguir siendo posible en vez de tirar.
     */
    private PreferenciaHorario preferenciaDestino(CambioHorarioPendiente pendiente, Instant ahora) {
        return loadPreferenciaPort.porParticipanteYHabito(pendiente.participanteId(), pendiente.habitoId())
                .orElseGet(() -> PreferenciaHorario.crear(pendiente.participanteId(), pendiente.habitoId(),
                        pendiente.horaDisparo(), pendiente.horaLimite(), ahora));
    }

    /** {@code recordatorio_activo} es nullable en el pendiente: null = "no toques el recordatorio". */
    private static void aplicarRecordatorioSiVino(PreferenciaHorario preferencia, CambioHorarioPendiente pendiente,
                                                   Instant ahora) {
        if (pendiente.recordatorioActivo() != null) {
            preferencia.actualizarRecordatorio(pendiente.recordatorioActivo(), pendiente.minutosRecordatorio(), ahora);
        }
    }
}

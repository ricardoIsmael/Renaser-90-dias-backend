package com.renaser.os.habits.application.services;

import com.renaser.os.habits.application.ports.in.preferencia.PromoverCambiosHorarioProgramadosUseCase;
import com.renaser.os.habits.application.ports.out.preferencia.HistorialCambioHorarioPort;
import com.renaser.os.habits.application.ports.out.preferencia.LoadCambioHorarioPendientePort;
import com.renaser.os.habits.application.ports.out.preferencia.LoadPreferenciaHorarioPort;
import com.renaser.os.habits.application.ports.out.preferencia.SaveCambioHorarioPendientePort;
import com.renaser.os.habits.application.ports.out.preferencia.SavePreferenciaHorarioPort;
import com.renaser.os.habits.domain.model.preferencia.CambioHorarioPendiente;
import com.renaser.os.habits.domain.model.preferencia.PreferenciaHorario;
import com.renaser.os.habits.domain.model.registro.CorteDeExpiracion;
import com.renaser.os.habits.domain.model.registro.JornadaDelDia;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.UserId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

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

    /** Participantes por consulta de zonas. */
    static final int TAMANO_LOTE = 200;

    private final LoadCambioHorarioPendientePort loadCambioPendientePort;
    private final SaveCambioHorarioPendientePort saveCambioPendientePort;
    private final LoadPreferenciaHorarioPort loadPreferenciaPort;
    private final SavePreferenciaHorarioPort savePreferenciaPort;
    private final HistorialCambioHorarioPort historialPort;
    private final ZonasDelPadron zonas;
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
                                          HistorialCambioHorarioPort historialPort, ZonasDelPadron zonas,
                                          Clock clock, PlatformTransactionManager transactionManager) {
        this.loadCambioPendientePort = loadCambioPendientePort;
        this.saveCambioPendientePort = saveCambioPendientePort;
        this.loadPreferenciaPort = loadPreferenciaPort;
        this.savePreferenciaPort = savePreferenciaPort;
        this.historialPort = historialPort;
        this.zonas = zonas;
        this.clock = clock;
        this.transaccionPropia = new TransactionTemplate(transactionManager);
        this.transaccionPropia.setPropagationBehavior(Propagation.REQUIRES_NEW.value());
    }

    /**
     * E-557: el pendiente rige cuando llega su {@code fecha_efectiva} EN LA ZONA de su participante. Se piden primero
     * todos los que podrian regir en alguna zona ({@link CorteDeExpiracion#fechaMasTardiaPosible}, un superconjunto
     * pequeno: lo programado con dias de antelacion) y el dominio decide por persona. Sin {@code @Transactional} sobre
     * el barrido: cada pendiente en SU transaccion, y el que falla queda para la proxima hora.
     */
    @Override
    public int promoverLosQueYaRigen() {
        Instant ahora = clock.now();
        List<CambioHorarioPendiente> candidatos = loadCambioPendientePort
                .queYaRigenEn(CorteDeExpiracion.fechaMasTardiaPosible(ahora));
        int promovidos = 0;
        int fallidos = 0;
        int sinSuDiaTodavia = 0;
        for (List<CambioHorarioPendiente> lote : enLotes(candidatos)) {
            Map<UserId, ZoneId> zonasDelLote = zonas.leerLote(participantesDe(lote));
            for (CambioHorarioPendiente pendiente : lote) {
                ResultadoDeLaPromocion resultado = promoverSiLeToca(pendiente, zonasDelLote, ahora);
                promovidos += resultado.promovidos();
                fallidos += resultado.fallidos();
                sinSuDiaTodavia += resultado.sinSuDiaTodavia();
            }
        }
        registrarElBarrido(promovidos, fallidos, candidatos.size() - sinSuDiaTodavia);
        return promovidos;
    }

    @Override
    public int promoverLosDe(UserId participanteId, LocalDate hoyEnSuZona) {
        Instant ahora = clock.now();
        int promovidos = 0;
        for (CambioHorarioPendiente pendiente : loadCambioPendientePort.deParticipante(participanteId)) {
            if (pendiente.rigeEn(hoyEnSuZona) && promoverEnTransaccionPropia(pendiente, ahora)) {
                promovidos++;
            }
        }
        return promovidos;
    }

    /** Un pendiente que falla (zona rota, fila corrupta) queda para la proxima hora; los demas siguen. */
    private ResultadoDeLaPromocion promoverSiLeToca(CambioHorarioPendiente pendiente,
                                                    Map<UserId, ZoneId> zonasDelLote, Instant ahora) {
        try {
            LocalDate hoyEnSuZona = JornadaDelDia.de(zonas.de(pendiente.participanteId(), zonasDelLote), ahora).hoy();
            if (!pendiente.rigeEn(hoyEnSuZona)) {
                return ResultadoDeLaPromocion.TODAVIA_NO;
            }
            return promoverEnTransaccionPropia(pendiente, ahora) ? ResultadoDeLaPromocion.PROMOVIDO
                    : ResultadoDeLaPromocion.YA_PROMOVIDO_POR_OTRO;
        } catch (RuntimeException ex) {
            log.warn("[habits] no se pudo promover el cambio de horario pendiente de {} para el habito {}: {}",
                    pendiente.participanteId(), pendiente.habitoId(), ex.toString());
            return ResultadoDeLaPromocion.FALLIDO;
        }
    }

    /**
     * C-6: cada pendiente en su transaccion. {@code false} si otro lo promovio antes (dos instancias, o este barrido y
     * el que arma el dia): no se cobra dos veces.
     */
    private boolean promoverEnTransaccionPropia(CambioHorarioPendiente pendiente, Instant ahora) {
        return Boolean.TRUE.equals(transaccionPropia.execute(status -> promover(pendiente, ahora)));
    }

    private void registrarElBarrido(int promovidos, int fallidos, int candidatos) {
        if (candidatos > 0) {
            log.info("[habits] promocion de cambios de horario: {} promovido(s), {} fallido(s) de {} candidato(s)",
                    promovidos, fallidos, candidatos);
        }
    }

    private static List<List<CambioHorarioPendiente>> enLotes(List<CambioHorarioPendiente> pendientes) {
        List<List<CambioHorarioPendiente>> lotes = new ArrayList<>();
        for (int desde = 0; desde < pendientes.size(); desde += TAMANO_LOTE) {
            lotes.add(pendientes.subList(desde, Math.min(desde + TAMANO_LOTE, pendientes.size())));
        }
        return lotes;
    }

    private static Set<UserId> participantesDe(List<CambioHorarioPendiente> lote) {
        return lote.stream().map(CambioHorarioPendiente::participanteId).collect(Collectors.toSet());
    }

    /** Que paso con un pendiente en este barrido. */
    private enum ResultadoDeLaPromocion {
        PROMOVIDO, YA_PROMOVIDO_POR_OTRO, TODAVIA_NO, FALLIDO;

        int promovidos() {
            return this == PROMOVIDO ? 1 : 0;
        }

        int fallidos() {
            return this == FALLIDO ? 1 : 0;
        }

        int sinSuDiaTodavia() {
            return this == TODAVIA_NO ? 1 : 0;
        }
    }

    /**
     * Primero se borra el pendiente y solo quien lo borro de verdad lo cobra: dos promociones simultaneas del mismo
     * pendiente (E-557: ahora hay dos caminos, el barrido y la generacion del dia) se serializan en la fila y la
     * segunda sale sin hacer nada, en vez de aplicar dos veces el cambio y cobrar dos cupos.
     */
    private boolean promover(CambioHorarioPendiente pendiente, Instant ahora) {
        if (!saveCambioPendientePort.borrar(pendiente.participanteId(), pendiente.habitoId())) {
            return false;
        }
        PreferenciaHorario preferencia = preferenciaDestino(pendiente, ahora);
        preferencia.aplicarAhora(pendiente.horaDisparo(), pendiente.horaLimite(), ahora);
        aplicarRecordatorioSiVino(preferencia, pendiente, ahora);
        savePreferenciaPort.save(preferencia);
        historialPort.registrar(pendiente.participanteId(), pendiente.habitoId(), pendiente.fechaEfectiva(),
                pendiente.horaDisparo(), pendiente.horaLimite(), ahora);
        return true;
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

package com.renaser.os.points.application.services;

import com.renaser.os.points.api.ConteoDelDia;
import com.renaser.os.points.api.SemanaDelSemaforoCerradaEvent;
import com.renaser.os.points.application.ports.out.semaforo.GuardarDiasDelSemaforoPort;
import com.renaser.os.points.application.ports.out.semaforo.SemanasDelSemaforoPort;
import com.renaser.os.points.domain.model.semaforo.CierreSemanal;
import com.renaser.os.points.domain.model.semaforo.CumplimientoDelDia;
import com.renaser.os.points.domain.model.semaforo.FotoSemanal;
import com.renaser.os.points.domain.model.semaforo.MedicionDeLaPersona;
import com.renaser.os.points.domain.model.semaforo.PlanDeCierre;
import com.renaser.os.shared.domain.UserId;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * El trabajo del barrido con UNA persona, en su propia transacción: guardar sus días cerrados y cerrar
 * las semanas que terminaron. Es un bean aparte de {@link CierreDelSemaforoService} a propósito: la
 * transacción solo existe si la llamada pasa por el proxy de Spring. Con auto-invocación el evento
 * saldría sin transacción y el {@code @ApplicationModuleListener} de {@code notifications} nunca se
 * ejecutaría (es lo que le pasa hoy a {@code AvisosService}, E-250).
 */
@Service
public class CierreDeParticipanteService {

    private final GuardarDiasDelSemaforoPort guardarDiasPort;
    private final SemanasDelSemaforoPort semanasPort;
    private final ApplicationEventPublisher eventos;

    public CierreDeParticipanteService(GuardarDiasDelSemaforoPort guardarDiasPort, SemanasDelSemaforoPort semanasPort,
                                       ApplicationEventPublisher eventos) {
        this.guardarDiasPort = guardarDiasPort;
        this.semanasPort = semanasPort;
        this.eventos = eventos;
    }

    /** Una persona y lo que el barrido decidió hacer con ella en esta corrida. */
    public record PersonaPorCerrar(UserId participanteId, PlanDeCierre plan) {
    }

    /** Lo que {@code habits} y {@code rocks} reportaron para el rango del plan. */
    public record ConteosDelRango(List<ConteoDelDia> habitos, List<ConteoDelDia> objetivos) {
    }

    public record ResultadoDeParticipante(int diasGuardados, int semanasCerradas) {
    }

    @Transactional
    public ResultadoDeParticipante cerrar(PersonaPorCerrar persona, ConteosDelRango conteos, Instant ahora) {
        PlanDeCierre plan = persona.plan();
        Map<LocalDate, CumplimientoDelDia> dias = plan.diasMedidos(conteos.habitos(), conteos.objetivos());
        int guardados = dias.isEmpty() ? 0 : guardarDiasPort.guardar(persona.participanteId(), dias.values(), ahora);
        MedicionDeLaPersona medicion = new MedicionDeLaPersona(plan.calendario(), dias, plan.hoyLocal());
        int cerradas = 0;
        for (LocalDate semanaHasta : plan.semanasPorCerrar()) {
            if (cerrarSemana(persona.participanteId(), medicion.fotoDe(semanaHasta, ahora), plan.hoyLocal())) {
                cerradas++;
            }
        }
        return new ResultadoDeParticipante(guardados, cerradas);
    }

    /**
     * La foto se guarda una sola vez (append-only): si otra corrida la cerró antes, no se avisa de nuevo.
     * El aviso sale solo si la semana tuvo días medidos y todavía es el fin de semana del cierre.
     */
    private boolean cerrarSemana(UserId participante, FotoSemanal foto, LocalDate hoyLocal) {
        if (!semanasPort.registrar(participante, foto)) {
            return false;
        }
        if (foto.tuvoDiasMedidos() && CierreSemanal.correspondeAvisar(foto.semanaHasta(), hoyLocal)) {
            eventos.publishEvent(new SemanaDelSemaforoCerradaEvent(
                    SemanaDelSemaforoCerradaEvent.claveDe(participante.value(), foto.semanaHasta()),
                    participante.value(), foto.semanaDesde(), foto.semanaHasta(), foto.porcentaje(), foto.color(),
                    foto.diasConDatos(), foto.cerradaEn()));
        }
        return true;
    }
}

package com.renaser.os.habits.application.services;

import com.renaser.os.habits.application.ports.in.preferencia.PlanDeLaSemanaSinRedUseCase;
import com.renaser.os.habits.application.ports.in.preferencia.PlanDeLaSemanaUseCase;
import com.renaser.os.habits.domain.model.preferencia.CambioDeFecha;
import com.renaser.os.habits.application.ports.in.preferencia.RepetirSemanaUseCase;
import com.renaser.os.habits.application.ports.in.preferencia.RepetirSemanaUseCase.ResultadoDeRepeticion;
import com.renaser.os.habits.application.ports.out.habito.LoadHabitoPort;
import com.renaser.os.habits.application.ports.out.participante.ConsultarProgresoParticipanteHabitsPort;
import com.renaser.os.habits.application.ports.out.sinred.UltimaEdicionDelPlanDeHabitosPort;
import com.renaser.os.habits.domain.model.habito.HabitoId;
import com.renaser.os.habits.domain.model.preferencia.SemanaCalendaria;
import com.renaser.os.shared.application.EntregaSinRed;
import com.renaser.os.shared.application.EntregaSinRed.Aplicado;
import com.renaser.os.shared.application.EntregaSinRed.Envio;
import com.renaser.os.shared.application.ports.out.EnviosSinRedPort;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.CodigoNoAplicado;
import com.renaser.os.shared.domain.HechoSinRed;
import com.renaser.os.shared.domain.NoAplicadoSinRedException;
import com.renaser.os.shared.domain.RevisionDeLaHora;
import com.renaser.os.shared.domain.UserId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * «Solo esta semana», repetir la semana y deshacerla, hechos sin red (spec 048, fase 3, FR-019). Las reglas de {@code PlanDeLaSemanaService} y
 * {@code RepeticionDeSemanaService} corridas con la hora real; aquí solo se decide si se aplica: si alguna fecha propia de esa semana cambió en el
 * servidor DESPUÉS de lo que hizo la persona, gana el servidor ({@code CAMBIO_MAS_NUEVO_EN_EL_SERVIDOR}, D13); un hábito que ya no existe es
 * {@code RECURSO_YA_NO_EXISTE}, salvo al volver al plan de siempre (un {@code DELETE} se aplica sin más).
 *
 * <p>Repetir y deshacer tocan el plan de TODOS los hábitos de esa semana; si un reintento ya se aplicó, responde que no hubo nada nuevo que copiar.
 */
@Service
class PlanDeLaSemanaSinRedService implements PlanDeLaSemanaSinRedUseCase {

    private static final String TIPO = "HABITO_PLAN_SEMANA";

    private final ConsultarProgresoParticipanteHabitsPort progresoPort;
    private final LoadHabitoPort loadHabitoPort;
    private final UltimaEdicionDelPlanDeHabitosPort ultima;
    private final PlanDeLaSemanaUseCase planDeLaSemana;
    private final RepetirSemanaUseCase repeticion;
    private final EntregaSinRed entrega;
    private final Clock clock;

    PlanDeLaSemanaSinRedService(ConsultarProgresoParticipanteHabitsPort progresoPort, LoadHabitoPort loadHabitoPort,
                                UltimaEdicionDelPlanDeHabitosPort ultima, PlanDeLaSemanaUseCase planDeLaSemana,
                                RepetirSemanaUseCase repeticion, EnviosSinRedPort envios, Clock clock) {
        this.progresoPort = progresoPort;
        this.loadHabitoPort = loadHabitoPort;
        this.ultima = ultima;
        this.planDeLaSemana = planDeLaSemana;
        this.repeticion = repeticion;
        this.entrega = new EntregaSinRed(envios, clock);
        this.clock = clock;
    }

    @Override
    @Transactional(noRollbackFor = NoAplicadoSinRedException.class)
    public void guardar(UserId actor, HabitoId habito, SemanaCalendaria semana, List<CambioDeFecha> cambios, HechoSinRed sinRed) {
        AccesoAlPlanDeHabitos.exigir(progresoPort, actor);
        Envio envio = new Envio(actor, sinRed, TIPO, "semana:" + habito.value() + ":" + semana.lunes());
        entrega.entregar(envio, () -> {
            RevisionDeLaHora hora = RevisionDeLaHora.de(sinRed, clock.now());
            exigirElHabito(habito);
            exigirSinCambioPosterior(ultima.delasFechas(actor, habito, semana.lunes(), semana.domingo()), hora);
            planDeLaSemana.guardar(actor, habito, semana, cambios, hora.referencia());
            return Aplicado.de(Boolean.TRUE);
        }, previo -> Boolean.TRUE);
    }

    @Override
    @Transactional(noRollbackFor = NoAplicadoSinRedException.class)
    public void volverAlPlanDeSiempre(UserId actor, HabitoId habito, SemanaCalendaria semana, HechoSinRed sinRed) {
        AccesoAlPlanDeHabitos.exigir(progresoPort, actor);
        Envio envio = new Envio(actor, sinRed, TIPO, "semana:" + habito.value() + ":" + semana.lunes());
        entrega.entregar(envio, () -> {
            RevisionDeLaHora hora = RevisionDeLaHora.de(sinRed, clock.now());
            if (loadHabitoPort.byId(habito).isEmpty()) {
                return Aplicado.de(Boolean.TRUE);
            }
            exigirSinCambioPosterior(ultima.delasFechas(actor, habito, semana.lunes(), semana.domingo()), hora);
            planDeLaSemana.volverAlPlanDeSiempre(actor, habito, semana, hora.referencia());
            return Aplicado.de(Boolean.TRUE);
        }, previo -> Boolean.TRUE);
    }

    @Override
    @Transactional(noRollbackFor = NoAplicadoSinRedException.class)
    public ResultadoDeRepeticion repetir(UserId actor, SemanaCalendaria modelo, SemanaCalendaria destino, boolean reemplazar,
                                         HechoSinRed sinRed) {
        AccesoAlPlanDeHabitos.exigir(progresoPort, actor);
        Envio envio = new Envio(actor, sinRed, TIPO, "repetir-semana:" + destino.lunes());
        return entrega.entregar(envio, () -> {
            RevisionDeLaHora hora = RevisionDeLaHora.de(sinRed, clock.now());
            exigirSinCambioPosterior(ultima.delasFechas(actor, null, destino.lunes(), destino.domingo()), hora);
            return Aplicado.de(repeticion.repetir(actor, modelo, destino, reemplazar, hora.referencia()));
        }, previo -> new ResultadoDeRepeticion(destino, 0, 0, 0, List.of()));
    }

    @Override
    @Transactional(noRollbackFor = NoAplicadoSinRedException.class)
    public int deshacerSemana(UserId actor, SemanaCalendaria semana, HechoSinRed sinRed) {
        AccesoAlPlanDeHabitos.exigir(progresoPort, actor);
        Envio envio = new Envio(actor, sinRed, TIPO, "deshacer-semana:" + semana.lunes());
        return entrega.entregar(envio, () -> {
            RevisionDeLaHora hora = RevisionDeLaHora.de(sinRed, clock.now());
            exigirSinCambioPosterior(ultima.delasFechas(actor, null, semana.lunes(), semana.domingo()), hora);
            return Aplicado.de(repeticion.deshacerSemana(actor, semana, hora.referencia()));
        }, previo -> 0);
    }

    private void exigirElHabito(HabitoId habito) {
        if (loadHabitoPort.byId(habito).isEmpty()) {
            throw new NoAplicadoSinRedException(CodigoNoAplicado.RECURSO_YA_NO_EXISTE);
        }
    }

    private static void exigirSinCambioPosterior(Optional<Instant> actualizadoEn, RevisionDeLaHora hora) {
        if (actualizadoEn.filter(hora::hayCambioMasNuevo).isPresent()) {
            throw new NoAplicadoSinRedException(CodigoNoAplicado.CAMBIO_MAS_NUEVO_EN_EL_SERVIDOR);
        }
    }
}

package com.renaser.os.rag.infrastructure.adapter.out.enfoque;

import com.renaser.os.habits.api.EnfoqueDiarioPort;
import com.renaser.os.rag.application.ports.out.enfoque.EnfoqueDiarioDelAprendizPort;
import com.renaser.os.shared.domain.UserId;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * Implementa {@link EnfoqueDiarioDelAprendizPort} delegando en el contrato publico de
 * {@code habits} (D-41). Es una traduccion y nada mas: que dia es hoy, si el audio vence, si el
 * Santuario o la racha se pueden iniciar lo resuelve {@code habits}. Mismo patron que
 * {@code GestionarPlanDeHabitosAdapter}.
 */
@Component
class EnfoqueDiarioAdapter implements EnfoqueDiarioDelAprendizPort {

    private final EnfoqueDiarioPort enfoqueDiario;

    EnfoqueDiarioAdapter(EnfoqueDiarioPort enfoqueDiario) {
        this.enfoqueDiario = enfoqueDiario;
    }

    @Override
    public EspirituDeHoy espirituDeHoy(UserId participanteId) {
        EnfoqueDiarioPort.EspirituDeHoy espiritu = enfoqueDiario.espirituDeHoy(participanteId);
        EnfoqueDiarioPort.AudioDeHoy audio = espiritu.audio();
        return new EspirituDeHoy(espiritu.zona(), espiritu.hoy(), espiritu.horaDesbloqueo(), espiritu.horaLimite(),
                EstadoEspiritu.valueOf(espiritu.estado().name()),
                audio == null ? null : new AudioDeHoy(audio.dia(), audio.titulo(), audio.fechaLimite(),
                        audio.entregadoEn()),
                espiritu.puntosPastillaRenacer());
    }

    @Override
    public boolean entregarResumenEspiritu(UserId actorId, int diaAudio, String resumen) {
        return enfoqueDiario.entregarResumenEspiritu(actorId, diaAudio, resumen);
    }

    @Override
    public List<SantuarioDeHoy> santuariosDeHoy(UserId participanteId) {
        return enfoqueDiario.santuariosDeHoy(participanteId).stream()
                .map(santuario -> new SantuarioDeHoy(santuario.zona(), santuario.registroId(), santuario.titulo(),
                        santuario.iniciable(), santuario.iniciableDesde()))
                .toList();
    }

    @Override
    public void iniciarSantuario(UserId actorId, UUID registroId) {
        enfoqueDiario.iniciarSantuario(actorId, registroId);
    }

    @Override
    public DiaSinCelularDeHoy diaSinCelularDeHoy(UserId participanteId) {
        EnfoqueDiarioPort.DiaSinCelularDeHoy dia = enfoqueDiario.diaSinCelularDeHoy(participanteId);
        return new DiaSinCelularDeHoy(dia.zona(), dia.registroId(), dia.titulo(), dia.iniciable(),
                dia.rachaEnCursoDesde(), dia.rachaEnCursoHoras(), dia.metasValidas());
    }

    @Override
    public void iniciarDiaSinCelular(UserId actorId, UUID registroId, int horasObjetivo) {
        enfoqueDiario.iniciarDiaSinCelular(actorId, registroId, horasObjetivo);
    }
}

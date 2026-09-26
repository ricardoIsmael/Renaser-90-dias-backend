package com.renaser.os.rag.infrastructure.adapter.out.habits;

import com.renaser.os.habits.api.AudioterapiaDelAprendizPort;
import com.renaser.os.rag.application.ports.out.audioterapia.AudioterapiaSemanalPort;
import com.renaser.os.shared.domain.UserId;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Implementa {@link AudioterapiaSemanalPort} delegando en el contrato publico de {@code habits}
 * (D-41, D-171). Es una traduccion y nada mas: que audio toca, que registro es el de hoy y como se
 * cierra lo resuelve {@code habits}.
 */
@Component
class AudioterapiaSemanalAdapter implements AudioterapiaSemanalPort {

    private final AudioterapiaDelAprendizPort audioterapia;

    AudioterapiaSemanalAdapter(AudioterapiaDelAprendizPort audioterapia) {
        this.audioterapia = audioterapia;
    }

    @Override
    public AudioterapiaDeHoy deHoyDe(UserId participanteId) {
        AudioterapiaDelAprendizPort.AudioterapiaDeHoy deHoy = audioterapia.deHoyDe(participanteId);
        return new AudioterapiaDeHoy(deHoy.semana(), deHoy.titulo(), deHoy.diaSiguienteCambio(), deHoy.registroId(),
                deHoy.estadoRegistro());
    }

    @Override
    public int entregarRespuestas(UserId actorId, UUID registroId, String texto) {
        return audioterapia.entregarRespuestas(actorId, registroId, texto);
    }
}

package com.renaser.os.habits.application.services;

import com.renaser.os.evidence.api.TipoEvidencia;
import com.renaser.os.habits.api.AudioterapiaDelAprendizPort;
import com.renaser.os.habits.application.ports.in.audioterapia.ConsultarAudioterapiaSemanalUseCase;
import com.renaser.os.habits.application.ports.in.audioterapia.ConsultarAudioterapiaSemanalUseCase.AudioDeLaSemana;
import com.renaser.os.habits.application.ports.in.registro.CompletarRegistroUseCase;
import com.renaser.os.habits.application.ports.in.registro.CompletarRegistroUseCase.CompletarRegistroCommand;
import com.renaser.os.habits.application.ports.in.registro.ConsultarTracksDelDiaConCatalogoUseCase;
import com.renaser.os.habits.application.ports.in.registro.ConsultarTracksDelDiaConCatalogoUseCase.TrackDelDiaConCatalogo;
import com.renaser.os.habits.application.ports.in.registro.SubirEvidenciaRegistroUseCase;
import com.renaser.os.habits.application.ports.in.registro.SubirEvidenciaRegistroUseCase.SubirEvidenciaRegistroCommand;
import com.renaser.os.habits.domain.model.registro.EstadoRegistro;
import com.renaser.os.habits.domain.model.registro.RegistroHabito;
import com.renaser.os.habits.domain.model.registro.RegistroHabitoId;
import com.renaser.os.shared.domain.UserId;
import org.springframework.stereotype.Service;

import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

/**
 * Implementa {@link AudioterapiaDelAprendizPort} (D-171) con casos de uso que ya existen: que audio
 * toca lo resuelve {@link ConsultarAudioterapiaSemanalUseCase}, que registro es el de hoy lo
 * resuelve la proyeccion del dia (en la zona de la persona) y cerrar lo hacen los mismos dos pasos
 * que la app: subir la evidencia de TEXTO y completar.
 *
 * <p><b>Sin {@code @Transactional} propio, a proposito.</b> Cada paso abre su transaccion corta.
 * Envolverlos en una sola haria que la lectura del registro de hoy (sin cerrojo) quedara en el mismo
 * contexto de persistencia que el {@code completar} con cerrojo, y Hibernate no rehidrata una
 * entidad ya gestionada: la guarda de estado decidiria sobre una lectura vieja. Es el bug que
 * documenta {@code ClaseDiariaHabitoService} (hallazgo del 2026-09-21). El precio es el mismo que
 * paga la app: si completar falla despues de subir, la evidencia queda subida sin completar. Por eso
 * el estado se valida ANTES de subir nada.
 */
@Service
public class AudioterapiaDelAprendizService implements AudioterapiaDelAprendizPort {

    private final ConsultarAudioterapiaSemanalUseCase audioterapiaUseCase;
    private final ConsultarTracksDelDiaConCatalogoUseCase tracksUseCase;
    private final SubirEvidenciaRegistroUseCase subirEvidenciaUseCase;
    private final CompletarRegistroUseCase completarUseCase;

    public AudioterapiaDelAprendizService(ConsultarAudioterapiaSemanalUseCase audioterapiaUseCase,
                                          ConsultarTracksDelDiaConCatalogoUseCase tracksUseCase,
                                          SubirEvidenciaRegistroUseCase subirEvidenciaUseCase,
                                          CompletarRegistroUseCase completarUseCase) {
        this.audioterapiaUseCase = audioterapiaUseCase;
        this.tracksUseCase = tracksUseCase;
        this.subirEvidenciaUseCase = subirEvidenciaUseCase;
        this.completarUseCase = completarUseCase;
    }

    @Override
    public AudioterapiaDeHoy deHoyDe(UserId participanteId) {
        Optional<AudioDeLaSemana> audio = audioDeLaSemana(participanteId);
        Optional<RegistroHabito> registro = registroDeHoy(participanteId);
        return new AudioterapiaDeHoy(audio.map(AudioDeLaSemana::semanaActual).orElse(null),
                audio.map(AudioDeLaSemana::titulo).orElse(null),
                audio.map(AudioDeLaSemana::diaSiguienteCambio).orElse(null),
                registro.map(r -> r.id().value()).orElse(null),
                registro.map(r -> r.estado().name()).orElse(null));
    }

    @Override
    public int entregarRespuestas(UserId actorId, UUID registroId, String texto) {
        if (audioDeLaSemana(actorId).isEmpty()) {
            throw new NoSuchElementException("Esta semana no hay audioterapia cargada");
        }
        RegistroHabito registro = registroDeHoy(actorId)
                .filter(deHoy -> deHoy.id().value().equals(registroId))
                .orElseThrow(() -> new NoSuchElementException("Ese registro no es la Audioterapia de hoy"));
        if (registro.estado() != EstadoRegistro.PENDIENTE && registro.estado() != EstadoRegistro.EN_CURSO) {
            throw new IllegalStateException("La Audioterapia de hoy ya esta " + registro.estado());
        }
        RegistroHabitoId id = registro.id();
        subirEvidenciaUseCase.subir(new SubirEvidenciaRegistroCommand(actorId, id, TipoEvidencia.TEXTO, null, null,
                texto, null, null, null));
        return completarUseCase.completar(new CompletarRegistroCommand(actorId, id, null, null)).puntosOtorgados();
    }

    private Optional<AudioDeLaSemana> audioDeLaSemana(UserId participanteId) {
        return audioterapiaUseCase.consultar(participanteId) instanceof AudioDeLaSemana audio
                ? Optional.of(audio) : Optional.empty();
    }

    private Optional<RegistroHabito> registroDeHoy(UserId participanteId) {
        return tracksUseCase.consultarHoyDe(participanteId).stream()
                .filter(track -> CLAVE_SISTEMA_AUDIOTERAPIA.equals(track.claveSistema()))
                .map(TrackDelDiaConCatalogo::registro)
                .findFirst();
    }
}

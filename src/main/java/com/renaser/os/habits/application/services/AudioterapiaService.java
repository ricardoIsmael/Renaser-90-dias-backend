package com.renaser.os.habits.application.services;

import com.renaser.os.habits.application.ports.in.audioterapia.ConsultarAudioterapiaSemanalUseCase;
import com.renaser.os.habits.application.ports.out.audioterapia.AudioterapiaCatalogPort;
import com.renaser.os.habits.application.ports.out.audioterapia.AudioterapiaCatalogPort.Audioterapia;
import com.renaser.os.habits.application.ports.out.habito.LoadHabitoPort;
import com.renaser.os.habits.application.ports.out.horario.LoadHorarioHabitoPort;
import com.renaser.os.habits.application.ports.out.participante.ConsultarProgresoParticipanteHabitsPort;
import com.renaser.os.habits.application.ports.out.participante.ConsultarProgresoParticipanteHabitsPort.ProgresoParticipanteHabits;
import com.renaser.os.habits.application.ports.out.participante.ConsultarProgresoParticipanteHabitsPort.RolParticipante;
import com.renaser.os.habits.domain.model.habito.Habito;
import com.renaser.os.habits.domain.model.horario.HorarioHabito;
import com.renaser.os.shared.application.ports.out.AlmacenamientoPort;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.List;
import java.util.NoSuchElementException;

/**
 * Resolver de solo lectura: dado el día de programa del aprendiz, ubica qué audioterapia
 * semanal le corresponde. A diferencia de "Espíritu" (registros_espiritu, máquina de estados),
 * acá no hace falta estado propio — el hábito "AUDIOTERAPIA SEMANAL" (JOURNALING) ya se completa
 * por el camino genérico de {@code RegistroService}; esto solo le dice al aprendiz qué audio
 * escuchar antes de completar.
 *
 * <p>El día de inicio NUNCA se hardcodea: se lee de {@code horarios_habito.dia_inicio} del
 * propio hábito (ya editable desde el panel admin, {@code HorarioHabitoAdminController}). La
 * duración de cada semana tampoco: viene de {@code audioterapias.duracion_dias}, editable por
 * semana vía {@link AudioterapiaAdminService} — el negocio confirmó (2026-08-28) que este número
 * cambia seguido y no debía quedar fijo en código.
 */
@Service
public class AudioterapiaService implements ConsultarAudioterapiaSemanalUseCase {

    /** Mismo TTL que la portada de curso (CatalogoAcademyService.TTL_PORTADA) — sin motivo para diferir. */
    private static final Duration TTL_AUDIO = Duration.ofHours(1);

    static final String CLAVE_SISTEMA_AUDIOTERAPIA = "AUDIO_THERAPY_WEEKLY";

    private final LoadHabitoPort loadHabitoPort;
    private final LoadHorarioHabitoPort loadHorarioPort;
    private final AudioterapiaCatalogPort catalogoPort;
    private final ConsultarProgresoParticipanteHabitsPort progresoPort;
    private final AlmacenamientoPort almacenamientoPort;

    public AudioterapiaService(LoadHabitoPort loadHabitoPort, LoadHorarioHabitoPort loadHorarioPort,
                                AudioterapiaCatalogPort catalogoPort,
                                ConsultarProgresoParticipanteHabitsPort progresoPort,
                                AlmacenamientoPort almacenamientoPort) {
        this.loadHabitoPort = loadHabitoPort;
        this.loadHorarioPort = loadHorarioPort;
        this.catalogoPort = catalogoPort;
        this.progresoPort = progresoPort;
        this.almacenamientoPort = almacenamientoPort;
    }

    @Override
    @Transactional(readOnly = true)
    public EstadoAudioterapia consultar(UserId actorId) {
        ProgresoParticipanteHabits progreso = requireParticipanteHabilitado(actorId);
        int diaInicio = diaInicioDelHabito();

        if (progreso.diaPrograma() < diaInicio) {
            return new EsperandoContenido();
        }

        List<Audioterapia> catalogo = catalogoPort.todasOrdenadas();
        int diaAcumulado = diaInicio;
        for (Audioterapia audioterapia : catalogo) {
            int diaFinVentana = diaAcumulado + audioterapia.duracionDias() - 1;
            if (progreso.diaPrograma() <= diaFinVentana) {
                int diaSiguienteCambio = diaFinVentana + 1;
                String url = firmarAudio(audioterapia.rutaStorage());
                return new AudioDeLaSemana(audioterapia.semana(), audioterapia.titulo(), url,
                        diaSiguienteCambio);
            }
            diaAcumulado = diaFinVentana + 1;
        }
        // dia de programa mas alla de la ultima semana cargada: el aprendiz queda al dia,
        // esperando contenido -- mismo criterio que AudioCatalogPort/EspirituService.
        return new EsperandoContenido();
    }

    private int diaInicioDelHabito() {
        Habito habito = loadHabitoPort.porClaveSistema(CLAVE_SISTEMA_AUDIOTERAPIA)
                .orElseThrow(() -> new NoSuchElementException(
                        "No existe en el catalogo un habito con claveSistema=" + CLAVE_SISTEMA_AUDIOTERAPIA));
        List<HorarioHabito> horarios = loadHorarioPort.porHabito(habito.id());
        return horarios.stream().findFirst()
                .orElseThrow(() -> new NoSuchElementException("AUDIOTERAPIA SEMANAL no tiene horario configurado"))
                .diaInicio();
    }

    private String firmarAudio(String rutaStorage) {
        if (rutaStorage == null || rutaStorage.isBlank()) {
            return null;
        }
        if (rutaStorage.matches("(?i)^https?://.*")) {
            return rutaStorage;
        }
        return almacenamientoPort.firmarLectura(rutaStorage, TTL_AUDIO).toString();
    }

    /** Mismo criterio que EspirituService.requireParticipanteHabilitado: SUSPENDIDO/no-TRAINEE -> 403. */
    private ProgresoParticipanteHabits requireParticipanteHabilitado(UserId actorId) {
        ProgresoParticipanteHabits progreso = progresoPort.deParticipante(actorId)
                .orElseThrow(() -> new NoSuchElementException("Participante no encontrado: " + actorId));
        if (progreso.suspendido()) {
            throw new NotAuthorizedException("Cuenta suspendida");
        }
        /* E-169: la puerta no es el ROL, es tener el programa andando. `TRACK_PROGRAM_AS_STAFF`
           y `POST /api/v1/mentor/activate-tracking` existen para que el staff curse los 90 dias;
           preguntar solo por el rol dejaba la inscripcion construida y el uso prohibido.

           Se conserva la rama del rol en vez de reducirlo a `!programaActivado`: un TRAINEE recien
           aprobado tiene `programa_activado_en` en null hasta que termina primer login + Ficha +
           Terminos, y el cambio corto lo habria dejado fuera de su propio programa. Asi el cambio
           es ESTRICTAMENTE aditivo: nadie que hoy pase, deja de pasar. */
        if (progreso.rol() != RolParticipante.TRAINEE && !progreso.programaActivado()) {
            throw new NotAuthorizedException("Audioterapia semanal es exclusiva de aprendices");
        }
        return progreso;
    }
}

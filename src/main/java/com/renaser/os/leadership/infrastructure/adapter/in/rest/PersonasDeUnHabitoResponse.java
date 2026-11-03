package com.renaser.os.leadership.infrastructure.adapter.in.rest;

import com.renaser.os.leadership.application.ports.in.ConsultarHabitosDeTodosUseCase.FilaDePersona;
import com.renaser.os.leadership.application.ports.in.ConsultarHabitosDeTodosUseCase.PersonasDeUnHabito;
import com.renaser.os.leadership.infrastructure.adapter.in.rest.HabitosDeTodosResponse.HabitoResponse;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

/**
 * Las personas de UN hábito en un día (spec 029): cada alumno con su estado, para que la app los agrupe en «Sin cumplir / Pendientes», «Cumplieron» y «No
 * les tocaba o en pausa». {@code status}: CUMPLIDO, PENDIENTE, SIN_CUMPLIR o SIN_DATOS (con {@code reason}: nunca un incumplimiento). Nunca el correo.
 */
public record PersonasDeUnHabitoResponse(LocalDate date, boolean isToday, HabitoResponse habit, List<PersonaResponse> people, int page,
                                          int size, long total) {

    public static PersonasDeUnHabitoResponse from(PersonasDeUnHabito personas) {
        return new PersonasDeUnHabitoResponse(personas.fecha(), personas.esHoy(), HabitoResponse.fromDelDia(personas.habito()),
                personas.personas().stream().map(PersonaResponse::from).toList(), personas.pagina(), personas.tamano(), personas.total());
    }

    public record PersonaResponse(UUID personId, String name, String photoPath, UUID groupId, String groupName, String status,
                                   String reason, LocalTime plannedTime, String doneAt, UUID evidenceId) {

        static PersonaResponse from(FilaDePersona fila) {
            return new PersonaResponse(fila.personaId(), fila.nombre(), fila.foto(), fila.grupoId(), fila.grupo(), fila.estado(),
                    fila.motivo(), fila.horaPlanificada(), fila.cumplioA(), fila.evidenciaId());
        }
    }
}

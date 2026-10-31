package com.renaser.os.leadership.infrastructure.adapter.in.rest;

import com.renaser.os.leadership.application.ports.in.ConsultarSemanaDePersonaUseCase.DiaDeLaFicha;
import com.renaser.os.leadership.application.ports.in.ConsultarSemanaDePersonaUseCase.MentorDelGrupo;
import com.renaser.os.leadership.application.ports.in.ConsultarSemanaDePersonaUseCase.SemanaDeLaPersona;
import com.renaser.os.leadership.domain.model.cumplimiento.ProgramacionDelDia;
import com.renaser.os.mentoring.api.ObligacionDelDia;
import com.renaser.os.mentoring.api.ResumenSemana;
import com.renaser.os.rocks.api.AccionesDelDiaFinder.AccionDelDia;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * La semana de una persona para el líder, en el JSON de {@code specs/005-lider-cumplimiento-por-persona/contracts}.
 * Las obligaciones viajan con la forma de {@code …/weekly-progress} (la ficha del mentor y la de administración),
 * más lo que la respalda: la respuesta escrita, las evidencias y la publicación del Muro.
 *
 * @param semanaEnCurso true si la ventana todavía no es una semana cerrada (es la vigente, o una que aún corre)
 * @param grupoId       el grupo con que se la mira; null si la persona no está en ninguno (spec 021)
 * @param mentor        null si no hay a quién avisar (grupo sin mentor, o la persona es el propio mentor)
 * @param puedeAvisarAlMentor spec 021: true si hay mentor a quien avisarle (es {@code mentor != null}); false si la
 *                      persona no tiene grupo, está en la recepción o su grupo no tiene mentor. Aditivo y opcional: una
 *                      app anterior lo ignora
 * @param programacion  spec 018: el «¿Programó?» de HOY; null si no hay nada que programar. Campo aditivo y opcional:
 *                      una app anterior lo ignora y la nueva, contra un servidor anterior, no dibuja la tarjeta
 */
public record SemanaDeLaPersonaResponse(UUID grupoId, UUID personaId, String nombre, String avatarUrl, String rol,
                                        LocalDate desde, LocalDate hasta, String zona, boolean semanaEnCurso,
                                        boolean cerrada, LocalDate primeraSemana, MentorResponse mentor,
                                        boolean puedeAvisarAlMentor, ResumenSemana resumen, String color,
                                        String etiqueta, BigDecimal porcentaje, List<DiaResponse> dias,
                                        ProgramacionResponse programacion) {

    public static SemanaDeLaPersonaResponse from(SemanaDeLaPersona semana) {
        return new SemanaDeLaPersonaResponse(semana.grupoId(), semana.personaId(), semana.nombre(),
                semana.avatarUrl(), semana.rol() == null ? null : semana.rol().name(), semana.desde(), semana.hasta(),
                semana.zona(), !semana.cerrada(), semana.cerrada(), semana.primeraSemana(),
                MentorResponse.from(semana.mentor()), semana.mentor() != null, semana.resumen(), semana.color().name(),
                semana.color().etiqueta(), semana.porcentaje(),
                semana.dias().stream().map(DiaResponse::from).toList(), ProgramacionResponse.from(semana.programacion()));
    }

    /**
     * @param programo el «Sí» / «No» que decide el servidor (la app no lo recalcula): al menos la mitad de los hábitos
     *                 con hora propia y todas las acciones de hoy agendadas
     */
    public record ProgramacionResponse(int habitosConHora, int habitosTotal, int accionesConHora, int accionesTotal,
                                       boolean programo) {

        static ProgramacionResponse from(ProgramacionDelDia programacion) {
            return programacion == null ? null
                    : new ProgramacionResponse(programacion.habitosConHora(), programacion.habitosTotal(),
                            programacion.accionesConHora(), programacion.accionesTotal(), programacion.programo());
        }
    }

    public record MentorResponse(UUID id, String nombre) {

        static MentorResponse from(MentorDelGrupo mentor) {
            return mentor == null ? null : new MentorResponse(mentor.id(), mentor.nombre());
        }
    }

    /** @param motivo por qué el día no tiene nada que medir (en palabras, nunca como incumplimiento); null si se midió */
    public record DiaResponse(LocalDate fecha, Integer diaDePrograma, String motivo,
                              List<ObligacionDelDia> obligaciones, List<AccionResponse> acciones) {

        static DiaResponse from(DiaDeLaFicha dia) {
            return new DiaResponse(dia.fecha(), dia.diaDePrograma(), dia.motivo(), dia.obligaciones(),
                    dia.acciones().stream().map(AccionResponse::from).toList());
        }
    }

    /** @param estado COMPLETADA o PENDIENTE: una acción se completa solo entregando evidencia */
    public record AccionResponse(UUID id, String titulo, String estado, UUID evidenciaId) {

        static AccionResponse from(AccionDelDia accion) {
            return new AccionResponse(accion.id(), accion.titulo(), accion.completada() ? "COMPLETADA" : "PENDIENTE",
                    accion.evidenciaId());
        }
    }
}

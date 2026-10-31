package com.renaser.os.leadership.application.services;

import com.renaser.os.leadership.application.ports.in.ConsultarSemanaDePersonaUseCase.MentorDelGrupo;
import com.renaser.os.leadership.application.services.PersonasDelPrograma.Mirada;
import com.renaser.os.leadership.domain.model.cumplimiento.ProgramacionDelDia;
import com.renaser.os.leadership.domain.model.cumplimiento.VentanaDeLaFicha;
import com.renaser.os.mentoring.api.SemanaDelAlumno;
import com.renaser.os.mentoring.api.SemanaDeSeguimientoFinder;
import com.renaser.os.points.api.VentanaDelSemaforo;
import com.renaser.os.rocks.api.AccionesDelDiaFinder;
import com.renaser.os.rocks.api.AccionesDelDiaFinder.AccionDelDia;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.UserSummary;
import com.renaser.os.users.api.UserSummaryFinder;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * Las lecturas que arman la ficha de una persona (spec 005): su semana de seguimiento, lo que dice el semáforo de esa
 * ventana, sus acciones del día y el mentor del grupo. Todo por APIs públicas, todo en la misma ventana. La spec 018 suma
 * si la persona programó su día de HOY, que no depende de la ventana.
 */
@Component
class LecturaDeLaFicha {

    private final SemanaDeSeguimientoFinder seguimiento;
    private final SemaforoDeLaPersona semaforo;
    private final AccionesDelDiaFinder acciones;
    private final UserSummaryFinder usuarios;
    private final ProgramacionDeLaPersona programacion;

    LecturaDeLaFicha(SemanaDeSeguimientoFinder seguimiento, SemaforoDeLaPersona semaforo, AccionesDelDiaFinder acciones,
                     UserSummaryFinder usuarios, ProgramacionDeLaPersona programacion) {
        this.seguimiento = seguimiento;
        this.semaforo = semaforo;
        this.acciones = acciones;
        this.usuarios = usuarios;
        this.programacion = programacion;
    }

    /** @param mirada la persona y el grupo con que se la mira, que puede ser ninguno (spec 021) */
    Lectura leer(Mirada mirada, VentanaDeLaFicha ventana) {
        UserId persona = mirada.personaId();
        SemanaDelAlumno semana = seguimiento.semanaDe(persona, mirada.grupoId(), ventana.desde(), ventana.hasta());
        return new Lectura(semana, semaforo.ventanaDe(persona, ventana),
                acciones.accionesEntre(persona, ventana.desde(), ventana.hasta()), mentorDe(mirada),
                programacion.deHoy(mirada.miembro()));
    }

    /**
     * El mentor al que se le avisa. Null cuando no hay: la persona no tiene grupo, está en la recepción o su grupo no
     * tiene mentor. Y null cuando la persona ES el mentor del grupo: no tiene sentido «avisar a su mentor» sobre sí
     * mismo, y el servidor rechazaría esa observación (spec 005, R-7).
     */
    private MentorDelGrupo mentorDe(Mirada mirada) {
        UserId mentor = mirada.mentorId();
        if (mentor == null || mentor.equals(mirada.personaId())) {
            return null;
        }
        String nombre = usuarios.findById(mentor).map(UserSummary::fullName).orElse(null);
        return new MentorDelGrupo(mentor.value(), nombre);
    }

    /**
     * @param semaforo     vacío si el semáforo no la mide
     * @param programacion vacío si no hay nada que programar hoy (programa sin activar, ningún hábito que le toque)
     */
    record Lectura(SemanaDelAlumno semana, Optional<VentanaDelSemaforo> semaforo, List<AccionDelDia> acciones,
                   MentorDelGrupo mentor, Optional<ProgramacionDelDia> programacion) {
    }
}

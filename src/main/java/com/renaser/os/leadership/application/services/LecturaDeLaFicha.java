package com.renaser.os.leadership.application.services;

import com.renaser.os.community.api.AcompanamientoFinder.GrupoAcompanado;
import com.renaser.os.leadership.application.ports.in.ConsultarSemanaDePersonaUseCase.MentorDelGrupo;
import com.renaser.os.leadership.application.services.PersonasDelGrupo.Miembro;
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

    Lectura leer(Miembro miembro, GrupoAcompanado grupo, VentanaDeLaFicha ventana) {
        UserId persona = miembro.perfil().id();
        SemanaDelAlumno semana = seguimiento.semanaDe(persona, grupo.grupoId(), ventana.desde(), ventana.hasta());
        return new Lectura(semana, semaforo.ventanaDe(persona, ventana),
                acciones.accionesEntre(persona, ventana.desde(), ventana.hasta()), mentorDe(grupo, persona),
                programacion.deHoy(miembro));
    }

    /**
     * El mentor al que se le avisa. Null cuando la persona ES el mentor del grupo: no tiene sentido «avisar a su
     * mentor» sobre sí mismo, y el servidor rechazaría esa observación (spec 005, R-7).
     */
    private MentorDelGrupo mentorDe(GrupoAcompanado grupo, UserId persona) {
        if (grupo.mentorId() == null || grupo.mentorId().equals(persona)) {
            return null;
        }
        String nombre = usuarios.findById(grupo.mentorId()).map(UserSummary::fullName).orElse(null);
        return new MentorDelGrupo(grupo.mentorId().value(), nombre);
    }

    /**
     * @param semaforo     vacío si el semáforo no la mide
     * @param programacion vacío si no hay nada que programar hoy (programa sin activar, ningún hábito que le toque)
     */
    record Lectura(SemanaDelAlumno semana, Optional<VentanaDelSemaforo> semaforo, List<AccionDelDia> acciones,
                   MentorDelGrupo mentor, Optional<ProgramacionDelDia> programacion) {
    }
}

package com.renaser.os.rag.application.services.analisis;

import com.renaser.os.shared.domain.Formacion;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.ParticipacionPrograma;
import com.renaser.os.users.api.ParticipacionProgramaFinder;
import com.renaser.os.users.api.ProgramasActivadosFinder;
import com.renaser.os.users.api.ProgramasActivadosFinder.ProgramaActivado;
import com.renaser.os.users.api.UserStatus;
import com.renaser.os.users.api.UserSummary;
import com.renaser.os.users.api.UserSummaryFinder;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;

/**
 * Quién puede tener análisis y con qué calendario (spec 031). Cualquiera que hace su programa —aprendiz, guía, mentor, líder, Administración,
 * Alquimista con programa propio—: no se filtra por rol (FR-030) ni por la duración de su formación (90 o 365 días): el análisis sigue después
 * del último día mientras la persona use la app (031-8 B). No lo tienen las cuentas suspendidas ni cerradas (una cuenta cerrada
 * queda suspendida) ni las de prueba de «ver como»: no hay quien las lea y cada análisis es una llamada a la IA (D-273). Tampoco quien no
 * activó su programa: sin Día 1 no hay semana 1.
 */
@Component
class QuienPuedeTenerAnalisis {

    /**
     * @param formacion la de su participación hoy (spec 022): el análisis es de una sola formación
     * @param primerDia el Día 1 con el ajuste de día ya aplicado: ancla de sus semanas y meses
     */
    record ParticipanteAnalizable(UserId id, Formacion formacion, ZoneId zona, LocalDate primerDia) {
    }

    private final ParticipacionProgramaFinder participaciones;
    private final ProgramasActivadosFinder programas;
    private final UserSummaryFinder usuarios;

    QuienPuedeTenerAnalisis(ParticipacionProgramaFinder participaciones, ProgramasActivadosFinder programas, UserSummaryFinder usuarios) {
        this.participaciones = participaciones;
        this.programas = programas;
        this.usuarios = usuarios;
    }

    Optional<ParticipanteAnalizable> de(UserId persona) {
        Optional<UserSummary> cuenta = usuarios.findById(persona)
                .filter(usuario -> usuario.status() == UserStatus.ACTIVE && !usuario.esCuentaDePrueba());
        Optional<ParticipacionPrograma> participacion = cuenta.flatMap(usuario -> participaciones.deParticipante(persona))
                .filter(p -> p.inscrito() && p.activado() && !p.suspendido());
        if (participacion.isEmpty()) {
            return Optional.empty();
        }
        return programas.de(persona).filter(programa -> programa.primeraFecha() != null)
                .map(programa -> aParticipante(participacion.get(), programa));
    }

    private static ParticipanteAnalizable aParticipante(ParticipacionPrograma participacion, ProgramaActivado programa) {
        return new ParticipanteAnalizable(participacion.participanteId(), participacion.formacion(), programa.zona(),
                programa.primeraFecha());
    }
}

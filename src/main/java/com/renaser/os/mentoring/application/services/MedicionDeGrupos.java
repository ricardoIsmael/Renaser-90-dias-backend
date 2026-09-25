package com.renaser.os.mentoring.application.services;

import com.renaser.os.community.api.AcompanamientoFinder;
import com.renaser.os.community.api.AcompanamientoFinder.GrupoAcompanado;
import com.renaser.os.points.api.SemaforoFinder;
import com.renaser.os.points.api.VentanaDelSemaforo;
import com.renaser.os.shared.domain.UserId;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Quiénes entran en la vista del semáforo de un grupo y qué dice el semáforo de ellos.
 *
 * <p>Lo usan la tabla del mentor, la del administrador, el guard del detalle y el resumen del
 * líder. Que sea UN solo padrón es el punto: si cada vista decidiera por su cuenta a quién contar,
 * el líder podría ver en un grupo a alguien que la tabla del mentor no muestra.
 *
 * <p>Nombre propio del módulo a propósito: dos beans con el mismo nombre simple en módulos
 * distintos chocan (E-32).
 */
@Component
class MedicionDeGrupos {

    private final AcompanamientoFinder acompanamientoFinder;
    private final SemaforoFinder semaforoFinder;

    MedicionDeGrupos(AcompanamientoFinder acompanamientoFinder, SemaforoFinder semaforoFinder) {
        this.acompanamientoFinder = acompanamientoFinder;
        this.semaforoFinder = semaforoFinder;
    }

    /**
     * Los aprendices vigentes del grupo, sin su mentor vigente aunque además curse: su programa
     * personal es independiente del grupo (mismo criterio que {@link AvisosService}). El mentor se
     * averigua acá y no se recibe, para que la tabla del mentor y la del administrador excluyan
     * exactamente a la misma persona.
     */
    List<UserId> aprendicesDe(UUID grupoId, Instant ahora) {
        UserId mentor = acompanamientoFinder.gruposConMentorVigente(ahora).stream()
                .filter(grupo -> grupo.grupoId().equals(grupoId))
                .map(GrupoAcompanado::mentorId)
                .findFirst()
                .orElse(null);
        return sinElMentor(acompanamientoFinder.aprendicesVigentes(grupoId, ahora), mentor);
    }

    /** La misma regla, cuando el grupo ya viene con su mentor (el barrido del líder). */
    List<UserId> aprendicesDe(GrupoAcompanado grupo, Instant ahora) {
        return sinElMentor(acompanamientoFinder.aprendicesVigentes(grupo.grupoId(), ahora), grupo.mentorId());
    }

    /**
     * UNA lectura del semáforo para toda la colección, nunca una por persona.
     *
     * @param semanaHasta null = la ventana vigente; si no, esa semana sábado→viernes
     * @return sin clave = esa persona no se mide
     */
    Map<UserId, VentanaDelSemaforo> ventanasDe(Collection<UserId> aprendices, LocalDate semanaHasta) {
        return semanaHasta == null
                ? semaforoFinder.vigenteDe(aprendices)
                : semaforoFinder.semanaDe(aprendices, semanaHasta);
    }

    private static List<UserId> sinElMentor(List<UserId> aprendices, UserId mentor) {
        return aprendices.stream()
                .filter(aprendiz -> !aprendiz.equals(mentor))
                .distinct()
                .toList();
    }
}

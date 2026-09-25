package com.renaser.os.mentoring.application.services;

import com.renaser.os.community.api.AcompanamientoFinder;
import com.renaser.os.community.api.AcompanamientoFinder.GrupoAcompanado;
import com.renaser.os.points.api.SemaforoFinder;
import com.renaser.os.points.api.VentanaDelSemaforo;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.UserSummary;
import com.renaser.os.users.api.UserSummaryFinder;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Quiénes entran en la vista del semáforo de un grupo y qué dice el semáforo de ellos.
 *
 * <p>Lo usan la tabla del mentor, la del administrador, el guard del detalle, el resumen del líder y
 * el aviso del sábado. Que sea UN solo padrón es el punto: si cada vista decidiera por su cuenta a
 * quién contar, el líder podría ver en un grupo a alguien que la tabla del mentor no muestra.
 *
 * <p><b>Solo cuentas activas</b> (decisión del dueño, 2026-09-25): un aprendiz suspendido, o una
 * cuenta sin aprobar, no aparece en la tabla ni suma en los conteos. Es el mismo criterio del
 * barrido de {@code points}, que solo calcula a cuentas activas: mostrarlo dejaría una fila que ya
 * no se mide. Si la cuenta se reactiva, vuelve a aparecer.
 *
 * <p>Nombre propio del módulo a propósito: dos beans con el mismo nombre simple en módulos
 * distintos chocan (E-32).
 */
@Component
class MedicionDeGrupos {

    private final AcompanamientoFinder acompanamientoFinder;
    private final SemaforoFinder semaforoFinder;
    private final UserSummaryFinder userSummaryFinder;

    MedicionDeGrupos(AcompanamientoFinder acompanamientoFinder, SemaforoFinder semaforoFinder,
                     UserSummaryFinder userSummaryFinder) {
        this.acompanamientoFinder = acompanamientoFinder;
        this.semaforoFinder = semaforoFinder;
        this.userSummaryFinder = userSummaryFinder;
    }

    /**
     * Los aprendices del grupo con su perfil, en el orden del padrón: la tabla muestra el nombre sin
     * volver a pedirlo. Sin su mentor vigente aunque además curse: su programa personal es
     * independiente del grupo (mismo criterio que {@link AvisosService}). El mentor se averigua acá y
     * no se recibe, para que la tabla del mentor y la del administrador excluyan a la misma persona.
     */
    Map<UserId, UserSummary> padronDe(UUID grupoId, Instant ahora) {
        UserId mentor = acompanamientoFinder.gruposConMentorVigente(ahora).stream()
                .filter(grupo -> grupo.grupoId().equals(grupoId))
                .map(GrupoAcompanado::mentorId)
                .findFirst()
                .orElse(null);
        return conCuentaActiva(sinElMentor(acompanamientoFinder.aprendicesVigentes(grupoId, ahora), mentor));
    }

    List<UserId> aprendicesDe(UUID grupoId, Instant ahora) {
        return List.copyOf(padronDe(grupoId, ahora).keySet());
    }

    /** La misma regla, cuando el grupo ya viene con su mentor (el aviso del sábado). */
    List<UserId> aprendicesDe(GrupoAcompanado grupo, Instant ahora) {
        return List.copyOf(conCuentaActiva(vigentesSinElMentor(grupo, ahora)).keySet());
    }

    /**
     * El padrón de varios grupos con UNA sola lectura de cuentas para todos (el resumen del líder
     * recorre todos los grupos). Un aprendiz de dos grupos (D-139) queda en los dos.
     */
    Map<UUID, List<UserId>> aprendicesPorGrupo(List<GrupoAcompanado> grupos, Instant ahora) {
        Map<UUID, List<UserId>> vigentes = new LinkedHashMap<>();
        grupos.forEach(grupo -> vigentes.put(grupo.grupoId(), vigentesSinElMentor(grupo, ahora)));
        Map<UserId, UserSummary> activos =
                conCuentaActiva(vigentes.values().stream().flatMap(List::stream).distinct().toList());
        Map<UUID, List<UserId>> porGrupo = new LinkedHashMap<>();
        vigentes.forEach((grupoId, aprendices) ->
                porGrupo.put(grupoId, aprendices.stream().filter(activos::containsKey).toList()));
        return porGrupo;
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

    private List<UserId> vigentesSinElMentor(GrupoAcompanado grupo, Instant ahora) {
        return sinElMentor(acompanamientoFinder.aprendicesVigentes(grupo.grupoId(), ahora), grupo.mentorId());
    }

    /** Una sola lectura de perfiles; conserva el orden de entrada. */
    private Map<UserId, UserSummary> conCuentaActiva(List<UserId> aprendices) {
        if (aprendices.isEmpty()) {
            return Map.of();
        }
        Map<UserId, UserSummary> perfiles = userSummaryFinder.findByIds(aprendices);
        Map<UserId, UserSummary> activos = new LinkedHashMap<>();
        for (UserId aprendiz : aprendices) {
            UserSummary perfil = perfiles.get(aprendiz);
            if (perfil != null && perfil.status().allowsAccess()) {
                activos.put(aprendiz, perfil);
            }
        }
        return activos;
    }

    private static List<UserId> sinElMentor(List<UserId> aprendices, UserId mentor) {
        return aprendices.stream()
                .filter(aprendiz -> !aprendiz.equals(mentor))
                .distinct()
                .toList();
    }
}

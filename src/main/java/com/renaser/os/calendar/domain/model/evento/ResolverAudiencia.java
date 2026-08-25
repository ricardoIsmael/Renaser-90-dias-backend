package com.renaser.os.calendar.domain.model.evento;

import java.util.UUID;

/**
 * Puerto directo de {@code canViewEvent} (audience.ts, repo viejo). DOMINIO PURO: no
 * consulta nada, recibe todo resuelto — el acceso a curso ({@code CURSO}) y el nivel del
 * visor ({@code NIVEL_MINIMO}, ya resuelto via {@code ProgresoNivel}) los trae quien llama.
 *
 * <p>Deliberadamente NO incluye la elegibilidad especial por tipo de evento
 * (MENTORIA_ALQUIMISTA) — esa es una capa aparte en el servicio de aplicacion, ver
 * {@code ReglasPorTipoEvento#requiereElegibilidad} y {@code ConsultarElegibilidadEventoPort}.
 */
public final class ResolverAudiencia {

    private ResolverAudiencia() {
    }

    public record VisorContexto(RolUsuario rol, int rangoNivel, UUID celulaId) {
    }

    /**
     * Proyeccion de audiencia de un evento con el rango de {@code nivelMinimoId} YA
     * resuelto contra el catalogo ({@link com.renaser.os.calendar.domain.model.nivelmembresia.ProgresoNivel}) —
     * el dominio de audiencia no conoce el catalogo de niveles, solo un numero.
     */
    public record EventoAudiencia(TipoAudiencia tipoAudiencia, Integer nivelMinimoRango, String cursoId,
                                   java.util.Set<RolUsuario> rolesDestino, UUID celulaDestinoId) {
    }

    public static boolean puedeVer(VisorContexto visor, EventoAudiencia evento, boolean tieneAccesoCurso) {
        if (visor.rol() == RolUsuario.ALCHEMIST || visor.rol() == RolUsuario.ADMIN) {
            return true;
        }
        return switch (evento.tipoAudiencia()) {
            case TODOS -> true;
            case NIVEL_MINIMO -> evento.nivelMinimoRango() != null && visor.rangoNivel() >= evento.nivelMinimoRango();
            case CURSO -> evento.cursoId() != null && tieneAccesoCurso;
            case ROLES -> evento.rolesDestino().contains(visor.rol());
            case CELULA -> evento.celulaDestinoId() != null && evento.celulaDestinoId().equals(visor.celulaId());
        };
    }
}

package com.renaser.os.calendar.domain.model.evento.audiencia;

import com.renaser.os.calendar.domain.model.evento.RolUsuario;
import com.renaser.os.calendar.domain.model.evento.TipoEvento;

import java.util.EnumSet;
import java.util.Set;

/**
 * Qué puede elegir cada rol al crear un evento (spec 044, FR-001, FR-002 y FR-007; decisiones P7 y P9 del dueño). Puro: el alcance concreto (qué
 * personas son sus alumnos, qué grupos son de su formación) lo resuelve la aplicación.
 *
 * <p>En {@code calendar} la guía de aprendiz es {@link RolUsuario#MENTOR} (D-265) y Soporte TI es {@link RolUsuario#ADMIN} (D-267).
 */
public final class QuienConvoca {

    private static final Set<TipoEvento> TIPOS_DEL_EQUIPO = EnumSet.of(TipoEvento.SESION_ESPECIAL, TipoEvento.ESPONTANEO);

    private QuienConvoca() {
    }

    /** Si el rol puede crear eventos (FR-001). */
    public static boolean puedeCrear(RolUsuario rol) {
        return rol != null && rol != RolUsuario.TRAINEE;
    }

    /** Administración y Alquimista: todo, como hasta hoy (D-186). */
    public static boolean administra(RolUsuario rol) {
        return rol == RolUsuario.ADMIN || rol == RolUsuario.ALCHEMIST;
    }

    /** Las partes que el rol puede elegir (FR-002). */
    public static Set<TipoDeParte> partesDe(RolUsuario rol) {
        if (rol == null) {
            return EnumSet.noneOf(TipoDeParte.class);
        }
        return switch (rol) {
            case ADMIN, ALCHEMIST -> EnumSet.of(TipoDeParte.PERSONA, TipoDeParte.GRUPO, TipoDeParte.MENTORES, TipoDeParte.COLOR,
                    TipoDeParte.PERSONAL);
            case MENTOR_LEAD -> EnumSet.of(TipoDeParte.PERSONA, TipoDeParte.GRUPO, TipoDeParte.MENTORES, TipoDeParte.COLOR,
                    TipoDeParte.PERSONAL);
            case MENTOR -> EnumSet.of(TipoDeParte.PERSONA, TipoDeParte.MIS_ALUMNOS, TipoDeParte.COLOR);
            case TRAINEE -> EnumSet.noneOf(TipoDeParte.class);
        };
    }

    /** Los tipos de evento que el rol puede crear (FR-007, P9). */
    public static Set<TipoEvento> tiposDe(RolUsuario rol) {
        if (administra(rol)) {
            return EnumSet.allOf(TipoEvento.class);
        }
        return puedeCrear(rol) ? TIPOS_DEL_EQUIPO : EnumSet.noneOf(TipoEvento.class);
    }

    /** Si el rol solo puede crear eventos con audiencia elegida (FR-007): todos menos Administración y Alquimista. */
    public static boolean soloConAudienciaElegida(RolUsuario rol) {
        return puedeCrear(rol) && !administra(rol);
    }
}

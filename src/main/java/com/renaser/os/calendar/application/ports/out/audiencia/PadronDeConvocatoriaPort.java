package com.renaser.os.calendar.application.ports.out.audiencia;

import com.renaser.os.calendar.domain.model.evento.RolUsuario;
import com.renaser.os.shared.domain.Formacion;
import com.renaser.os.shared.domain.UserId;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * A quién se puede convocar y cómo se lo nombra (spec 044). Compone contratos públicos de {@code users} y {@code community}; {@code calendar} no lee
 * sus tablas.
 */
public interface PadronDeConvocatoriaPort {

    /** Los integrantes vigentes de los grupos que el mentor acompaña hoy, sin él (S1). */
    Set<UserId> alumnosDe(UserId mentor);

    /** Si la persona es hoy alumna de ese mentor (sin armar la lista entera). */
    boolean esAlumnoDe(UserId persona, UserId mentor);

    /** Aprendices, mentores y guías activos que pertenecen a la formación (S2). */
    Set<UserId> personasDeLaFormacion(Formacion formacion);

    /** Aprendices activos que pertenecen a la formación: los únicos que tienen semáforo (S4). */
    Set<UserId> aprendicesDeLaFormacion(Formacion formacion);

    /** Mentores y guías de aprendiz activos que pertenecen a la formación. */
    Set<UserId> mentoresDeLaFormacion(Formacion formacion);

    /** Administración y Alquimista activos (S3); no pertenecen a una formación. */
    Set<UserId> personalActivo();

    /** Los grupos operativos de la formación, con sus integrantes vigentes. */
    List<GrupoConvocable> gruposDe(Formacion formacion);

    /** Los ids de quienes coinciden con el texto (nombre o correo, sin tildes ni mayúsculas). */
    Set<UserId> buscar(String texto);

    /** Lo que se muestra de cada persona: nombre, foto, rol y grupo. Las que no existen no aparecen. */
    Map<UserId, Convocado> fichas(Collection<UserId> personas);

    /** El rol que la persona tiene hoy, si existe y está activa. */
    java.util.Optional<RolUsuario> rolActivo(UserId persona);

    record GrupoConvocable(UUID id, String nombre, Formacion formacion, Set<UserId> integrantes) {
    }

    /**
     * @param rol      el rol en palabras («Aprendiz», «Mentor», «Guía de aprendiz», «Líder de mentores», «Administrador», «Alquimista»)
     * @param grupo    el nombre del grupo de la persona, o {@code null}
     */
    record Convocado(UserId id, String nombre, String fotoUrl, String rol, String grupo) {
    }
}

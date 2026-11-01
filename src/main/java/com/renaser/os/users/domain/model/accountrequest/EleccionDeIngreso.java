package com.renaser.os.users.domain.model.accountrequest;

import com.renaser.os.shared.domain.Formacion;
import com.renaser.os.users.api.UserRole;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

/**
 * La foto de lo que se decidió al aceptar una solicitud (spec 025): para el historial de «Solicitudes». No es la pertenencia viva de la persona (esa
 * está en su participación y en sus formaciones asignadas) ni la ficha que le toca (spec 026): es lo que se eligió ESE día.
 *
 * @param rol                  el rol base con el que entró; {@code null} en lo aceptado antes de las formaciones
 * @param formaciones          vacío = Administración o Alquimista, que ven las tres
 * @param duracionElegidaDias  solo el aprendiz la elige; {@code null} en el resto y en lo anterior a las formaciones
 * @param rolPersonalizadoId     el rol personalizado con el que entró, si entró con uno (su base es {@code rol}); {@code null} con un rol base
 * @param rolPersonalizadoNombre cómo se llamaba ese rol al aceptar: una foto, no cambia si después lo renombran o lo borran
 */
public record EleccionDeIngreso(UserRole rol, Set<Formacion> formaciones, Integer duracionElegidaDias, boolean conDiagnostico, UUID rolPersonalizadoId,
                                String rolPersonalizadoNombre) {

    public EleccionDeIngreso {
        formaciones = formaciones.isEmpty() ? Set.of() : Collections.unmodifiableSet(EnumSet.copyOf(formaciones));
        if (rolPersonalizadoId != null && (rolPersonalizadoNombre == null || rolPersonalizadoNombre.isBlank())) {
            throw new IllegalArgumentException("Un rol personalizado se guarda con su nombre.");
        }
    }

    /** Con un rol base: sin rol personalizado. */
    public EleccionDeIngreso(UserRole rol, Set<Formacion> formaciones, Integer duracionElegidaDias, boolean conDiagnostico) {
        this(rol, formaciones, duracionElegidaDias, conDiagnostico, null, null);
    }

    /** La misma elección, hecha con ese rol personalizado (el nombre que tiene hoy). */
    public EleccionDeIngreso conRolPersonalizado(UUID id, String nombre) {
        return new EleccionDeIngreso(rol, formaciones, duracionElegidaDias, conDiagnostico, id, nombre);
    }

    /** Lo aceptado antes de las formaciones fue siempre la Escuela, sin diagnóstico (V140). */
    public static EleccionDeIngreso anteriorALasFormaciones() {
        return new EleccionDeIngreso(null, Set.of(Formacion.POR_DEFECTO), null, false);
    }
}

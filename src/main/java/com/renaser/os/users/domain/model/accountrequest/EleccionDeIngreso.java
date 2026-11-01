package com.renaser.os.users.domain.model.accountrequest;

import com.renaser.os.shared.domain.Formacion;
import com.renaser.os.users.api.UserRole;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * La foto de lo que se decidió al aceptar una solicitud (spec 025): para el historial de «Solicitudes». No es la pertenencia viva de la persona (esa
 * está en su participación y en sus formaciones asignadas) ni la ficha que le toca (spec 026): es lo que se eligió ESE día.
 *
 * @param rol                  el rol base con el que entró; {@code null} en lo aceptado antes de las formaciones
 * @param formaciones          vacío = Administración o Alquimista, que ven las tres
 * @param duracionElegidaDias  solo el aprendiz la elige; {@code null} en el resto y en lo anterior a las formaciones
 */
public record EleccionDeIngreso(UserRole rol, Set<Formacion> formaciones, Integer duracionElegidaDias, boolean conDiagnostico) {

    public EleccionDeIngreso {
        formaciones = formaciones.isEmpty() ? Set.of() : Collections.unmodifiableSet(EnumSet.copyOf(formaciones));
    }

    /** Lo aceptado antes de las formaciones fue siempre la Escuela, sin diagnóstico (V140). */
    public static EleccionDeIngreso anteriorALasFormaciones() {
        return new EleccionDeIngreso(null, Set.of(Formacion.POR_DEFECTO), null, false);
    }
}

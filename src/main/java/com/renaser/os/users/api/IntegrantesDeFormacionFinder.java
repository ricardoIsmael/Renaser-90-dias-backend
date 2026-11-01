package com.renaser.os.users.api;

import com.renaser.os.shared.domain.Formacion;
import com.renaser.os.shared.domain.UserId;

import java.util.Set;

/**
 * Quiénes son de una formación hoy (spec 027, FR-018): los aprendices activos que la cursan y el personal asignado a ella. Sin cuentas de prueba de «ver
 * como», sin suspendidas. Sirve para decir quiénes faltan por aceptar unos términos. Administración y Alquimista no son de ninguna.
 */
public interface IntegrantesDeFormacionFinder {

    Set<UserId> deLaFormacion(Formacion formacion);
}

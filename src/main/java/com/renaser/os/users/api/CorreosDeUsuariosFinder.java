package com.renaser.os.users.api;

import com.renaser.os.shared.domain.UserId;

import java.util.Collection;
import java.util.Map;

/**
 * El correo de varias cuentas a la vez (spec 002, fase 2; D-271). Lo pide la consola Soporte TI para mostrar a quién
 * busca y a quién suma como probadora de un despliegue: dos personas pueden llamarse igual, el correo no.
 *
 * <p>Va aparte de {@link UserSummary} a propósito: ese resumen viaja a todos los módulos y no lleva datos de contacto.
 * Lo usan {@code plataforma}, detrás del permiso exclusivo de Soporte TI, y {@code calendar} para invitar al evento de Calendar a quien crea o
 * edita un evento con Meet automático (ADMIN o ALCHEMIST; spec 040, decisión 6 del dueño del 2026-11-05).
 */
public interface CorreosDeUsuariosFinder {

    /** Los ids que no existen no aparecen en el mapa. Una sola consulta. */
    Map<UserId, String> correosDe(Collection<UserId> ids);
}

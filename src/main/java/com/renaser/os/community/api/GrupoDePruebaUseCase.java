package com.renaser.os.community.api;

import com.renaser.os.shared.domain.UserId;

import java.util.Collection;
import java.util.UUID;

/**
 * Arma la primera vez, y repara cada vez, el «Grupo de prueba (ver como)» (spec 002, fase 2, US5; D-273): el mentor
 * de prueba lo lidera, los aprendices de prueba son sus integrantes y la guía de prueba lo acompaña. Lo pide la consola
 * Soporte TI al abrir «ver como» un rol que tiene grupo.
 *
 * <p>No pasa por los casos de uso de Administración (piden un ADMIN o ALQUIMISTA como actor y avisan a personas
 * reales): escribe las asignaciones como el sistema. A cambio, <b>solo acepta cuentas de prueba</b> (400 si alguna no lo
 * es) y solo lo pide Soporte TI activo (403): nunca mete a una persona real en este grupo ni saca a nadie de uno real.
 */
public interface GrupoDePruebaUseCase {

    /** @return el id del grupo de prueba */
    UUID asegurar(UserId soporteTi, UserId mentor, Collection<UserId> aprendices, Collection<UserId> guias);
}

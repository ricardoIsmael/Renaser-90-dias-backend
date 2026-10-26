package com.renaser.os.plataforma.application.ports.in;

import com.renaser.os.plataforma.application.ports.in.FuncionalidadEnConsola.Actor;
import com.renaser.os.users.api.UserRole;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Un rol personalizado como lo ve la consola (contrato de la fase 2, §4.5, {@code GET /soporte-ti/roles}; D-272).
 *
 * @param funcionalidades las claves marcadas que siguen en el catálogo, ordenadas
 * @param personas        cuántas cuentas lo tienen asignado
 * @param version         la que hay que mandar en el {@code PUT}
 */
public record RolPersonalizadoEnConsola(UUID id, String clave, String nombre, String descripcion, UserRole rolBase,
                                        boolean activo, List<String> funcionalidades, long personas, long version,
                                        Actor actualizadoPor, Instant actualizadoEn) {
}

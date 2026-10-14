package com.renaser.os.users.api;

import com.renaser.os.shared.domain.UserId;

import java.time.Instant;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Un rol personalizado visto desde otro módulo (contrato de la fase 2, §4.1; D-269): lo que lista la consola Soporte TI
 * y lo que copia en memoria la evaluación de interruptores de {@code plataforma}.
 *
 * @param clave           {@code rp:<id>}: la misma con la que figura en la auditoría y en los roles asignables
 * @param funcionalidades las claves marcadas, tal como se guardaron (una que ya no está en el catálogo se ignora al
 *                        evaluar)
 * @param personas        cuántas cuentas lo tienen asignado hoy, activo o no
 */
public record RolPersonalizadoResumen(UUID id, String clave, String nombre, String descripcion, UserRole rolBase,
                                      boolean activo, Set<String> funcionalidades, long personas, long version,
                                      UserId actualizadoPor, Instant actualizadoEn) {

    public RolPersonalizadoResumen {
        Objects.requireNonNull(id, "id es obligatorio");
        Objects.requireNonNull(rolBase, "rolBase es obligatorio");
        funcionalidades = Set.copyOf(funcionalidades);
    }
}

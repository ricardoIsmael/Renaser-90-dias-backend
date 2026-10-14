package com.renaser.os.plataforma.domain.model.roles;

import com.renaser.os.shared.domain.UserId;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * La copia en memoria de los roles personalizados de una instancia (contrato de la fase 2, §4.4; D-269): quién tiene
 * uno vigente y qué marca cada rol activo. Inmutable: se reemplaza entera al recargar, así que leerla en cada pedido
 * es leer una referencia.
 *
 * @param personaARol solo las asignaciones vigentes (rol activo que hereda del rol de la cuenta)
 * @param roles       solo los roles activos
 */
public record FotoDeRolesPersonalizados(Map<UserId, UUID> personaARol, Map<UUID, RolEnMemoria> roles) {

    private static final FotoDeRolesPersonalizados VACIA = new FotoDeRolesPersonalizados(Map.of(), Map.of());

    public FotoDeRolesPersonalizados {
        personaARol = Map.copyOf(personaARol);
        roles = Map.copyOf(roles);
    }

    /** Antes de la primera lectura, o si nunca se pudo leer: nadie tiene rol personalizado (nada se restringe). */
    public static FotoDeRolesPersonalizados vacia() {
        return VACIA;
    }

    /** El rol personalizado vigente de esa persona, si tiene uno y está activo. */
    public Optional<RolEnMemoria> de(UserId persona) {
        UUID rol = persona == null ? null : personaARol.get(persona);
        return rol == null ? Optional.empty() : Optional.ofNullable(roles.get(rol));
    }
}

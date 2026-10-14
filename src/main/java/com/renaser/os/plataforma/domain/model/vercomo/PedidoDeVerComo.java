package com.renaser.os.plataforma.domain.model.vercomo;

import com.renaser.os.users.api.UserRole;

import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * Lo que pide la consola en {@code POST /soporte-ti/ver-como}: {@code { rol }} o {@code { rolPersonalizadoId }}, uno de
 * los dos (contrato fase-2.md §5). Todo lo inválido es 400 antes de crear nada.
 *
 * @param rol                el rol base, o {@code null} si se pidió un rol personalizado
 * @param rolPersonalizadoId el rol personalizado, o {@code null} si se pidió un rol base
 */
public record PedidoDeVerComo(UserRole rol, UUID rolPersonalizadoId) {

    public PedidoDeVerComo {
        if ((rol == null) == (rolPersonalizadoId == null)) {
            throw new IllegalArgumentException("Elige un rol o un rol personalizado, uno de los dos");
        }
        if (rol == UserRole.IT_SUPPORT) {
            throw new IllegalArgumentException("Elige uno de los seis roles: Soporte TI no se mira con «ver como»");
        }
    }

    /**
     * @param rol el nombre del rol en la API ({@code "MENTOR"}, {@code "TRAINEE_GUIDE"}…), sin importar mayúsculas
     */
    public static PedidoDeVerComo de(String rol, UUID rolPersonalizadoId) {
        if (rol == null || rol.isBlank()) {
            return new PedidoDeVerComo(null, rolPersonalizadoId);
        }
        UserRole base = rolBase(rol.strip().toUpperCase(Locale.ROOT))
                .orElseThrow(() -> new IllegalArgumentException("No existe el rol «" + rol.strip() + "»"));
        return new PedidoDeVerComo(base, rolPersonalizadoId);
    }

    public boolean esDeUnRolBase() {
        return rol != null;
    }

    private static Optional<UserRole> rolBase(String nombre) {
        for (UserRole candidato : UserRole.values()) {
            if (candidato.name().equals(nombre)) {
                return Optional.of(candidato);
            }
        }
        return Optional.empty();
    }
}

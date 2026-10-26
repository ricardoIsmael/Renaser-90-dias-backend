package com.renaser.os.plataforma.domain.model.roles;

import com.renaser.os.plataforma.domain.model.catalogo.CatalogoDeFuncionalidades;
import com.renaser.os.plataforma.domain.model.catalogo.DefinicionDeFuncionalidad;
import com.renaser.os.users.api.UserRole;

import java.util.Collection;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

/**
 * Lo que la consola valida de un pedido sobre un rol personalizado antes de llamar a {@code users} (contrato de la fase
 * 2, §4.3 y §4.5; D-272): el motivo, el rol base y, sobre todo, que <b>cada clave exista en el catálogo y que el rol
 * base la use</b> ({@code rolesQueLaUsan}). Un rol hijo nunca tiene más que su base (spec US7, escenario 1). Esta parte
 * vive acá y no en {@code users} porque el catálogo es de {@code plataforma}. Todo error es 400.
 */
public final class PedidoDeRolPersonalizado {

    /** El mismo tope que el motivo de un cambio de interruptor ({@code CambioDeFuncionalidad}). */
    static final int LARGO_MAXIMO_DEL_MOTIVO = 500;

    private PedidoDeRolPersonalizado() {
    }

    public static String motivoValido(String motivo) {
        if (motivo == null || motivo.isBlank()) {
            throw new IllegalArgumentException("Escribe el motivo del cambio");
        }
        String limpio = motivo.strip();
        if (limpio.codePointCount(0, limpio.length()) > LARGO_MAXIMO_DEL_MOTIVO) {
            throw new IllegalArgumentException("El motivo no puede pasar de " + LARGO_MAXIMO_DEL_MOTIVO
                    + " caracteres");
        }
        return limpio;
    }

    /** El nombre de la API ({@code MENTOR}); nunca Soporte TI (FR-029). */
    public static UserRole rolBaseValido(String rolBase) {
        if (rolBase == null || rolBase.isBlank()) {
            throw new IllegalArgumentException("Falta el rol base");
        }
        UserRole rol;
        try {
            rol = UserRole.valueOf(rolBase.strip());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("El rol «" + rolBase + "» no existe.");
        }
        if (rol == UserRole.IT_SUPPORT) {
            throw new IllegalArgumentException("Un rol personalizado no hereda de Soporte TI");
        }
        return rol;
    }

    /** Las claves marcadas, sin repetir: cada una del catálogo y de las que usa {@code rolBase}. */
    public static Set<String> clavesValidas(Collection<String> claves, UserRole rolBase,
                                            CatalogoDeFuncionalidades catalogo) {
        if (claves == null) {
            throw new IllegalArgumentException("Faltan las funcionalidades del rol (puede ser una lista vacía)");
        }
        Set<String> validas = new TreeSet<>();
        for (String clave : claves) {
            Optional<DefinicionDeFuncionalidad> definicion = catalogo.definicion(clave);
            if (definicion.isEmpty()) {
                throw new IllegalArgumentException("«" + clave + "» no está en el catálogo de funcionalidades");
            }
            if (!definicion.get().rolesQueLaUsan().contains(rolBase)) {
                throw new IllegalArgumentException("«" + definicion.get().nombre() + "» no la usa "
                        + rolBase.nombreVisible() + ": un rol personalizado no tiene más que su base");
            }
            validas.add(clave);
        }
        return validas;
    }
}

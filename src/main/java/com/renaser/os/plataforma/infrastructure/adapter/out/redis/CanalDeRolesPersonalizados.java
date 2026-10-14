package com.renaser.os.plataforma.infrastructure.adapter.out.redis;

/**
 * El canal Redis por el que una instancia avisa a las demás que cambiaron los roles personalizados (D-269). Aparte de
 * {@link CanalDeFuncionalidades}: ese lleva la versión del estado de los interruptores y descarta lo que no es un
 * número; los roles no tienen una versión global.
 */
public final class CanalDeRolesPersonalizados {

    public static final String NOMBRE = "renaser:roles-personalizados";

    private CanalDeRolesPersonalizados() {
    }
}

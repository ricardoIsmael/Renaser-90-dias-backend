package com.renaser.os.plataforma.domain.model.estado;

import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.UserRole;

import java.time.Instant;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * El desvío guardado de una funcionalidad (contrato §4): prendida o apagada para todos, para qué roles está apagada y
 * el aviso que ve la gente. Sin fila rige el valor por defecto del catálogo.
 *
 * <p>Solo se guardan los roles APAGADOS: un rol «prendido» con la funcionalidad prendida para todos no cambia nada, y
 * con la funcionalidad apagada para todos tampoco (apagada para todos gana, spec «Rol y global en conflicto»).
 *
 * @param version del cambio que dejó esta fila; sale de una secuencia global, así que la mayor de todas es la versión
 *                del estado completo
 */
public record EstadoDeFuncionalidad(String clave, boolean prendidaGlobal, Set<UserRole> apagadaPara, String aviso,
                                    long version, UserId actualizadoPor, Instant actualizadoEn) {

    /** Largo máximo del aviso editable (FR-005), contado como Postgres ({@code char_length}). */
    public static final int LARGO_MAXIMO_DEL_AVISO = 200;

    public EstadoDeFuncionalidad {
        Objects.requireNonNull(clave, "clave es obligatoria");
        apagadaPara = apagadaPara == null || apagadaPara.isEmpty() ? Set.of() : Set.copyOf(EnumSet.copyOf(apagadaPara));
        aviso = normalizarAviso(aviso);
        Objects.requireNonNull(actualizadoPor, "actualizadoPor es obligatorio");
        Objects.requireNonNull(actualizadoEn, "actualizadoEn es obligatorio");
    }

    /** Si está apagada para ese rol por una excepción propia (sin mirar el estado global ni la madre). */
    public boolean apagadaPara(UserRole rol) {
        return apagadaPara.contains(rol);
    }

    /** Lo que queda en la auditoría como «antes» o «después». */
    public Map<String, Object> paraAuditoria() {
        return foto(prendidaGlobal, apagadaPara, aviso);
    }

    /** La misma forma para el estado por defecto de una funcionalidad sin fila. */
    public static Map<String, Object> porDefectoParaAuditoria(boolean prendida) {
        return foto(prendida, Set.of(), null);
    }

    /** Sin espacios de más; vacío es «sin aviso» (se muestra el genérico). Más de 200 caracteres es un error. */
    static String normalizarAviso(String aviso) {
        if (aviso == null || aviso.isBlank()) {
            return null;
        }
        String limpio = aviso.strip();
        if (limpio.codePointCount(0, limpio.length()) > LARGO_MAXIMO_DEL_AVISO) {
            throw new IllegalArgumentException("El aviso no puede pasar de " + LARGO_MAXIMO_DEL_AVISO + " caracteres");
        }
        return limpio;
    }

    /**
     * {@code prendidaGlobal}, {@code porRol} (los siete roles, con el nombre de la API, como los manda la consola),
     * {@code apagadaPara} (solo los apagados, para leerlo de un vistazo) y {@code aviso}.
     */
    private static Map<String, Object> foto(boolean prendida, Set<UserRole> apagadaPara, String aviso) {
        Map<String, Object> porRol = new LinkedHashMap<>();
        for (UserRole rol : UserRole.values()) {
            porRol.put(rol.name(), !apagadaPara.contains(rol));
        }
        Map<String, Object> foto = new LinkedHashMap<>();
        foto.put("prendidaGlobal", prendida);
        foto.put("porRol", porRol);
        foto.put("apagadaPara", apagadaPara.stream().map(Enum::name).sorted().toList());
        foto.put("aviso", aviso);
        return foto;
    }
}

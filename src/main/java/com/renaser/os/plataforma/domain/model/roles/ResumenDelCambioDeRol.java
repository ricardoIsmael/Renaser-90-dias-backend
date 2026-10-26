package com.renaser.os.plataforma.domain.model.roles;

import com.renaser.os.users.api.UserRole;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Qué cambió de un rol personalizado, en palabras, para el aviso a Administración (D-272, decisión del dueño del
 * 2026-10-14: «si Soporte TI edita un rol que tiene personas —funcionalidades, activo o base— o lo reemplaza en bloque
 * moviendo personas, avisar a todos los ADMIN y ALQUIMISTA»). Vacío si el cambio no le cambia nada a nadie: sin
 * personas, o solo el nombre o la descripción.
 */
public final class ResumenDelCambioDeRol {

    private ResumenDelCambioDeRol() {
    }

    /** Una foto de lo que importa del rol para quienes lo tienen. */
    public record Foto(UserRole rolBase, boolean activo, Set<String> funcionalidades) {
    }

    public static Optional<String> deUnaEdicion(Foto antes, Foto despues, long personas) {
        if (personas <= 0) {
            return Optional.empty();
        }
        List<String> cambios = new ArrayList<>();
        if (antes.activo() && !despues.activo()) {
            cambios.add("lo desactivó: quedan solo con su rol base (" + despues.rolBase().nombreVisible() + ")");
        } else if (!antes.activo() && despues.activo()) {
            cambios.add("lo volvió a activar");
        }
        if (antes.rolBase() != despues.rolBase()) {
            cambios.add("ahora hereda de " + despues.rolBase().nombreVisible());
        }
        if (!antes.funcionalidades().equals(despues.funcionalidades())) {
            cambios.add(cambioDeFuncionalidades(antes.funcionalidades(), despues.funcionalidades()));
        }
        return cambios.isEmpty() ? Optional.empty() : Optional.of(String.join("; ", cambios));
    }

    /** @param destino {@code null} si quedaron solo con su rol base */
    public static Optional<String> deUnReemplazo(String destino, UserRole rolBase, int personasMovidas) {
        if (personasMovidas <= 0) {
            return Optional.empty();
        }
        return Optional.of(destino == null
                ? "quedaron solo con su rol base (" + rolBase.nombreVisible() + ")"
                : "las pasó a «" + destino + "»");
    }

    private static String cambioDeFuncionalidades(Set<String> antes, Set<String> despues) {
        long sumadas = despues.stream().filter(clave -> !antes.contains(clave)).count();
        long quitadas = antes.stream().filter(clave -> !despues.contains(clave)).count();
        List<String> partes = new ArrayList<>();
        if (sumadas > 0) {
            partes.add("sumó " + sumadas + (sumadas == 1 ? " funcionalidad" : " funcionalidades"));
        }
        if (quitadas > 0) {
            partes.add("quitó " + quitadas + (quitadas == 1 ? " funcionalidad" : " funcionalidades"));
        }
        return String.join(" y ", partes);
    }
}

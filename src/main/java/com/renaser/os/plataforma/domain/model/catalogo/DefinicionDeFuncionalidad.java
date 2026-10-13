package com.renaser.os.plataforma.domain.model.catalogo;

import com.renaser.os.users.api.UserRole;

import java.util.Objects;
import java.util.Set;

/**
 * Una funcionalidad del catálogo (spec 002, FR-001; contrato §3). Se declara en código y nunca se crea desde la app.
 *
 * <p>Apagar la madre apaga sus hijas (FR-002). La madre es el prefijo de la clave ({@code chat.grupos} es hija de
 * {@code chat}), salvo en las dos claves de la spec cuyo prefijo no es una funcionalidad: {@code cuenta.eliminar} y
 * {@code mentor.mi_grupo} son raíces.
 *
 * @param madre          la clave de la madre, o {@code null} si es raíz
 * @param rolesQueLaUsan a quién le llega, para que la consola muestre los interruptores por rol que importan. Vacío
 *                       solo en {@code registro}, que se usa sin cuenta. No restringe nada: lo que autoriza sigue
 *                       siendo {@code @RequiresPermission} y los guards.
 */
public record DefinicionDeFuncionalidad(String clave, String madre, String area, String nombre, String descripcion,
                                        Set<UserRole> rolesQueLaUsan, boolean critica, ValorPorDefecto porDefecto,
                                        PoliticaDeProcesos procesos) {

    public DefinicionDeFuncionalidad {
        if (clave == null || !clave.matches("[a-z_]+(\\.[a-z_]+)*")) {
            throw new IllegalArgumentException("Clave de funcionalidad invalida: " + clave);
        }
        if (madre != null && !clave.startsWith(madre + ".")) {
            throw new IllegalArgumentException("La madre de " + clave + " tiene que ser su prefijo: " + madre);
        }
        Objects.requireNonNull(area, "area es obligatoria");
        Objects.requireNonNull(nombre, "nombre es obligatorio");
        Objects.requireNonNull(descripcion, "descripcion es obligatoria");
        rolesQueLaUsan = Set.copyOf(rolesQueLaUsan);
        Objects.requireNonNull(porDefecto, "porDefecto es obligatorio");
        Objects.requireNonNull(procesos, "procesos es obligatorio");
    }

    /** Si una clave {@code ia.*}: solo esas puede apagarlas el circuito automático de la IA (contrato §5). */
    public boolean esDeLaIa() {
        return clave.equals("ia") || clave.startsWith("ia.");
    }
}

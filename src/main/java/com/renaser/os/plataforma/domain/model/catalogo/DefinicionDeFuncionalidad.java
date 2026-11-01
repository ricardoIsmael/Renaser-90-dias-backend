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
 * @param apagadaPorDefectoPara los roles para los que la funcionalidad «nace apagada» mientras nadie guarde una fila
 *                       (spec 034: el Código Renaser en las fichas). Distinto de {@link ValorPorDefecto#APAGADA}, que la
 *                       apaga para todos: acá el resto de los roles la ven prendida. Con una fila guardada manda la fila.
 * @param seEsconde      si, apagada, deja de existir para quien la tiene apagada (404 «no disponible», la app la
 *                       esconde) en vez de mostrar el aviso de mantenimiento (503). Para lo que no es un servicio que se
 *                       cae, sino algo que un rol no debe ver.
 */
public record DefinicionDeFuncionalidad(String clave, String madre, String area, String nombre, String descripcion,
                                        Set<UserRole> rolesQueLaUsan, boolean critica, ValorPorDefecto porDefecto,
                                        PoliticaDeProcesos procesos, Set<UserRole> apagadaPorDefectoPara,
                                        boolean seEsconde) {

    /** Una funcionalidad común: prendida para todos por defecto y, apagada, con el aviso de mantenimiento. */
    public DefinicionDeFuncionalidad(String clave, String madre, String area, String nombre, String descripcion,
                                     Set<UserRole> rolesQueLaUsan, boolean critica, ValorPorDefecto porDefecto,
                                     PoliticaDeProcesos procesos) {
        this(clave, madre, area, nombre, descripcion, rolesQueLaUsan, critica, porDefecto, procesos, Set.of(), false);
    }

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
        apagadaPorDefectoPara = apagadaPorDefectoPara == null ? Set.of() : Set.copyOf(apagadaPorDefectoPara);
    }

    /** Si, sin fila guardada, esta funcionalidad nace apagada para ese rol. */
    public boolean nacioApagadaPara(UserRole rol) {
        return rol != null && apagadaPorDefectoPara.contains(rol);
    }

    /** Si una clave {@code ia.*}: solo esas puede apagarlas el circuito automático de la IA (contrato §5). */
    public boolean esDeLaIa() {
        return clave.equals("ia") || clave.startsWith("ia.");
    }
}

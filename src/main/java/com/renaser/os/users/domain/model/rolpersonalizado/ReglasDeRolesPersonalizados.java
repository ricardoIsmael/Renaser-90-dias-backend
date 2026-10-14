package com.renaser.os.users.domain.model.rolpersonalizado;

import com.renaser.os.users.api.RolPersonalizadoRechazadoException;
import com.renaser.os.users.api.UserRole;

import java.util.Collection;
import java.util.UUID;

/**
 * Las reglas de los roles personalizados que miran más de una fila (spec 002, FR-029 y FR-034): el nombre no se repite
 * ni se confunde con un rol base, y no hay más de {@value #MAXIMO_DE_ACTIVOS} activos. Las aplica el caso de uso con
 * las escrituras serializadas (un candado de transacción), así que dos altas a la vez no se cuelan juntas.
 */
public final class ReglasDeRolesPersonalizados {

    /** FR-034 («a confirmar: 20»), confirmado en el contrato de la fase 2 (§4.3): que la consola siga manejable. */
    public static final int MAXIMO_DE_ACTIVOS = 20;

    private ReglasDeRolesPersonalizados() {
    }

    /**
     * El nombre no puede ser el de un rol base —se confundiría con él en «Equipo y roles»— ni el de otro rol
     * personalizado. Se comparan sin mayúsculas ni tildes. Se rechazan los siete nombres visibles (también «Soporte
     * TI», aunque no se asigne desde la app).
     *
     * @param propio el id del rol que se edita (su propio nombre no cuenta), o {@code null} al crear
     */
    public static void exigirNombreLibre(RolPersonalizado candidato, Collection<RolPersonalizado> existentes,
                                         UUID propio) {
        for (UserRole base : UserRole.values()) {
            if (candidato.seLlamaIgualQue(base.nombreVisible())) {
                throw RolPersonalizadoRechazadoException.nombreRepetido("«" + candidato.nombre()
                        + "» es el nombre de un rol base: elige otro");
            }
        }
        for (RolPersonalizado otro : existentes) {
            if (!otro.id().equals(propio) && candidato.seLlamaIgualQue(otro.nombre())) {
                throw RolPersonalizadoRechazadoException.nombreRepetido("Ya hay un rol que se llama «" + otro.nombre()
                        + "»");
            }
        }
    }

    /**
     * Hasta {@value #MAXIMO_DE_ACTIVOS} activos (FR-034). Se pide al crear y al reactivar; desactivar y editar uno
     * activo no suman.
     *
     * @param propio el id del rol que se reactiva (no se cuenta a sí mismo), o {@code null} al crear
     */
    public static void exigirCupo(Collection<RolPersonalizado> existentes, UUID propio) {
        long activos = existentes.stream().filter(RolPersonalizado::activo).filter(r -> !r.id().equals(propio))
                .count();
        if (activos >= MAXIMO_DE_ACTIVOS) {
            throw RolPersonalizadoRechazadoException.maximoDeRoles("Ya hay " + MAXIMO_DE_ACTIVOS
                    + " roles personalizados activos: desactiva uno antes de sumar otro");
        }
    }

    /**
     * El destino de un reemplazo en bloque: activo y con el mismo rol base que el origen (si no, las personas cambiarían
     * de rol base sin que Administración lo decida).
     */
    public static void exigirDestinoValido(RolPersonalizado origen, RolPersonalizado destino) {
        if (destino.id().equals(origen.id())) {
            throw new IllegalArgumentException("El rol de destino es el mismo que el de origen");
        }
        if (!destino.activo()) {
            throw new IllegalArgumentException("«" + destino.nombre() + "» está desactivado: no recibe personas");
        }
        if (destino.rolBase() != origen.rolBase()) {
            throw new IllegalArgumentException("«" + destino.nombre() + "» hereda de otro rol base: el reemplazo en "
                    + "bloque es entre roles con la misma base");
        }
    }
}

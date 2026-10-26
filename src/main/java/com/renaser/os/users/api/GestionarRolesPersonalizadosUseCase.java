package com.renaser.os.users.api;

import com.renaser.os.shared.domain.UserId;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Crear, editar (también desactivar y reactivar), borrar y reemplazar en bloque un rol personalizado (spec 002, FR-029,
 * FR-033 y FR-034; contrato de la fase 2, §4; D-272). Lo llama la consola Soporte TI ({@code plataforma}), que antes
 * valida que cada clave exista en el catálogo y que el rol base la use: el catálogo vive en {@code plataforma} y
 * {@code users} no lo ve. Cada operación exige que {@code actor} sea Soporte TI activo (403), publica
 * {@link RolPersonalizadoCambiadoEvent} y devuelve las fotos de antes y después para la auditoría.
 *
 * <p>Errores: 400 ({@code IllegalArgumentException}) por datos inválidos, nombre repetido o de un rol base, base Soporte
 * TI, más de 20 activos o un destino de reemplazo inválido; 404 si el rol no existe; 409
 * ({@code IllegalStateException}) por versión vieja, o por borrar o cambiar de base un rol con personas.
 */
public interface GestionarRolesPersonalizadosUseCase {

    CambioDeRolPersonalizado crear(CrearRolPersonalizadoCommand command);

    CambioDeRolPersonalizado editar(EditarRolPersonalizadoCommand command);

    /** {@code despues} es {@code null}. */
    CambioDeRolPersonalizado borrar(BorrarRolPersonalizadoCommand command);

    ReemplazoEnBloque reemplazarEnBloque(ReemplazarRolPersonalizadoCommand command);

    /** @param funcionalidades las claves marcadas; vacía es válida */
    record CrearRolPersonalizadoCommand(UserId actor, String nombre, String descripcion, UserRole rolBase,
                                        Set<String> funcionalidades) {

        public CrearRolPersonalizadoCommand {
            Objects.requireNonNull(actor, "actor es obligatorio");
        }
    }

    /**
     * @param rolBase {@code null} = el mismo. Cambiarlo con personas asignadas es 409 (FR-033); sin personas se permite
     *                (agregado al contrato, que no lo trae en el cuerpo del PUT)
     * @param version la que se estaba mirando (bloqueo optimista, 409)
     */
    record EditarRolPersonalizadoCommand(UserId actor, UUID id, String nombre, String descripcion, UserRole rolBase,
                                         Set<String> funcionalidades, boolean activo, long version) {

        public EditarRolPersonalizadoCommand {
            Objects.requireNonNull(actor, "actor es obligatorio");
            Objects.requireNonNull(id, "id es obligatorio");
        }
    }

    record BorrarRolPersonalizadoCommand(UserId actor, UUID id) {

        public BorrarRolPersonalizadoCommand {
            Objects.requireNonNull(actor, "actor es obligatorio");
            Objects.requireNonNull(id, "id es obligatorio");
        }
    }

    /** @param destinoId otro rol activo con la misma base, o {@code null} para dejar a esas personas solo con la base */
    record ReemplazarRolPersonalizadoCommand(UserId actor, UUID id, UUID destinoId) {

        public ReemplazarRolPersonalizadoCommand {
            Objects.requireNonNull(actor, "actor es obligatorio");
            Objects.requireNonNull(id, "id es obligatorio");
        }
    }

    /** @param antes {@code null} al crear; @param despues {@code null} al borrar */
    record CambioDeRolPersonalizado(RolPersonalizadoResumen antes, RolPersonalizadoResumen despues) {
    }

    /** @param destino {@code null} si las personas quedaron solo con su rol base */
    record ReemplazoEnBloque(RolPersonalizadoResumen origen, RolPersonalizadoResumen destino, int personasMovidas) {
    }
}

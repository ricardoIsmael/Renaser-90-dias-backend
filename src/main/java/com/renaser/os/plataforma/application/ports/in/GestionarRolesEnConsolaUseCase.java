package com.renaser.os.plataforma.application.ports.in;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Los roles personalizados en la consola Soporte TI (spec 002, US7; contrato de la fase 2, §4.5; D-269). Cada operación
 * exige Soporte TI con la consola abierta (403 / 423), valida las claves contra el catálogo y el rol base (400), llama
 * a {@code users.api} y deja su fila de auditoría {@code ROL_PERSONALIZADO} en la misma transacción.
 *
 * <p><b>Críticos</b> (piden {@code X-Reautenticacion}, 428 sin ella): borrar y reemplazar en bloque. Borrar no se
 * deshace, y el reemplazo cambia de una vez lo que pueden usar todas las personas de un rol. Crear, editar y
 * desactivar no: no tocan a nadie (crear), o se revierten con otro {@code PUT} y quedan auditados.
 */
public interface GestionarRolesEnConsolaUseCase {

    List<RolPersonalizadoEnConsola> listar(AccesoDeConsola acceso);

    RolPersonalizadoEnConsola crear(CrearRolEnConsolaCommand command);

    RolPersonalizadoEnConsola editar(EditarRolEnConsolaCommand command);

    void borrar(BorrarRolEnConsolaCommand command);

    /** @return cuántas personas se movieron */
    int reemplazar(ReemplazarRolEnConsolaCommand command);

    /** @param rolBase el nombre de la API ({@code MENTOR}) */
    record CrearRolEnConsolaCommand(AccesoDeConsola acceso, String nombre, String descripcion, String rolBase,
                                    List<String> funcionalidades, String motivo, String dispositivo) {

        public CrearRolEnConsolaCommand {
            Objects.requireNonNull(acceso, "acceso es obligatorio");
        }
    }

    /** @param rolBase opcional: {@code null} = el mismo (cambiarlo con personas es 409) */
    record EditarRolEnConsolaCommand(AccesoDeConsola acceso, UUID id, String nombre, String descripcion,
                                     String rolBase, List<String> funcionalidades, Boolean activo, String motivo,
                                     Long version, String dispositivo) {

        public EditarRolEnConsolaCommand {
            Objects.requireNonNull(acceso, "acceso es obligatorio");
        }
    }

    record BorrarRolEnConsolaCommand(AccesoDeConsola acceso, UUID id, String motivo, String reautenticacion,
                                     String dispositivo) {

        public BorrarRolEnConsolaCommand {
            Objects.requireNonNull(acceso, "acceso es obligatorio");
        }
    }

    /** @param destinoId {@code null}: las personas quedan solo con su rol base */
    record ReemplazarRolEnConsolaCommand(AccesoDeConsola acceso, UUID id, UUID destinoId, String motivo,
                                         String reautenticacion, String dispositivo) {

        public ReemplazarRolEnConsolaCommand {
            Objects.requireNonNull(acceso, "acceso es obligatorio");
        }
    }
}

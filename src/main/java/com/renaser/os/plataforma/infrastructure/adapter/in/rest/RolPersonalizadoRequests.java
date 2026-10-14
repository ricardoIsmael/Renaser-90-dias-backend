package com.renaser.os.plataforma.infrastructure.adapter.in.rest;

import java.util.List;
import java.util.UUID;

/**
 * Los cuerpos de la consola de roles personalizados (contrato de la fase 2, §4.5; D-269). Sin anotaciones de
 * validación a propósito: los valida el caso de uso <b>después</b> de los cerrojos de la consola, así un pedido sin
 * desbloqueo es 423 aunque el cuerpo venga incompleto (y no un 400 que delate qué se esperaba).
 */
final class RolPersonalizadoRequests {

    private RolPersonalizadoRequests() {
    }

    /** {@code POST /soporte-ti/roles}. */
    record Crear(String nombre, String descripcion, String rolBase, List<String> funcionalidades, String motivo) {
    }

    /** {@code PUT /soporte-ti/roles/{id}}. {@code rolBase} es opcional (agregado al contrato): sin personas, cambia. */
    record Editar(String nombre, String descripcion, String rolBase, List<String> funcionalidades, Boolean activo,
                  String motivo, Long version) {
    }

    /** {@code POST /soporte-ti/roles/{id}/reemplazar}. */
    record Reemplazar(UUID rolDestinoId, String motivo) {
    }

    /** La respuesta del reemplazo en bloque. */
    record Reemplazados(int personasMovidas) {
    }
}

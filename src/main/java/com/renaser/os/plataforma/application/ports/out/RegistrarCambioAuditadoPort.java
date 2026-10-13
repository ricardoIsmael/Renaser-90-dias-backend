package com.renaser.os.plataforma.application.ports.out;

import com.renaser.os.plataforma.domain.model.auditoria.CambioAuditado;

/** Agrega una fila a la auditoría append-only. No hay operación para editarla ni borrarla. */
public interface RegistrarCambioAuditadoPort {

    void registrar(CambioAuditado cambio);
}

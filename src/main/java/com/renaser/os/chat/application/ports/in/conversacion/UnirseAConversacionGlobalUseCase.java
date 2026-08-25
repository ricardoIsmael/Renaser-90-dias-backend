package com.renaser.os.chat.application.ports.in.conversacion;

import com.renaser.os.shared.domain.UserId;

/**
 * Busca-o-crea la conversacion GLOBAL y agrega a {@code usuarioId} como participante, de
 * forma idempotente. Sin comando propio: lo dispara solo
 * {@code UsuarioRegistradoChatListener} (adapter/in/event) al escuchar
 * {@code users.api.UsuarioRegistradoEvent} — DECISION 2026-08-24 del baseline
 * (V1__baseline_renaser.sql:1293-1295): "todo usuario nuevo se agrega AUTOMATICAMENTE a la
 * conversacion GLOBAL".
 */
public interface UnirseAConversacionGlobalUseCase {

    void unirse(UserId usuarioId);
}

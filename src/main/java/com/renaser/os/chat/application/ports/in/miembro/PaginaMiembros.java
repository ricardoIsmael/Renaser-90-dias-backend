package com.renaser.os.chat.application.ports.in.miembro;

import com.renaser.os.shared.domain.UserId;

import java.util.List;

/**
 * Pagina de {@link MiembroResumen}, cursor keyset (nunca OFFSET — CLAUDE.MD del
 * encargo). {@code siguienteCursor} es el id del ultimo miembro devuelto en esta
 * pagina; {@code null} si no hay mas.
 */
public record PaginaMiembros(List<MiembroResumen> miembros, UserId siguienteCursor, boolean hayMas) {
}

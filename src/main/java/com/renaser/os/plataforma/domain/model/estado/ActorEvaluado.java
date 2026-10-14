package com.renaser.os.plataforma.domain.model.estado;

import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.UserRole;

/**
 * Quién pide, para la evaluación de una persona (contrato §5). El rol se lee recién cuando hace falta —una excepción por
 * rol en la cadena— y una sola vez: {@code GET /funcionalidades/mias} recorre todo el catálogo y, sin excepciones por
 * rol, no toca la base.
 *
 * <p>Las {@link ReglaDeDisponibilidad} de la fase 2 usan {@link #id()} (la cubeta del despliegue progresivo, el rol
 * personalizado asignado) y resuelven lo suyo con su propia copia en memoria.
 */
public interface ActorEvaluado {

    UserId id();

    /** El rol de hoy; una cuenta sin rol conocido se evalúa como {@code TRAINEE}. */
    UserRole rol();
}

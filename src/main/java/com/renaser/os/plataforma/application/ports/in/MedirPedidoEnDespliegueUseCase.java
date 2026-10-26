package com.renaser.os.plataforma.application.ports.in;

import com.renaser.os.shared.domain.UserId;

/**
 * Cuenta un pedido a una funcionalidad para las métricas del despliegue (contrato fase 2 §3.3): lo llama el interceptor
 * de interruptores al terminar cada pedido. Tiene que costar casi nada: sin despliegues en curso no hace nada, y con
 * ellos suma en memoria (se escribe en lote cada minuto). Nunca lanza.
 *
 * @see com.renaser.os.plataforma.domain.model.despliegue.ConteoDeDespliegue
 */
public interface MedirPedidoEnDespliegueUseCase {

    /**
     * @param persona {@code null} si el pedido no tiene una persona (no se cuenta: sin persona no hay cubeta)
     * @param error   si la respuesta fue 5xx
     */
    void registrar(String clave, UserId persona, boolean error);
}

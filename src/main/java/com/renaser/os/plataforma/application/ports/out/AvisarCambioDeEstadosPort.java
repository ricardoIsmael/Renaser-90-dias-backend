package com.renaser.os.plataforma.application.ports.out;

/**
 * Avisa a las demás instancias que el estado cambió (plan.md D3: canal Redis {@code renaser:funcionalidades}). Es un
 * empujón, no la fuente de verdad: si no llega, cada instancia se pone al día en su recarga de cada 30 s.
 */
public interface AvisarCambioDeEstadosPort {

    void avisar(long version);
}

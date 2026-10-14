package com.renaser.os.plataforma.application.ports.out;

/**
 * Avisa a las demás instancias que cambiaron los roles personalizados o sus asignaciones (canal Redis
 * {@code renaser:roles-personalizados}; D-269). Es un empujón, no la fuente de verdad: si no llega, cada instancia se
 * pone al día en su recarga de cada 30 s.
 */
public interface AvisarCambioDeRolesPersonalizadosPort {

    void avisar();
}

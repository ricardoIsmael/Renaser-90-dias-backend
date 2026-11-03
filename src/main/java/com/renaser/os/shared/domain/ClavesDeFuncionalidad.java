package com.renaser.os.shared.domain;

/**
 * Las claves del catálogo de funcionalidades que más de un módulo necesita nombrar. {@code habits}, {@code leadership}
 * y {@code plataforma} no pueden importarse entre sí para compartir una constante, y la clave es un contrato con la
 * base y con la app: una clave que ya se usa no se renombra (catálogo de {@code plataforma}).
 */
public final class ClavesDeFuncionalidad {

    /**
     * Si el mentor, la guía y el líder ven el Código Renaser en las fichas de los aprendices (spec 034, D-299). Nace
     * apagada para esos tres roles y la decide Administración; Administración, Alquimista y la propia persona no pasan
     * por ella.
     */
    public static final String RADAR_EN_FICHAS = "radar.fichas";

    private ClavesDeFuncionalidad() {
    }
}

package com.renaser.os.plataforma.domain.model.auditoria;

/** Qué clase de cambio quedó auditado (contrato §4, {@code cambios_auditados.tipo}). */
public enum TipoDeCambio {
    /** Soporte TI cambió un interruptor desde la consola. */
    MANUAL,
    /** El circuito de la IA se abrió o se cerró solo. Es el único sin actor. */
    AUTOMATICO,
    /** Alguien intentó desbloquear la consola con su contraseña (salió bien o no: queda igual). */
    DESBLOQUEO,
    /** Alguien confirmó su contraseña para un cambio crítico (salió bien o no). */
    REAUTENTICACION,
    /**
     * Soporte TI creó, editó, desactivó, borró o reemplazó en bloque un rol personalizado (spec 002, fase 2; V106,
     * D-269). La {@code clave} es la del rol, {@code rp:<id>}.
     */
    ROL_PERSONALIZADO
}

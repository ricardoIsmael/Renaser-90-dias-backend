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
    REAUTENTICACION
}

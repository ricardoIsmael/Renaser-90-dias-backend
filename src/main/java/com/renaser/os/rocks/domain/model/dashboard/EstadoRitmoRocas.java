package com.renaser.os.rocks.domain.model.dashboard;

/**
 * Semáforo de ritmo (Hueco #15, dashboard agregado `GET /rocks`) — cuántos de
 * los últimos 7 días (terminando ayer, sin contar hoy) tuvieron al menos una
 * Roca Diaria completada. Portado literal de {@code rocks/service.ts:885-890}
 * del repo viejo (Next.js) — no estaba documentado en {@code docs/MODULO_ROCKS.md}
 * §1.8/§6 como pendiente por no haberse encontrado; una segunda lectura del
 * archivo viejo lo encontró completo, con umbrales exactos.
 */
public enum EstadoRitmoRocas {
    OK,
    LENTO,
    CRITICO;

    /** {@code diasCompletadosUltimos7 >= 5 -> OK · >= 3 -> LENTO · si no, CRITICO}. */
    public static EstadoRitmoRocas calcular(int diasCompletadosUltimos7) {
        if (diasCompletadosUltimos7 >= 5) {
            return OK;
        }
        if (diasCompletadosUltimos7 >= 3) {
            return LENTO;
        }
        return CRITICO;
    }
}

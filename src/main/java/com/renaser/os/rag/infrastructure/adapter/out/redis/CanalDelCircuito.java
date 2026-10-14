package com.renaser.os.rag.infrastructure.adapter.out.redis;

/** El canal Redis por el que una instancia avisa a las demás que el circuito de la IA cambió (plan.md D7). */
public final class CanalDelCircuito {

    public static final String NOMBRE = "renaser:ia:circuito";

    private CanalDelCircuito() {
    }
}

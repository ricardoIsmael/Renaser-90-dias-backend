package com.renaser.os.rocks.domain.model.rocamaestra;

import com.renaser.os.shared.domain.UserId;

import java.time.Instant;
import java.util.Objects;

/**
 * Roca Maestra: el objetivo de un participante en un eje (Cuerpo/Trabajo/Relaciones) para los
 * 90 dias. Una por (participante, eje) — {@code UNIQUE (participante_id, eje)} en el baseline.
 *
 * <p>Lleva dos cosas: la frase del aprendiz ({@code objetivo}) y, opcionalmente, la parte
 * medible ({@link MetaCuantitativa}). Un objetivo puede ser puramente cualitativo, y entonces
 * {@code meta} es {@code null}; ver el porque en el javadoc de esa clase y en la migracion V35.
 *
 * <p><b>Corregido 2026-09-06 (D-119).</b> Este javadoc decia que {@code rocks} NO crea Rocas
 * Maestras —que las creaba {@code onboarding}, "Ola 5, no construido todavia"— y que una vez
 * creada era <i>"un hecho inmutable"</i>. Las dos afirmaciones dejaron de ser ciertas:
 * <ul>
 *   <li>{@code onboarding} ya existe y <b>nunca las creo</b>. El resultado fue que la tabla
 *       quedo vacia en produccion y la pantalla de Objetivos del Plan mostraba datos escritos
 *       a mano en el frontend, iguales para todos los aprendices.
 *   <li>El aprendiz edita su objetivo <b>durante</b> el programa, no solo al arrancar. Eso no
 *       es onboarding, es gestion de la propia meta, y vive donde vive el agregado.
 * </ul>
 * Por eso la escritura quedo aca, en el modulo dueno de {@code rocas_maestras}. Si algun dia
 * {@code onboarding} necesita sembrarlas, el camino es un puerto en {@code rocks.api}, no un
 * INSERT desde otro modulo.
 *
 * <p>Sigue siendo un {@code record}: es un agregado chico y se reemplaza entero al editarse
 * ({@link #redefinir}), en vez de mutar campos. {@code CLAUDE.md} §5.4.7 permite las dos
 * formas; la inmutable es preferible cuando el agregado es asi de chico.
 */
public record RocaMaestra(RocaMaestraId id, UserId participanteId, EjeObjetivo eje, String objetivo,
                           MetaCuantitativa meta, Instant creadoEn, Instant actualizadoEn) {

    /**
     * Tope de la frase del objetivo. Mismo criterio y mismo valor que
     * {@code RocaDiaria.MAX_TITULO}: la columna es {@code text} y no lo limita, asi que si no
     * se acota aca un cliente puede mandar megabytes.
     */
    public static final int MAX_OBJETIVO = 500;

    public RocaMaestra {
        Objects.requireNonNull(id, "id es obligatorio");
        Objects.requireNonNull(participanteId, "participanteId es obligatorio");
        Objects.requireNonNull(eje, "eje es obligatorio");
        Objects.requireNonNull(creadoEn, "creadoEn es obligatorio");
        Objects.requireNonNull(actualizadoEn, "actualizadoEn es obligatorio");
        if (objetivo == null || objetivo.isBlank()) {
            throw new IllegalArgumentException("objetivo es obligatorio");
        }
        objetivo = objetivo.trim();
        if (objetivo.length() > MAX_OBJETIVO) {
            throw new IllegalArgumentException("El objetivo no puede pasar de " + MAX_OBJETIVO + " caracteres");
        }
    }

    /** Roca Maestra recien definida. {@code meta} puede ser {@code null}: objetivo cualitativo. */
    public static RocaMaestra definir(RocaMaestraId id, UserId participanteId, EjeObjetivo eje, String objetivo,
                                       MetaCuantitativa meta, Instant ahora) {
        return new RocaMaestra(id, participanteId, eje, objetivo, meta, ahora, ahora);
    }

    /**
     * Misma roca, otro contenido. Conserva identidad y fecha de creacion — es la misma meta del
     * mismo eje, corregida, no una nueva.
     */
    public RocaMaestra redefinir(String nuevoObjetivo, MetaCuantitativa nuevaMeta, Instant ahora) {
        return new RocaMaestra(id, participanteId, eje, nuevoObjetivo, nuevaMeta, creadoEn, ahora);
    }

    /** {@code false} = objetivo puramente cualitativo, sin barra de avance que dibujar. */
    public boolean tieneMeta() {
        return meta != null;
    }

    /** Solo para el adaptador de persistencia: reconstruye una roca maestra ya existente. */
    public static RocaMaestra rehydrate(RocaMaestraId id, UserId participanteId, EjeObjetivo eje, String objetivo,
                                         MetaCuantitativa meta, Instant creadoEn, Instant actualizadoEn) {
        return new RocaMaestra(id, participanteId, eje, objetivo, meta, creadoEn, actualizadoEn);
    }
}

package com.renaser.os.rocks.domain.model.rocamaestra;

import com.renaser.os.shared.domain.UserId;

import java.math.BigDecimal;
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
 * <p>Sigue siendo un {@code record}: es un agregado chico y se reemplaza entero al cambiar
 * ({@link #registrarAvance}), en vez de mutar campos. {@code CLAUDE.md} §5.4.7 permite las dos
 * formas; la inmutable es preferible cuando el agregado es asi de chico.
 *
 * <p><b>Una vez definida, queda fija (D-234, decision del dueno del 2026-09-30).</b> El objetivo,
 * la meta, la unidad y el punto de partida nacen del Mapa de Renacimiento y no se cambian; lo que
 * se ajusta son los objetivos semanales y las acciones diarias. Lo unico que se mueve es el
 * avance, que no es cambiar la meta sino registrar cuanto se lleva.
 *
 * > <b>Corregido 2026-09-30 (D-234).</b> Este javadoc decia que el aprendiz <i>"edita su objetivo
 * > durante el programa"</i> y el agregado tenia un {@code redefinir} libre que reemplazaba todo.
 * > Esa era la regla de D-119; el dueno la cambio. Se conserva lo de arriba porque sigue siendo
 * > cierto por que la escritura vive en {@code rocks} y no en {@code onboarding}.
 *
 * <p><b>Fija desde que el Mapa esta ACTIVO, no desde que se definio (D-284, 2026-10-27).</b> "Nace del
 * Mapa" suponia que el Mapa siempre iba primero, y no es asi: el Mapa es opcional y Plan deja definir el
 * objetivo antes. Esa roca provisional dejaba la activacion del Mapa con un 409 {@code ROCA_MAESTRA_FIJA}
 * (issue #20). Mientras el Mapa no esta activo, lo que escribe el Mapa
 * ({@link #reemplazarPorLaDelMapa}) manda y reemplaza a la roca; una vez activo, rige D-234 tal cual
 * ({@link #recibirDefinicion}). Quien decide en cual de los dos casos esta es el servicio, que es el que
 * sabe del Mapa; el agregado solo sabe hacer cada cosa.
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
     * Recibe de nuevo la definicion del eje y decide que hacer con ella (D-234).
     *
     * <ul>
     *   <li>Mismo objetivo, meta, unidad y punto de partida, mismo avance: se devuelve esta misma
     *       roca, sin tocar. Es imprescindible que sea idempotente: la activacion del Mapa
     *       reintenta el {@code PUT} de los tres ejes si algo fallo a mitad.
     *   <li>Lo fijo igual y otro avance: {@link #registrarAvance}.
     *   <li>Cualquier cambio en lo fijo: {@link RocaMaestraFijaException}.
     * </ul>
     *
     * <p>Los numeros se comparan por valor ({@code compareTo}), no con {@code equals}: la base
     * devuelve {@code 30000.00} y el cliente manda {@code 30000}, y eso es la misma meta.
     */
    public RocaMaestra recibirDefinicion(String objetivoPedido, MetaCuantitativa metaPedida, Instant ahora) {
        if (!esLaMismaDefinicion(objetivoPedido, metaPedida)) {
            throw new RocaMaestraFijaException();
        }
        if (metaPedida == null || metaPedida.avance().compareTo(meta.avance()) == 0) {
            return this;
        }
        return registrarAvance(metaPedida.avance(), ahora);
    }

    /**
     * La roca que escribe el Mapa de Renacimiento mientras el Mapa todavia no esta activo (D-284).
     *
     * <p>Lo que el Mapa dice manda: si la persona ya habia definido este eje desde Plan, el objetivo, la
     * meta, la unidad, el punto de partida y el avance se reemplazan por los del Mapa. Se conservan la
     * identidad y la fecha de creacion, para que lo que ya cuelgue de la roca (objetivos del mes,
     * semanales, acciones) siga apuntando a ella.
     *
     * <p>Si lo que llega es lo mismo que ya hay —el reintento de una activacion que fallo a mitad—, se
     * trata como siempre ({@link #recibirDefinicion}): se devuelve esta misma roca sin tocar.
     */
    public RocaMaestra reemplazarPorLaDelMapa(String objetivoPedido, MetaCuantitativa metaPedida, Instant ahora) {
        if (esLaMismaDefinicion(objetivoPedido, metaPedida)) {
            return recibirDefinicion(objetivoPedido, metaPedida, ahora);
        }
        return new RocaMaestra(id, participanteId, eje, objetivoPedido, metaPedida, creadoEn, ahora);
    }

    /**
     * Anota cuanto lleva hoy la persona. No cambia la meta: conserva objetivo, unidad y punto de
     * partida, y la identidad y fecha de creacion de la roca.
     */
    public RocaMaestra registrarAvance(BigDecimal nuevoAvance, Instant ahora) {
        if (!tieneMeta()) {
            throw new IllegalStateException("Un objetivo sin meta medible no tiene avance que registrar");
        }
        return new RocaMaestra(id, participanteId, eje, objetivo, meta.conAvance(nuevoAvance), creadoEn, ahora);
    }

    /** Mismo objetivo (tras {@code trim}), meta, unidad y punto de partida; el avance no cuenta. */
    public boolean esLaMismaDefinicion(String objetivoPedido, MetaCuantitativa metaPedida) {
        return objetivoPedido != null && objetivo.equals(objetivoPedido.trim()) && mismaMedida(metaPedida);
    }

    /** Misma meta, unidad y punto de partida; el avance no cuenta, es lo que si se mueve. */
    private boolean mismaMedida(MetaCuantitativa metaPedida) {
        if (meta == null || metaPedida == null) {
            return meta == metaPedida;
        }
        return meta.objetivo().compareTo(metaPedida.objetivo()) == 0
                && meta.unidad().equals(metaPedida.unidad())
                && mismoNumero(meta.lineaBase(), metaPedida.lineaBase());
    }

    private static boolean mismoNumero(BigDecimal a, BigDecimal b) {
        return a == null || b == null ? a == b : a.compareTo(b) == 0;
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

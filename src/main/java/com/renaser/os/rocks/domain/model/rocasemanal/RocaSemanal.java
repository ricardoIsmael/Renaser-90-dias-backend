package com.renaser.os.rocks.domain.model.rocasemanal;

import com.renaser.os.rocks.domain.model.rocamaestra.RocaMaestraId;
import com.renaser.os.shared.domain.Clock;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.experimental.Accessors;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Roca Semanal: el plan de la semana para un eje (Cuerpo/Trabajo/Relaciones),
 * con exactamente 3 acciones críticas. Se cierra al final de la semana con la
 * autoevaluación de revisión (W-04).
 */
@Getter
@Accessors(fluent = true)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@EqualsAndHashCode(of = "id")
public final class RocaSemanal {

    private static final int MAX_TEXTO = 1000;
    private static final int MAX_TITULO = 500;

    private final RocaSemanalId id;
    private final RocaMaestraId rocaMaestraId;
    private final int numeroSemana;
    private String titulo;
    private List<AccionCritica> acciones;
    private String obstaculo;
    private String contingencia;
    private Integer autoevaluacionInicio;
    private Integer autoevaluacionFin;
    private String bloqueoPrincipal;
    private String correccion;
    private final Instant creadoEn;
    private Instant actualizadoEn;

    /** Planifica una nueva Roca Semanal, con sus 3 acciones críticas (Planning Semanal, W-02). */
    public static RocaSemanal planificar(RocaMaestraId rocaMaestraId, int numeroSemana, String titulo,
                                          List<AccionCritica> acciones, String obstaculo, String contingencia,
                                          Integer autoevaluacionInicio, Clock clock) {
        Objects.requireNonNull(rocaMaestraId, "rocaMaestraId es obligatorio");
        requireNumeroSemanaValido(numeroSemana);
        requireAccionesValidas(acciones);
        Instant ahora = clock.now();
        return new RocaSemanal(RocaSemanalId.newId(), rocaMaestraId, numeroSemana, requireTitulo(titulo), acciones,
                requireTexto(obstaculo, "obstaculo"), requireTexto(contingencia, "contingencia"),
                requireEscala(autoevaluacionInicio, "autoevaluacionInicio"), null, null, null, ahora, ahora);
    }

    /** Solo para el adaptador de persistencia: reconstruye una roca semanal ya existente. */
    public static RocaSemanal rehydrate(RocaSemanalId id, RocaMaestraId rocaMaestraId, int numeroSemana,
                                         String titulo, List<AccionCritica> acciones, String obstaculo,
                                         String contingencia, Integer autoevaluacionInicio, Integer autoevaluacionFin,
                                         String bloqueoPrincipal, String correccion, Instant creadoEn,
                                         Instant actualizadoEn) {
        return new RocaSemanal(id, rocaMaestraId, numeroSemana, titulo, acciones, obstaculo, contingencia,
                autoevaluacionInicio, autoevaluacionFin, bloqueoPrincipal, correccion, creadoEn, actualizadoEn);
    }

    /**
     * Actualiza los campos de planificación (W-03). Parámetro {@code null} =
     * "no se toca" (PATCH parcial, igual que `UpdateWeeklyRockInput` del repo
     * viejo) — la ventana de edición la valida el caso de uso ANTES de llamar
     * a este método (necesita la zona horaria del participante, que el dominio
     * no conoce).
     */
    public void actualizarPlanificacion(String titulo, List<AccionCritica> acciones, String obstaculo,
                                         String contingencia, Integer autoevaluacionInicio, Clock clock) {
        if (titulo != null) {
            this.titulo = requireTitulo(titulo);
        }
        if (acciones != null) {
            requireAccionesValidas(acciones);
            this.acciones = acciones;
        }
        if (obstaculo != null) {
            this.obstaculo = requireTexto(obstaculo, "obstaculo");
        }
        if (contingencia != null) {
            this.contingencia = requireTexto(contingencia, "contingencia");
        }
        if (autoevaluacionInicio != null) {
            this.autoevaluacionInicio = requireEscala(autoevaluacionInicio, "autoevaluacionInicio");
        }
        this.actualizadoEn = clock.now();
    }

    /**
     * Cierre de semana (W-04): autoevaluación final + bloqueo + corrección.
     * Idempotente — sobreescribe una revisión anterior sin restricción de
     * ventana (mismo comportamiento que `reviewWeeklyRock` del repo viejo).
     */
    public void registrarRevision(int autoevaluacionFin, String bloqueoPrincipal, String correccion, Clock clock) {
        this.autoevaluacionFin = requireEscala(autoevaluacionFin, "autoevaluacionFin");
        this.bloqueoPrincipal = requireNotBlank(bloqueoPrincipal, "bloqueoPrincipal");
        this.correccion = requireNotBlank(correccion, "correccion");
        this.actualizadoEn = clock.now();
    }

    private static void requireNumeroSemanaValido(int numeroSemana) {
        if (numeroSemana < 1 || numeroSemana > 13) {
            throw new IllegalArgumentException("numeroSemana debe estar entre 1 y 13: " + numeroSemana);
        }
    }

    private static void requireAccionesValidas(List<AccionCritica> acciones) {
        if (acciones == null || acciones.size() != 3) {
            throw new IllegalArgumentException("una roca semanal exige exactamente 3 acciones criticas");
        }
        Set<Integer> ordenes = acciones.stream().map(AccionCritica::orden).collect(Collectors.toSet());
        if (!ordenes.equals(Set.of(1, 2, 3))) {
            throw new IllegalArgumentException("las acciones criticas deben tener orden 1, 2 y 3, sin repetir");
        }
    }

    private static String requireTitulo(String titulo) {
        String limpio = requireNotBlank(titulo, "titulo");
        if (limpio.length() > MAX_TITULO) {
            throw new IllegalArgumentException("titulo supera " + MAX_TITULO + " caracteres");
        }
        return limpio;
    }

    private static String requireTexto(String valor, String campo) {
        if (valor == null) {
            return null;
        }
        String limpio = valor.trim();
        if (limpio.length() > MAX_TEXTO) {
            throw new IllegalArgumentException(campo + " supera " + MAX_TEXTO + " caracteres");
        }
        return limpio.isBlank() ? null : limpio;
    }

    private static Integer requireEscala(Integer valor, String campo) {
        if (valor == null) {
            return null;
        }
        if (valor < 1 || valor > 10) {
            throw new IllegalArgumentException(campo + " debe estar entre 1 y 10: " + valor);
        }
        return valor;
    }

    private static String requireNotBlank(String value, String campo) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(campo + " es obligatorio");
        }
        return value.trim();
    }

    @Override
    public String toString() {
        return "RocaSemanal[" + id + ", semana " + numeroSemana + ", " + rocaMaestraId + "]";
    }
}

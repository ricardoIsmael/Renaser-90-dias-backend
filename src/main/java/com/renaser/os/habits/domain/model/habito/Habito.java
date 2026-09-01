package com.renaser.os.habits.domain.model.habito;

import com.renaser.os.shared.domain.UserId;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.experimental.Accessors;

import java.time.Instant;
import java.util.Objects;

/**
 * Catalogo (SISTEMA) o habito propio de un participante (PERSONAL) — unifica
 * `habits`+`personal_habits` del repo viejo (P-12, tabla `habitos`).
 *
 * <p>Simplificacion deliberada de esta primera version (ver docs/MODULO_HABITS.md
 * "que quedo simplificado"): no incluye el escalonamiento de horarios
 * (`habitStaggering.ts`/`staggerService.ts`, ~1470 lineas del repo viejo) ni el
 * renombre de claves del catalogo (`renameableKeys.ts`) como comportamiento
 * embebido en este agregado — esas reglas viven, simplificadas, en
 * `desbloqueos_habito`/`renombres_habito` a traves de los casos de uso
 * correspondientes, sin el algoritmo completo de relleno por lotes.
 */
@Getter
@Accessors(fluent = true)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@EqualsAndHashCode(of = "id")
public final class Habito {

    private final HabitoId id;
    private final AmbitoHabito ambito;
    private final UserId participanteId; // null si ambito == SISTEMA
    private String titulo;
    private String descripcion;
    private final TipoHabito tipo;
    private String categoriaClave;
    private String iconoClave;
    private String claveSistema; // null si ambito == PERSONAL
    private ExigenciaEvidencia exigenciaEvidencia;
    private boolean esOpcional;
    private boolean obligatorioEnIntoxicacion;
    private boolean eleccionDiaSemanal;
    private Integer horasExtraEvidencia; // null = usar el default global (VentanaEntrega)
    private Integer diaLimiteEdicionLibre; // null = usar el default global FREE_SCHEDULE_EDITS_UNTIL_DAY=7 (hueco #12)
    private PlantillaHabitoPersonal plantillaClave; // solo PERSONAL
    private String etiquetaMeta; // solo PERSONAL
    private boolean activo;
    private final Instant creadoEn;
    private Instant actualizadoEn;

    /**
     * El {@code id} entra por parametro, no se genera aca: la identidad viene del puerto
     * {@code IdGenerator} que inyecta el caso de uso. Asi la factoria es referencialmente
     * transparente y un test puede fijar el id que espera, en vez de tener que caer a
     * {@link #rehydrate} para lograrlo.
     */
    public static Habito crearDeSistema(HabitoId id, String titulo, TipoHabito tipo, String categoriaClave,
                                         ExigenciaEvidencia exigenciaEvidencia, Instant ahora) {
        Objects.requireNonNull(id, "id es obligatorio");
        return new Habito(id, AmbitoHabito.SISTEMA, null, requireTitulo(titulo), null, tipo,
                requireCategoria(categoriaClave), null, null, exigenciaEvidencia, false, false, false, null, null,
                null, null, true, ahora, ahora);
    }

    /**
     * Alta desde el panel admin (hueco #11, docs/PLAN_INTEGRACION_FRONTEND.md #11):
     * variante de {@link #crearDeSistema(HabitoId, String, TipoHabito, String, ExigenciaEvidencia, Instant)}
     * que además fija descripcion/opcionalidad desde el alta, en vez de dejarlas en su
     * default y forzar un segundo PATCH. {@code claveSistema} queda null a proposito — el
     * panel admin no crea claves funcionales (esas las siembra la migracion baseline).
     *
     * <p>El {@code id} entra por parametro: lo genera el caso de uso con el puerto
     * {@code IdGenerator} ({@code HabitoAdminService.crear}).
     */
    public static Habito crearDeSistema(HabitoId id, String titulo, TipoHabito tipo, DetallesHabito detalles,
                                         Instant ahora) {
        Objects.requireNonNull(id, "id es obligatorio");
        Objects.requireNonNull(detalles, "detalles es obligatorio");
        Objects.requireNonNull(tipo, "tipo es obligatorio");
        return new Habito(id, AmbitoHabito.SISTEMA, null, requireTitulo(titulo), detalles.descripcion(),
                tipo, requireCategoria(detalles.categoriaClave()), null, null, detalles.exigenciaEvidencia(),
                detalles.esOpcional(), detalles.obligatorioEnIntoxicacion(), false, null, null, null, null, true,
                ahora, ahora);
    }

    /** El {@code id} entra por parametro: lo genera el caso de uso con el puerto {@code IdGenerator}. */
    public static Habito crearPersonal(HabitoId id, UserId participanteId, String titulo, TipoHabito tipo,
                                        String categoriaClave, PlantillaHabitoPersonal plantilla, String etiquetaMeta,
                                        Instant ahora) {
        Objects.requireNonNull(id, "id es obligatorio");
        Objects.requireNonNull(participanteId, "participanteId es obligatorio para un habito PERSONAL");
        return new Habito(id, AmbitoHabito.PERSONAL, participanteId, requireTitulo(titulo), null, tipo,
                requireCategoria(categoriaClave), null, null, ExigenciaEvidencia.OPCIONAL, false, false, false, null,
                null, plantilla, etiquetaMeta, true, ahora, ahora);
    }

    /** Solo para el adaptador de persistencia. */
    public static Habito rehydrate(HabitoId id, AmbitoHabito ambito, UserId participanteId, String titulo,
                                    String descripcion, TipoHabito tipo, String categoriaClave, String iconoClave,
                                    String claveSistema, ExigenciaEvidencia exigenciaEvidencia, boolean esOpcional,
                                    boolean obligatorioEnIntoxicacion, boolean eleccionDiaSemanal,
                                    Integer horasExtraEvidencia, Integer diaLimiteEdicionLibre,
                                    PlantillaHabitoPersonal plantillaClave, String etiquetaMeta, boolean activo,
                                    Instant creadoEn, Instant actualizadoEn) {
        return new Habito(id, ambito, participanteId, titulo, descripcion, tipo, categoriaClave, iconoClave,
                claveSistema, exigenciaEvidencia, esOpcional, obligatorioEnIntoxicacion, eleccionDiaSemanal,
                horasExtraEvidencia, diaLimiteEdicionLibre, plantillaClave, etiquetaMeta, activo, creadoEn,
                actualizadoEn);
    }

    public void renombrar(String nuevoTitulo, Instant ahora) {
        this.titulo = requireTitulo(nuevoTitulo);
        this.actualizadoEn = ahora;
    }

    public void desactivar(Instant ahora) {
        this.activo = false;
        this.actualizadoEn = ahora;
    }

    /** Reactiva un habito de catalogo dado de baja logica (panel admin). */
    public void activar(Instant ahora) {
        this.activo = true;
        this.actualizadoEn = ahora;
    }

    /**
     * Edicion administrativa (panel admin, hueco #11). Deliberadamente NO toca:
     * <ul>
     *   <li>{@code titulo} — tiene su propio metodo ({@link #renombrar}), con su propia
     *       validacion de "obligatorio y recortado".</li>
     *   <li>{@code ambito}/{@code participanteId} — identidad del agregado, no se
     *       reasignan jamas.</li>
     *   <li>{@code claveSistema} — clave funcional estable que {@code SelectorHabito.PorClaveSistema}
     *       usa para resolver politicas (ej. {@code PASTILLA_RENACER}); cambiarla en caliente
     *       deja politicas ya indexadas apuntando a un habito distinto sin que nadie lo note.</li>
     *   <li>{@code tipo} — {@code SelectorHabito.PorTipo} lo usa para reglas estructurales
     *       (ej. BLOQUEO = Santuario) y el flujo de un {@code registro_habito} ya generado
     *       asume el tipo que tenia el habito al crearse (Santuario vs completar directo).
     *       Reclasificar un CHECKBOX en BLOQUEO (o viceversa) despues de que ya existan
     *       tracks es un cambio de regla de negocio no confirmado — no se implementa.</li>
     * </ul>
     */
    public void actualizarDetalles(DetallesHabito detalles, Instant ahora) {
        Objects.requireNonNull(detalles, "detalles es obligatorio");
        this.descripcion = detalles.descripcion();
        this.categoriaClave = requireCategoria(detalles.categoriaClave());
        this.exigenciaEvidencia = detalles.exigenciaEvidencia();
        this.esOpcional = detalles.esOpcional();
        this.obligatorioEnIntoxicacion = detalles.obligatorioEnIntoxicacion();
        this.actualizadoEn = ahora;
    }

    public boolean esDeSistema() {
        return ambito == AmbitoHabito.SISTEMA;
    }

    public boolean esBloqueo() {
        return tipo == TipoHabito.BLOQUEO;
    }

    private static String requireTitulo(String titulo) {
        if (titulo == null || titulo.isBlank()) {
            throw new IllegalArgumentException("El titulo del habito es obligatorio");
        }
        return titulo.trim();
    }

    private static String requireCategoria(String categoriaClave) {
        if (categoriaClave == null || categoriaClave.isBlank()) {
            throw new IllegalArgumentException("La categoria del habito es obligatoria");
        }
        return categoriaClave.trim().toUpperCase();
    }

    @Override
    public String toString() {
        return "Habito[" + id + ", " + ambito + ", " + tipo + "]";
    }
}

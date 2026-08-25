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
    private PlantillaHabitoPersonal plantillaClave; // solo PERSONAL
    private String etiquetaMeta; // solo PERSONAL
    private boolean activo;
    private final Instant creadoEn;
    private Instant actualizadoEn;

    public static Habito crearDeSistema(String titulo, TipoHabito tipo, String categoriaClave,
                                         ExigenciaEvidencia exigenciaEvidencia, Instant ahora) {
        return new Habito(HabitoId.newId(), AmbitoHabito.SISTEMA, null, requireTitulo(titulo), null, tipo,
                requireCategoria(categoriaClave), null, null, exigenciaEvidencia, false, false, false, null, null,
                null, true, ahora, ahora);
    }

    public static Habito crearPersonal(UserId participanteId, String titulo, TipoHabito tipo, String categoriaClave,
                                        PlantillaHabitoPersonal plantilla, String etiquetaMeta, Instant ahora) {
        Objects.requireNonNull(participanteId, "participanteId es obligatorio para un habito PERSONAL");
        return new Habito(HabitoId.newId(), AmbitoHabito.PERSONAL, participanteId, requireTitulo(titulo), null, tipo,
                requireCategoria(categoriaClave), null, null, ExigenciaEvidencia.OPCIONAL, false, false, false, null,
                plantilla, etiquetaMeta, true, ahora, ahora);
    }

    /** Solo para el adaptador de persistencia. */
    public static Habito rehydrate(HabitoId id, AmbitoHabito ambito, UserId participanteId, String titulo,
                                    String descripcion, TipoHabito tipo, String categoriaClave, String iconoClave,
                                    String claveSistema, ExigenciaEvidencia exigenciaEvidencia, boolean esOpcional,
                                    boolean obligatorioEnIntoxicacion, boolean eleccionDiaSemanal,
                                    Integer horasExtraEvidencia, PlantillaHabitoPersonal plantillaClave,
                                    String etiquetaMeta, boolean activo, Instant creadoEn, Instant actualizadoEn) {
        return new Habito(id, ambito, participanteId, titulo, descripcion, tipo, categoriaClave, iconoClave,
                claveSistema, exigenciaEvidencia, esOpcional, obligatorioEnIntoxicacion, eleccionDiaSemanal,
                horasExtraEvidencia, plantillaClave, etiquetaMeta, activo, creadoEn, actualizadoEn);
    }

    public void renombrar(String nuevoTitulo, Instant ahora) {
        this.titulo = requireTitulo(nuevoTitulo);
        this.actualizadoEn = ahora;
    }

    public void desactivar(Instant ahora) {
        this.activo = false;
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

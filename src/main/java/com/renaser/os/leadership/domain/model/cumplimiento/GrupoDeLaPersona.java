package com.renaser.os.leadership.domain.model.cumplimiento;

import com.renaser.os.shared.domain.UserId;

import java.util.Collection;
import java.util.Comparator;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * El grupo desde el que se mira a una persona (spec 021): el que ya traía la ficha por grupo, o el que le toca a quien
 * el líder abre sin pasar por un grupo. Una persona puede estar en dos grupos a la vez (D-139) o en ninguno.
 *
 * <p>Sirve para tres cosas y nada más: dar el {@code grupoId} que viaja en la respuesta y queda en el registro de la
 * consulta, decidir a qué mentor se le avisa («Avisar a su mentor») y nombrar el grupo en la lista.
 *
 * @param mentorId  su mentor vigente; null si no tiene o si es la recepción
 * @param recepcion si es el grupo de bienvenida (lo atienden guías, no un mentor)
 */
public record GrupoDeLaPersona(UUID grupoId, String nombre, UserId mentorId, boolean recepcion) {

    public GrupoDeLaPersona {
        Objects.requireNonNull(grupoId, "grupoId es obligatorio");
    }

    /** True si hay a quién avisar: un grupo regular con mentor vigente. */
    public boolean conMentor() {
        return !recepcion && mentorId != null;
    }

    /**
     * Primero el grupo regular con mentor (el único al que se le puede avisar), después el regular sin mentor y al final
     * la recepción; a igual categoría, el principal de la persona, y si no, el de nombre menor. Así la misma persona cae
     * siempre en el mismo grupo en la lista y en su ficha.
     *
     * @param principal el grupo principal de la persona ({@code participantes_programa.celula_id}); null si no tiene
     * @return el grupo con que se la mira; vacío si no está en ninguno
     */
    public static Optional<GrupoDeLaPersona> elegir(Collection<GrupoDeLaPersona> candidatos, UUID principal) {
        Comparator<GrupoDeLaPersona> orden = Comparator
                .comparingInt(GrupoDeLaPersona::categoria)
                .thenComparing(grupo -> !grupo.grupoId().equals(principal))
                .thenComparing(grupo -> grupo.nombre() == null ? "" : grupo.nombre(), String.CASE_INSENSITIVE_ORDER)
                .thenComparing(GrupoDeLaPersona::grupoId);
        return candidatos.stream().min(orden);
    }

    private int categoria() {
        if (recepcion) {
            return 2;
        }
        return mentorId != null ? 0 : 1;
    }
}

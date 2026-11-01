package com.renaser.os.users.api;

import com.renaser.os.shared.domain.AlcanceDeFormacion;
import com.renaser.os.shared.domain.Formacion;
import com.renaser.os.shared.domain.UserId;

import java.util.Collection;
import java.util.Optional;
import java.util.Set;

/**
 * <b>El punto único de «la formación de la persona»</b> (spec 022, D-295). Toda lectura que deba aislar por formación (personas, grupos, chats,
 * hábitos, Muro, ranking…) pregunta aquí; nadie reimplementa la regla ni lee {@code participantes_programa.formacion} por su cuenta.
 *
 * <p>Dos preguntas distintas:
 * <ul>
 *   <li>{@link #de(UserId)}: <b>qué formaciones ve quien actúa</b>. Se pasa al filtro SQL
 *       ({@code shared.infrastructure.persistence.FiltroDeFormacionSql}).</li>
 *   <li>{@link #formacionDe(UserId)}: <b>a cuál pertenece una persona</b> (la de su participación; la activa si es personal).</li>
 * </ul>
 */
public interface AlcanceDeFormacionFinder {

    /**
     * Las formaciones que ve {@code actor}. Una persona que no existe, no ve nada ({@link AlcanceDeFormacion#ninguna()}).
     * Administración, Alquimista y Soporte TI ven {@link AlcanceDeFormacion#todas()}; el personal con varias formaciones, solo la activa.
     */
    AlcanceDeFormacion de(UserId actor);

    /** La formación de la persona; vacío si es de las que ven todas y no tienen una, o si no existe. */
    Optional<Formacion> formacionDe(UserId persona);

    /**
     * Todas las formaciones a las que PERTENECE la persona: la que cursa el aprendiz; las asignadas del personal (no solo la activa); la de su programa
     * propio si Administración lo hace. Vacío si no pertenece a ninguna. Es la pregunta de «¿puede ser del grupo de esta formación?».
     */
    Set<Formacion> formacionesDe(UserId persona);

    /**
     * De estas personas, las que pertenecen a alguna formación del alcance. Una consulta para todas (sin N+1): es el filtro de las listas que se arman
     * con ids (directorio del chat, candidatos de un grupo, mentores del líder…).
     */
    Set<UserId> dentroDelAlcance(Collection<UserId> personas, AlcanceDeFormacion alcance);

    /**
     * Para «pedir por identificador»: si {@code objetivo} no es de ninguna formación que {@code actor} ve, falla como si no existiera. «Es de» una
     * formación: el aprendiz, la que cursa; el personal, cualquiera de las que tiene asignadas (aunque no sea su activa). Quien no pertenece a ninguna
     * (Administración, sin programa) no es visible para quien no ve todas. Si {@code actor} ve las tres, no se filtra.
     *
     * @throws com.renaser.os.shared.domain.ObjetoDeOtraFormacionException (404) si es de otra formación
     */
    void exigirQueLaVea(UserId actor, UserId objetivo);

    /** Igual que {@link #exigirQueLaVea(UserId, UserId)} pero para algo (un grupo, una publicación…) que ya se sabe de qué formación es. */
    void exigirQueLaVea(UserId actor, Formacion formacionDelObjeto);
}

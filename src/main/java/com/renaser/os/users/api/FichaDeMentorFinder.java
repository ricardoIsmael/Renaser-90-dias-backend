package com.renaser.os.users.api;

import com.renaser.os.shared.domain.UserId;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * El cuerpo de mentores tal como lo ve la gestion del Lider de Mentores (SDD 002, RL-04/RL-06; D-241):
 * quienes son, en que estado esta su cuenta, y lo que dice su perfil de mentor.
 *
 * <p><b>Por que no {@link PerfilMentorFinder}.</b> Aquel expone solo la especialidad, para el selector
 * de mentor de {@code community}; ampliar su record obligaba a tocar a quien ya lo usa. Este es de
 * lectura y no lleva email ni datos de contacto.
 *
 * <p>Nivel y estado operativo viajan como texto ({@code N0}..{@code N3}, {@code GREEN}/{@code YELLOW}/
 * {@code RED}) porque los enums son del dominio de {@code users}, que nadie de afuera puede importar.
 * {@code null} = el usuario no tiene perfil de mentor todavia, no un valor por defecto.
 */
public interface FichaDeMentorFinder {

    /** Usuarios con rol MENTOR y cuenta ACTIVA, ordenados por nombre. */
    List<FichaDeMentor> mentoresActivos();

    /** Ese usuario si su rol es MENTOR, en cualquier estado de cuenta; vacio si no existe o no es mentor. */
    Optional<FichaDeMentor> mentor(UserId usuarioId);

    record FichaDeMentor(UserId id, String nombreCompleto, String avatarUrl, UserStatus estado,
                         PerfilDeMentor perfil) {
    }

    /** @param desde cuando se creo su perfil de mentor */
    record PerfilDeMentor(String nivel, String estadoOperativo, Instant desde) {
    }
}

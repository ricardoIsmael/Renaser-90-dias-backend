package com.renaser.os.users.infrastructure.adapter.out.persistence.user;

import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.domain.model.user.Email;
import com.renaser.os.users.domain.model.user.User;
import com.renaser.os.users.api.UserRole;
import com.renaser.os.users.api.UserStatus;
import org.springframework.stereotype.Component;

/**
 * Traduccion explicita a mano, NO MapStruct (D-5 anotado: MapStruct es para mapeo plano
 * campo-a-campo; aca cada campo interesante necesita traduccion — UUID<->UserId,
 * String<->Email, y 2 enums en idiomas distintos — asi que la claridad de un mapper a
 * mano gana, y evita el riesgo ya documentado de MapStruct 1.6.3/1.7.0-beta en JDK 25).
 */
@Component
class UserPersistenceMapper {

    User toDomain(UserJpaEntity entity) {
        return User.rehydrate(
                UserId.of(entity.getId()),
                new Email(entity.getEmail()),
                toDomainRole(entity.getRol()),
                toDomainStatus(entity.getEstado()),
                entity.getNombreCompleto(),
                entity.getAvatarUrl(),
                entity.getBio(),
                entity.getDepartamento(),
                entity.getUltimaActividadEn(),
                entity.getBajaSolicitadaEn());
    }

    UserJpaEntity toEntity(User user) {
        return new UserJpaEntity(
                user.id().value(),
                user.email().value(),
                user.fullName(),
                user.avatarUrl(),
                user.bio(),
                user.department(),
                toJpaRole(user.role()),
                toJpaStatus(user.status()),
                user.lastActiveAt(),
                user.bajaSolicitadaEn());
    }

    /** Version no-privada de {@link #toJpaRole}, para que el adaptador traduzca filtros de busqueda. */
    RolUsuarioJpa toJpaRolePublic(UserRole role) {
        return toJpaRole(role);
    }

    /** Version no-privada de {@link #toJpaStatus}, para que el adaptador traduzca filtros de busqueda. */
    EstadoUsuarioJpa toJpaStatusPublic(UserStatus status) {
        return toJpaStatus(status);
    }

    private RolUsuarioJpa toJpaRole(UserRole role) {
        return switch (role) {
            case TRAINEE -> RolUsuarioJpa.APRENDIZ;
            case MENTOR -> RolUsuarioJpa.MENTOR;
            case MENTOR_LEAD -> RolUsuarioJpa.LIDER_MENTORES;
            case ADMIN -> RolUsuarioJpa.ADMIN;
            case ALCHEMIST -> RolUsuarioJpa.ALQUIMISTA;
        };
    }

    private UserRole toDomainRole(RolUsuarioJpa jpa) {
        return switch (jpa) {
            case APRENDIZ -> UserRole.TRAINEE;
            case MENTOR -> UserRole.MENTOR;
            case LIDER_MENTORES -> UserRole.MENTOR_LEAD;
            case ADMIN -> UserRole.ADMIN;
            case ALQUIMISTA -> UserRole.ALCHEMIST;
        };
    }

    // R-3 resuelta (2026-08-27): INACTIVO ya tiene significado en el dominio — "registrado, sin
    // aprobar todavia" — asi que el mapeo es 1:1 en ambos sentidos y ya no hay estado de la BD
    // que el dominio no sepa leer.

    private EstadoUsuarioJpa toJpaStatus(UserStatus status) {
        return switch (status) {
            case ACTIVE -> EstadoUsuarioJpa.ACTIVO;
            case INACTIVE -> EstadoUsuarioJpa.INACTIVO;
            case SUSPENDED -> EstadoUsuarioJpa.SUSPENDIDO;
        };
    }

    private UserStatus toDomainStatus(EstadoUsuarioJpa jpa) {
        return switch (jpa) {
            case ACTIVO -> UserStatus.ACTIVE;
            case INACTIVO -> UserStatus.INACTIVE;
            case SUSPENDIDO -> UserStatus.SUSPENDED;
        };
    }
}

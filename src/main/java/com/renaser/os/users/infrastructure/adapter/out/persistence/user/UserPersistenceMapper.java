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
                entity.getUltimaActividadEn());
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
                user.lastActiveAt());
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

    private EstadoUsuarioJpa toJpaStatus(UserStatus status) {
        return switch (status) {
            case ACTIVE -> EstadoUsuarioJpa.ACTIVO;
            case SUSPENDED -> EstadoUsuarioJpa.SUSPENDIDO;
        };
    }

    private UserStatus toDomainStatus(EstadoUsuarioJpa jpa) {
        return switch (jpa) {
            case ACTIVO -> UserStatus.ACTIVE;
            case SUSPENDIDO -> UserStatus.SUSPENDED;
            case INACTIVO -> throw new IllegalStateException(
                    "estado_usuario.INACTIVO no tiene equivalente en UserStatus todavia (pregunta abierta R-3)");
        };
    }
}

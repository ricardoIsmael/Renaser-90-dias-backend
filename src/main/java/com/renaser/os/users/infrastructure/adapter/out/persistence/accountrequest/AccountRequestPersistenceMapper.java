package com.renaser.os.users.infrastructure.adapter.out.persistence.accountrequest;

import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.domain.model.accountrequest.AccountRequest;
import com.renaser.os.users.domain.model.accountrequest.AccountRequestId;
import com.renaser.os.users.domain.model.accountrequest.AccountRequestStatus;
import com.renaser.os.users.domain.model.user.Email;
import org.springframework.stereotype.Component;

@Component
class AccountRequestPersistenceMapper {

    AccountRequest toDomain(AccountRequestJpaEntity e) {
        return AccountRequest.rehydrate(
                AccountRequestId.of(e.getId()),
                UserId.of(e.getSupabaseUserId()),
                new Email(e.getEmail()),
                e.getNombreCompleto(),
                e.getTelefono(),
                e.getCiudad(),
                toDomainStatus(e.getEstado()),
                e.getMotivoRechazo(),
                e.getRevisadaPor() == null ? null : UserId.of(e.getRevisadaPor()),
                e.getRevisadaEn(),
                e.getUsuarioCreadoId() == null ? null : UserId.of(e.getUsuarioCreadoId()),
                e.getIpSolicitud(),
                e.getCreadoEn(),
                e.getActualizadoEn());
    }

    AccountRequestJpaEntity toEntity(AccountRequest r) {
        return new AccountRequestJpaEntity(
                r.id().value(),
                r.supabaseUserId().value(),
                r.email().value(),
                r.fullName(),
                r.phone(),
                r.city(),
                toJpaStatus(r.status()),
                r.rejectionReason(),
                r.reviewedBy() == null ? null : r.reviewedBy().value(),
                r.reviewedAt(),
                r.createdUserId() == null ? null : r.createdUserId().value(),
                r.requestIp(),
                r.createdAt(),
                r.updatedAt());
    }

    private EstadoSolicitudJpa toJpaStatus(AccountRequestStatus status) {
        return switch (status) {
            case PENDING -> EstadoSolicitudJpa.PENDIENTE;
            case APPROVED -> EstadoSolicitudJpa.APROBADA;
            case REJECTED -> EstadoSolicitudJpa.RECHAZADA;
        };
    }

    private AccountRequestStatus toDomainStatus(EstadoSolicitudJpa jpa) {
        return switch (jpa) {
            case PENDIENTE -> AccountRequestStatus.PENDING;
            case APROBADA -> AccountRequestStatus.APPROVED;
            case RECHAZADA -> AccountRequestStatus.REJECTED;
        };
    }
}

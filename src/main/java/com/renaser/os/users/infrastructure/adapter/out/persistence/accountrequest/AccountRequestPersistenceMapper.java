package com.renaser.os.users.infrastructure.adapter.out.persistence.accountrequest;

import com.renaser.os.shared.domain.Formacion;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.UserRole;
import com.renaser.os.users.domain.model.accountrequest.AccountRequest;
import com.renaser.os.users.domain.model.accountrequest.AccountRequestId;
import com.renaser.os.users.domain.model.accountrequest.AccountRequestStatus;
import com.renaser.os.users.domain.model.accountrequest.EleccionDeIngreso;
import com.renaser.os.users.domain.model.accountrequest.OrigenSocial;
import com.renaser.os.users.domain.model.identidadexterna.ProveedorIdentidad;
import com.renaser.os.users.domain.model.user.Email;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Set;

import static java.util.stream.Collectors.toSet;

@Component
class AccountRequestPersistenceMapper {

    AccountRequest toDomain(AccountRequestJpaEntity e) {
        return AccountRequest.rehydrate(
                AccountRequestId.of(e.getId()),
                UserId.of(e.getUsuarioId()),
                new Email(e.getEmail()),
                e.getNombreCompleto(),
                e.getTelefono(),
                e.getCiudad(),
                toDomainOrigenSocial(e),
                toDomainStatus(e.getEstado()),
                e.getMotivoRechazo(),
                e.getRevisadaPor() == null ? null : UserId.of(e.getRevisadaPor()),
                e.getRevisadaEn(),
                e.getUsuarioCreadoId() == null ? null : UserId.of(e.getUsuarioCreadoId()),
                e.getIpSolicitud(),
                e.getCreadoEn(),
                e.getActualizadoEn(),
                toDomainEleccion(e));
    }

    /** Sin formaciones guardadas la solicitud no está aceptada (o es de antes: V140 ya les puso la Escuela). */
    private EleccionDeIngreso toDomainEleccion(AccountRequestJpaEntity e) {
        if (e.getFormacionesElegidas() == null) {
            return null;
        }
        Set<Formacion> formaciones = Arrays.stream(e.getFormacionesElegidas()).map(Formacion::desde).collect(toSet());
        UserRole rol = e.getRolElegido() == null ? null : UserRole.valueOf(e.getRolElegido());
        Integer duracion = e.getDuracionElegidaDias() == null ? null : e.getDuracionElegidaDias().intValue();
        return new EleccionDeIngreso(rol, formaciones, duracion, Boolean.TRUE.equals(e.getConDiagnostico()));
    }

    AccountRequestJpaEntity toEntity(AccountRequest r) {
        return new AccountRequestJpaEntity(
                r.id().value(),
                r.usuarioId().value(),
                r.email().value(),
                r.fullName(),
                r.phone(),
                r.city(),
                r.origenSocial() == null ? null : r.origenSocial().proveedor().name(),
                r.origenSocial() == null ? null : r.origenSocial().sujetoProveedor(),
                toJpaStatus(r.status()),
                r.rejectionReason(),
                r.reviewedBy() == null ? null : r.reviewedBy().value(),
                r.reviewedAt(),
                r.createdUserId() == null ? null : r.createdUserId().value(),
                r.requestIp(),
                r.createdAt(),
                r.updatedAt(),
                r.eleccion() == null ? null : r.eleccion().formaciones().stream().map(Formacion::codigo).toArray(String[]::new),
                r.eleccion() == null || r.eleccion().duracionElegidaDias() == null ? null : r.eleccion().duracionElegidaDias().shortValue(),
                r.eleccion() == null || r.eleccion().rol() == null ? null : r.eleccion().rol().name(),
                r.eleccion() == null ? null : r.eleccion().conDiagnostico());
    }

    /**
     * Las dos columnas viajan juntas o ninguna (CHECK de la migracion V12), asi que alcanza
     * con mirar una: si el proveedor es NULL, esta solicitud entro por el formulario.
     */
    private OrigenSocial toDomainOrigenSocial(AccountRequestJpaEntity e) {
        return e.getProveedor() == null ? null
                : new OrigenSocial(ProveedorIdentidad.valueOf(e.getProveedor()), e.getSujetoProveedor());
    }

    /** Version no-privada de {@link #toJpaStatus}, para que el adaptador traduzca filtros de busqueda. */
    EstadoSolicitudJpa toJpaStatusPublic(AccountRequestStatus status) {
        return toJpaStatus(status);
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

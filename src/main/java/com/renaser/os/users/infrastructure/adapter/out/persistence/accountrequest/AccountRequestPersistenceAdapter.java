package com.renaser.os.users.infrastructure.adapter.out.persistence.accountrequest;

import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.application.ports.out.accountrequest.DeleteAccountRequestPort;
import com.renaser.os.users.application.ports.out.accountrequest.LoadAccountRequestPort;
import com.renaser.os.users.application.ports.out.accountrequest.SaveAccountRequestPort;
import com.renaser.os.users.domain.model.accountrequest.AccountRequest;
import com.renaser.os.users.domain.model.accountrequest.AccountRequestId;
import com.renaser.os.users.domain.model.accountrequest.AccountRequestStatus;
import com.renaser.os.users.domain.model.accountrequest.OrigenSocial;
import com.renaser.os.users.domain.model.user.Email;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Component
class AccountRequestPersistenceAdapter implements LoadAccountRequestPort, SaveAccountRequestPort,
        DeleteAccountRequestPort {
    /**
     * Tope duro de filas por pagina (2026-09-18).
     *
     * <p>`page` y `size` viajaban crudos desde la query hasta `PageRequest.of`. Un solo
     * `?size=1000000` devolvia el padron entero —nombre, correo, rol, estado— en una sola
     * respuesta: eso no es una consulta paginada, es una exportacion de datos personales. El chat y
     * el muro ya acotaban lo suyo (`MiembroService.LIMITE_MAXIMO`); estas dos lecturas, que son las
     * que mas PII devuelven, no.
     *
     * <p>Se acota en el ADAPTADOR y no en el controller a proposito: es el ultimo punto por el que
     * pasan todos los llamadores de esta consulta, asi que ningun camino nuevo puede saltearselo.
     */
    private static final int TAMANO_PAGINA_MAXIMO = 200;
    private static final int TAMANO_PAGINA_POR_DEFECTO = 20;


    private final SpringDataAccountRequestRepository repository;
    private final AccountRequestPersistenceMapper mapper;

    AccountRequestPersistenceAdapter(SpringDataAccountRequestRepository repository,
                                      AccountRequestPersistenceMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Override
    public Optional<AccountRequest> byId(AccountRequestId id) {
        return repository.findById(id.value()).map(mapper::toDomain);
    }

    @Override
    public boolean existePorEmail(Email email) {
        // Email ya normaliza a minusculas en su constructor, que es como se guarda la columna.
        return repository.existsByEmail(email.value());
    }

    @Override
    public Optional<AccountRequest> porOrigenSocial(OrigenSocial origenSocial) {
        return repository.findByProveedorAndSujetoProveedor(origenSocial.proveedor().name(),
                origenSocial.sujetoProveedor()).map(mapper::toDomain);
    }

    @Override
    public long countSubmittedFromIpSince(String requestIp, Instant since) {
        return repository.countByIpSolicitudAndCreadoEnAfter(requestIp, since);
    }

    /** Panel admin de solicitudes de cuenta (gap #9). `statusFilter == null` = cualquier estado. */
    @Override
    public List<AccountRequest> pagina(AccountRequestStatus statusFilter, int page, int size) {
        var pageable = PageRequest.of(page, tamanoAcotado(size), Sort.by(Sort.Direction.DESC, "creadoEn"));
        var entidades = statusFilter == null
                ? repository.findAll(pageable).getContent()
                : repository.findByEstado(mapper.toJpaStatusPublic(statusFilter), pageable);
        return entidades.stream().map(mapper::toDomain).toList();
    }

    @Override
    public long contar(AccountRequestStatus statusFilter) {
        return statusFilter == null ? repository.count() : repository.countByEstado(mapper.toJpaStatusPublic(statusFilter));
    }

    @Override
    public boolean deleteById(AccountRequestId id) {
        if (!repository.existsById(id.value())) {
            return false;
        }
        repository.deleteById(id.value());
        return true;
    }

    /** Sin {@code existsById} previo, a diferencia de {@link #deleteById}: la consulta derivada
     * de Spring Data ya es idempotente -- si no hay fila para ese usuario, no borra nada y no
     * lanza (el {@code EmptyResultDataAccessException} lo tira {@code deleteById(id)}, que es
     * otro metodo). */
    @Override
    public void borrarPorUsuario(UserId usuarioId) {
        repository.deleteByUsuarioId(usuarioId.value());
    }

    @Override
    public AccountRequest save(AccountRequest accountRequest) {
        var saved = repository.save(mapper.toEntity(accountRequest));
        return mapper.toDomain(saved);
    }

    private static int tamanoAcotado(int size) {
        return size <= 0 ? TAMANO_PAGINA_POR_DEFECTO : Math.min(size, TAMANO_PAGINA_MAXIMO);
    }
}

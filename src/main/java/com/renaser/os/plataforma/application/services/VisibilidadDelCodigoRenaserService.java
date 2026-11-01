package com.renaser.os.plataforma.application.services;

import com.renaser.os.plataforma.application.ports.in.CambiarVisibilidadDelCodigoRenaserUseCase;
import com.renaser.os.plataforma.application.ports.in.ConsultarVisibilidadDelCodigoRenaserUseCase;
import com.renaser.os.plataforma.application.ports.in.VisibilidadDelCodigoRenaser;
import com.renaser.os.plataforma.application.ports.in.VisibilidadDelCodigoRenaser.UltimoCambio;
import com.renaser.os.plataforma.application.ports.out.CargarEstadosPort;
import com.renaser.os.plataforma.application.ports.out.GuardarEstadoPort;
import com.renaser.os.plataforma.application.ports.out.RegistrarCambioAuditadoPort;
import com.renaser.os.plataforma.domain.model.catalogo.CatalogoDeFuncionalidades;
import com.renaser.os.plataforma.domain.model.catalogo.CodigoRenaserEnFichas;
import com.renaser.os.plataforma.domain.model.catalogo.CodigoRenaserEnFichas.Estado;
import com.renaser.os.plataforma.domain.model.catalogo.DefinicionDeFuncionalidad;
import com.renaser.os.plataforma.domain.model.estado.EstadoDeFuncionalidad;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.IdGenerator;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.UserRole;
import com.renaser.os.users.api.UserStatus;
import com.renaser.os.users.api.UserSummary;
import com.renaser.os.users.api.UserSummaryFinder;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.Set;

/**
 * El interruptor «Código Renaser en las fichas» (spec 034, D-299) visto desde Administración: un sí o un no para el
 * mentor, la guía y el líder juntos. No guarda nada propio: lee y escribe la funcionalidad {@code radar.fichas} del
 * catálogo de Soporte TI por el mismo camino de escritura que la consola ({@link EscrituraDeEstado}), así que el cambio queda
 * en la auditoría append-only, recarga la copia de todas las instancias y rige en el acto.
 *
 * <p><b>Quién:</b> ADMIN, ALCHEMIST e IT_SUPPORT con la cuenta activa (decisión del dueño, 034-4 B). El interceptor de permisos
 * falla abierto para algunos roles (A-1), así que la guardia es de este servicio, como en {@code GuardiaDeAnimalesDeFase}.
 * <b>Sin motivo escrito</b> (034-5): la auditoría guarda un motivo fijo. <b>Concurrencia</b> («gana el último»): si otro
 * cambio llega entre la lectura y la escritura, se relee y se reintenta; los dos quedan auditados.
 */
@Service
class VisibilidadDelCodigoRenaserService
        implements ConsultarVisibilidadDelCodigoRenaserUseCase, CambiarVisibilidadDelCodigoRenaserUseCase {

    private static final Set<UserRole> ROLES_QUE_DECIDEN = Set.of(UserRole.ADMIN, UserRole.ALCHEMIST, UserRole.IT_SUPPORT);
    private static final int INTENTOS = 3;

    private final UserSummaryFinder usuarios;
    private final CargarEstadosPort cargarEstadosPort;
    private final EscrituraDeEstado escritura;
    private final DefinicionDeFuncionalidad definicion = CatalogoDeFuncionalidades.inicial()
            .definicion(CodigoRenaserEnFichas.CLAVE).orElseThrow();

    VisibilidadDelCodigoRenaserService(UserSummaryFinder usuarios, CargarEstadosPort cargarEstadosPort,
                                       GuardarEstadoPort guardarEstadoPort, RegistrarCambioAuditadoPort auditoria,
                                       ApplicationEventPublisher events, IdGenerator idGenerator, Clock clock) {
        this.usuarios = usuarios;
        this.cargarEstadosPort = cargarEstadosPort;
        this.escritura = new EscrituraDeEstado(guardarEstadoPort, auditoria, events, idGenerator, clock);
    }

    @Override
    @Transactional(readOnly = true)
    public VisibilidadDelCodigoRenaser consultar(UserId actor) {
        exigirQuePuedaDecidir(actor);
        return vistaDe(filaVigente());
    }

    @Override
    @Transactional
    public VisibilidadDelCodigoRenaser cambiar(UserId actor, boolean visible) {
        exigirQuePuedaDecidir(actor);
        for (int intento = 1; intento <= INTENTOS; intento++) {
            Optional<EstadoDeFuncionalidad> vigente = filaVigente();
            if (CodigoRenaserEnFichas.yaRige(visible, vigente)) {
                return vistaDe(vigente);
            }
            Optional<EstadoDeFuncionalidad> guardada = escritura.aplicar(definicion, vigente.orElse(null),
                    CodigoRenaserEnFichas.cambioHacia(visible, vigente), actor, null);
            if (guardada.isPresent()) {
                return vistaDe(guardada);
            }
        }
        throw new IllegalStateException("Otra persona cambió esto al mismo tiempo. Vuelve a intentarlo.");
    }

    private Optional<EstadoDeFuncionalidad> filaVigente() {
        return cargarEstadosPort.todos().stream().filter(f -> f.clave().equals(CodigoRenaserEnFichas.CLAVE)).findFirst();
    }

    private VisibilidadDelCodigoRenaser vistaDe(Optional<EstadoDeFuncionalidad> fila) {
        Estado estado = CodigoRenaserEnFichas.estadoDe(fila);
        UltimoCambio ultimo = fila.map(f -> new UltimoCambio(f.actualizadoPor(), nombreDe(f.actualizadoPor()), f.actualizadoEn()))
                .orElse(null);
        return new VisibilidadDelCodigoRenaser(estado.visible(), estado.mixto(), estado.porRol(), ultimo);
    }

    private String nombreDe(UserId id) {
        return usuarios.findById(id).map(UserSummary::fullName).filter(n -> !n.isBlank()).orElse(null);
    }

    private void exigirQuePuedaDecidir(UserId actorId) {
        UserSummary actor = usuarios.findById(actorId)
                .orElseThrow(() -> new NotAuthorizedException("Cuenta inexistente o suspendida"));
        if (actor.status() != UserStatus.ACTIVE) {
            throw new NotAuthorizedException("La cuenta esta suspendida");
        }
        if (!ROLES_QUE_DECIDEN.contains(actor.role())) {
            throw new NotAuthorizedException(
                    "Solo Administración, Alquimista y Soporte TI deciden quién ve el Código Renaser en las fichas");
        }
    }
}

package com.renaser.os.plataforma.application.services;

import com.renaser.os.plataforma.application.ports.in.AccesoDeConsola;
import com.renaser.os.plataforma.application.ports.in.ConsultarAuditoriaUseCase;
import com.renaser.os.plataforma.application.ports.in.FuncionalidadEnConsola.Actor;
import com.renaser.os.plataforma.application.ports.out.ConsultarAuditoriaPort;
import com.renaser.os.plataforma.application.ports.out.ConsultarAuditoriaPort.FiltroDeAuditoria;
import com.renaser.os.plataforma.application.ports.out.ConsultarAuditoriaPort.PaginaDeCambios;
import com.renaser.os.plataforma.application.ports.out.RegistrarCambioAuditadoPort;
import com.renaser.os.plataforma.domain.model.auditoria.CambioAuditado;
import com.renaser.os.shared.application.AuditoriaDePlataforma;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.IdGenerator;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.UserSummary;
import com.renaser.os.users.api.UserSummaryFinder;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Objects;

/**
 * La auditoría de la plataforma: leerla desde la consola (contrato §6) y anotar los cambios automáticos que reporta
 * {@code rag} por {@link AuditoriaDePlataforma} (el circuito de la IA). Lo que no hace, a propósito: editar o borrar.
 */
@Service
@Primary
class AuditoriaDePlataformaService implements ConsultarAuditoriaUseCase, AuditoriaDePlataforma {

    static final int TAMANO_MAXIMO = 100;

    private final GuardiaDeSoporteTi guardia;
    private final ConsultarAuditoriaPort consultarPort;
    private final RegistrarCambioAuditadoPort registrarPort;
    private final UserSummaryFinder userSummaryFinder;
    private final IdGenerator idGenerator;
    private final Clock clock;

    AuditoriaDePlataformaService(GuardiaDeSoporteTi guardia, ConsultarAuditoriaPort consultarPort,
                                 RegistrarCambioAuditadoPort registrarPort, UserSummaryFinder userSummaryFinder,
                                 IdGenerator idGenerator, Clock clock) {
        this.guardia = guardia;
        this.consultarPort = consultarPort;
        this.registrarPort = registrarPort;
        this.userSummaryFinder = userSummaryFinder;
        this.idGenerator = idGenerator;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public PaginaDeAuditoria consultar(AccesoDeConsola acceso, Filtro filtro) {
        guardia.exigirConsolaAbierta(acceso);
        PaginaDeCambios pagina = consultarPort.buscar(new FiltroDeAuditoria(filtro.clave(), filtro.actor(),
                filtro.desde(), filtro.hasta(), Math.max(0, filtro.pagina()),
                Math.clamp(filtro.tamano(), 1, TAMANO_MAXIMO)));
        Map<UserId, UserSummary> actores = userSummaryFinder.findByIds(pagina.contenido().stream()
                .map(CambioAuditado::actor).filter(Objects::nonNull).distinct().toList());
        return new PaginaDeAuditoria(pagina.contenido().stream().map(cambio -> fila(cambio, actores)).toList(),
                pagina.pagina(), pagina.total());
    }

    @Override
    @Transactional
    public void registrarCambioAutomatico(String clave, Map<String, Object> antes, Map<String, Object> despues,
                                          String causa) {
        registrarPort.registrar(CambioAuditado.automatico(idGenerator.newId(), clave, antes, despues, causa,
                clock.now()));
    }

    private static Fila fila(CambioAuditado cambio, Map<UserId, UserSummary> actores) {
        Actor actor = cambio.actor() == null ? null : new Actor(cambio.actor(),
                actores.containsKey(cambio.actor()) ? actores.get(cambio.actor()).fullName() : null);
        return new Fila(cambio.id(), cambio.tipo(), actor, cambio.clave(), cambio.antes(), cambio.despues(),
                cambio.motivo(), cambio.dispositivo(), cambio.creadoEn());
    }
}

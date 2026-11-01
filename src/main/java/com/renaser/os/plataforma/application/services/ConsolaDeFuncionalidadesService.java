package com.renaser.os.plataforma.application.services;

import com.renaser.os.plataforma.application.ports.in.AccesoDeConsola;
import com.renaser.os.plataforma.application.ports.in.CambiarFuncionalidadUseCase;
import com.renaser.os.plataforma.application.ports.in.ConsultarFuncionalidadesUseCase;
import com.renaser.os.plataforma.application.ports.in.FuncionalidadEnConsola;
import com.renaser.os.plataforma.application.ports.out.GuardarEstadoPort;
import com.renaser.os.plataforma.application.ports.out.RegistrarCambioAuditadoPort;
import com.renaser.os.plataforma.application.ports.out.TokensDeConsolaPort;
import com.renaser.os.plataforma.domain.model.catalogo.DefinicionDeFuncionalidad;
import com.renaser.os.plataforma.domain.model.estado.CambioDeFuncionalidad;
import com.renaser.os.plataforma.domain.model.estado.EstadoDeFuncionalidad;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.IdGenerator;
import com.renaser.os.shared.domain.ReautenticacionRequeridaException;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * La consola de interruptores (contrato §6): listar y cambiar. Un cambio, en este orden:
 * <ol>
 *   <li>Soporte TI con la consola abierta (403 / 423);</li>
 *   <li>clave del catálogo, motivo y aviso válidos (400; una clave anti-bloqueo no está en el catálogo);</li>
 *   <li>la versión que estaba mirando sigue siendo la de la fila (409);</li>
 *   <li>si es crítico, el token de reautenticación de un solo uso (428);</li>
 *   <li>guarda la fila, deja la auditoría y publica {@link FuncionalidadCambiadaEvent} en la MISMA transacción. Después
 *       del commit, la copia de esta instancia se recarga y avisa a las demás por Redis ({@code AlCambiarFuncionalidad}).</li>
 * </ol>
 */
@Service
class ConsolaDeFuncionalidadesService implements ConsultarFuncionalidadesUseCase, CambiarFuncionalidadUseCase {

    private final GuardiaDeSoporteTi guardia;
    private final FuncionalidadesEnConsola vistas;
    private final EstadoVigente estado;
    private final EscrituraDeEstado escritura;
    private final TokensDeConsolaPort tokens;

    ConsolaDeFuncionalidadesService(GuardiaDeSoporteTi guardia, FuncionalidadesEnConsola vistas, EstadoVigente estado,
                                    GuardarEstadoPort guardarEstadoPort, RegistrarCambioAuditadoPort auditoria,
                                    TokensDeConsolaPort tokens, ApplicationEventPublisher events,
                                    IdGenerator idGenerator, Clock clock) {
        this.guardia = guardia;
        this.vistas = vistas;
        this.estado = estado;
        this.escritura = new EscrituraDeEstado(guardarEstadoPort, auditoria, events, idGenerator, clock);
        this.tokens = tokens;
    }

    @Override
    @Transactional(readOnly = true)
    public List<FuncionalidadEnConsola> listar(AccesoDeConsola acceso) {
        guardia.exigirConsolaAbierta(acceso);
        return vistas.todas();
    }

    @Override
    @Transactional
    public FuncionalidadEnConsola cambiar(CambiarFuncionalidadCommand command) {
        guardia.exigirConsolaAbierta(command.acceso());
        DefinicionDeFuncionalidad definicion = estado.catalogo().definicion(command.clave())
                .orElseThrow(() -> new IllegalArgumentException(
                        "«" + command.clave() + "» no tiene interruptor: no está en el catálogo"));
        CambioDeFuncionalidad cambio = ReglasDelPedido.cambioDe(command);
        EstadoDeFuncionalidad anterior = vistas.filasGuardadas().get(definicion.clave());
        exigirVersionVigente(anterior, cambio);
        if (cambio.esCritico(definicion)) {
            exigirReautenticacion(command);
        }
        EstadoDeFuncionalidad nuevo = escritura
                .aplicar(definicion, anterior, cambio, command.acceso().actor(), command.dispositivo())
                .orElseThrow(() -> new IllegalStateException("Otra persona cambió esta funcionalidad recién. Vuelve a cargarla."));
        return vistas.una(nuevo.clave());
    }

    private static void exigirVersionVigente(EstadoDeFuncionalidad anterior, CambioDeFuncionalidad cambio) {
        long vigente = anterior == null ? 0L : anterior.version();
        if (vigente != cambio.versionEsperada()) {
            throw new IllegalStateException("Otra persona cambió esta funcionalidad recién. Vuelve a cargarla.");
        }
    }

    private void exigirReautenticacion(CambiarFuncionalidadCommand command) {
        String token = command.reautenticacion();
        if (token == null || token.isBlank()
                || !tokens.gastarReautenticacion(token, command.acceso().actor(), command.acceso().sesion())) {
            throw new ReautenticacionRequeridaException();
        }
    }
}

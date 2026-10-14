package com.renaser.os.plataforma.application.services;

import com.renaser.os.plataforma.application.ports.in.AccesoDeConsola;
import com.renaser.os.plataforma.application.ports.in.CambiarDespliegueUseCase;
import com.renaser.os.plataforma.application.ports.in.ConsultarDespliegueUseCase;
import com.renaser.os.plataforma.application.ports.in.DespliegueEnConsola;
import com.renaser.os.plataforma.application.ports.out.CargarDesplieguesPort;
import com.renaser.os.plataforma.application.ports.out.GuardarDesplieguePort;
import com.renaser.os.plataforma.application.ports.out.TokensDeConsolaPort;
import com.renaser.os.plataforma.application.services.AnotadorDeDespliegues.Anotacion;
import com.renaser.os.plataforma.domain.model.catalogo.DefinicionDeFuncionalidad;
import com.renaser.os.plataforma.domain.model.despliegue.CambioDeDespliegue;
import com.renaser.os.plataforma.domain.model.despliegue.DespliegueDeFuncionalidad;
import com.renaser.os.plataforma.domain.model.despliegue.PasoDelDespliegue;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.ReautenticacionRequeridaException;
import com.renaser.os.shared.domain.UserId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.function.Predicate;

/**
 * El escalón de un despliegue desde la consola (contrato fase 2 §3.4). Un cambio, en este orden (el de la fase 1):
 * <ol>
 *   <li>Soporte TI con la consola abierta (403 / 423);</li>
 *   <li>clave del catálogo, porcentaje 0..100 y motivo (400; una clave anti-bloqueo no está en el catálogo);</li>
 *   <li>si mandó versión, que siga siendo la vigente (409);</li>
 *   <li>si el paso es crítico, el token de reautenticación de un solo uso (428);</li>
 *   <li>guarda (409 si otro cambio llegó en el medio), deja la auditoría y publica los eventos en la MISMA transacción.
 *       Pedir lo que ya está no guarda, no audita ni avisa.</li>
 * </ol>
 */
@Service
class ConsolaDeDesplieguesService implements ConsultarDespliegueUseCase, CambiarDespliegueUseCase {

    static final String OTRO_CAMBIO = "Otra persona cambió este despliegue recién. Vuelve a cargarlo.";

    private final GuardiaDeSoporteTi guardia;
    private final EstadoVigente estado;
    private final CargarDesplieguesPort cargarDesplieguesPort;
    private final GuardarDesplieguePort guardarDesplieguePort;
    private final TokensDeConsolaPort tokens;
    private final AnotadorDeDespliegues anotador;
    private final DesplieguesEnConsola vistas;
    private final Clock clock;

    ConsolaDeDesplieguesService(GuardiaDeSoporteTi guardia, EstadoVigente estado,
                                CargarDesplieguesPort cargarDesplieguesPort, GuardarDesplieguePort guardarDesplieguePort,
                                TokensDeConsolaPort tokens, AnotadorDeDespliegues anotador, DesplieguesEnConsola vistas,
                                Clock clock) {
        this.guardia = guardia;
        this.estado = estado;
        this.cargarDesplieguesPort = cargarDesplieguesPort;
        this.guardarDesplieguePort = guardarDesplieguePort;
        this.tokens = tokens;
        this.anotador = anotador;
        this.vistas = vistas;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public DespliegueEnConsola detalle(AccesoDeConsola acceso, String clave) {
        guardia.exigirConsolaAbierta(acceso);
        return vistas.detalle(definicion(estado, clave).clave());
    }

    @Override
    @Transactional
    public DespliegueEnConsola cambiar(CambiarDespliegueCommand command) {
        guardia.exigirConsolaAbierta(command.acceso());
        DefinicionDeFuncionalidad definicion = definicion(estado, command.clave());
        CambioDeDespliegue cambio = cambioDe(command, definicion.clave());
        DespliegueDeFuncionalidad actual = cargarDesplieguesPort.de(definicion.clave()).orElse(null);
        exigirVersionVigente(actual, cambio);
        PasoDelDespliegue paso = cambio.pasoDesde(actual);
        if (paso == PasoDelDespliegue.NINGUNO) {
            return vistas.detalle(definicion.clave());
        }
        if (cambio.esCritico(paso, definicion.critica())) {
            exigirReautenticacion(command);
        }
        UserId actor = command.acceso().actor();
        Instant ahora = clock.now();
        DespliegueDeFuncionalidad nuevo = aplicar(paso, cambio, actual, actor, ahora);
        anotador.escalon(definicion, new Anotacion(actor, definicion.clave(), paraAuditoria(actual),
                paraAuditoria(nuevo), cambio.motivo(), command.dispositivo(), ahora), cambio.resumen(paso, actual));
        return vistas.detalle(definicion.clave());
    }

    /** La definición del catálogo; una clave que no está (anti-bloqueo o inventada) no tiene despliegue: 400. */
    static DefinicionDeFuncionalidad definicion(EstadoVigente estado, String clave) {
        return estado.catalogo().definicion(clave).orElseThrow(() -> new IllegalArgumentException(
                "«" + clave + "» no tiene despliegue: no está en el catálogo"));
    }

    private static CambioDeDespliegue cambioDe(CambiarDespliegueCommand command, String clave) {
        if (command.porcentaje() == null) {
            throw new IllegalArgumentException("Falta el porcentaje (0 a 100).");
        }
        return new CambioDeDespliegue(clave, command.porcentaje(), command.motivo(), command.version());
    }

    private static void exigirVersionVigente(DespliegueDeFuncionalidad actual, CambioDeDespliegue cambio) {
        long vigente = actual == null ? 0L : actual.version();
        if (cambio.versionEsperada() != null && cambio.versionEsperada() != vigente) {
            throw new IllegalStateException(OTRO_CAMBIO);
        }
    }

    private void exigirReautenticacion(CambiarDespliegueCommand command) {
        String token = command.reautenticacion();
        if (token == null || token.isBlank()
                || !tokens.gastarReautenticacion(token, command.acceso().actor(), command.acceso().sesion())) {
            throw new ReautenticacionRequeridaException();
        }
    }

    /** Guarda el paso con bloqueo optimista sobre la versión leída; devuelve el despliegue nuevo, o {@code null} al terminar. */
    private DespliegueDeFuncionalidad aplicar(PasoDelDespliegue paso, CambioDeDespliegue cambio,
                                              DespliegueDeFuncionalidad actual, UserId actor, Instant ahora) {
        return switch (paso) {
            case EMPIEZA -> guardado(guardarDesplieguePort::empezar, DespliegueDeFuncionalidad.empezar(cambio.clave(),
                    cambio.porcentaje(), guardarDesplieguePort.siguienteVersion(), actor, ahora));
            case SUBE, BAJA -> guardado(nuevo -> guardarDesplieguePort.cambiarPorcentaje(nuevo, actual.version()),
                    actual.conPorcentaje(cambio.porcentaje(), guardarDesplieguePort.siguienteVersion(), actor, ahora));
            case TERMINA -> {
                exigirGuardado(guardarDesplieguePort.terminar(actual.clave(), actual.version()));
                yield null;
            }
            case NINGUNO -> actual;
        };
    }

    private static DespliegueDeFuncionalidad guardado(Predicate<DespliegueDeFuncionalidad> guardar,
                                                      DespliegueDeFuncionalidad nuevo) {
        exigirGuardado(guardar.test(nuevo));
        return nuevo;
    }

    private static void exigirGuardado(boolean guardado) {
        if (!guardado) {
            throw new IllegalStateException(OTRO_CAMBIO);
        }
    }

    private static Map<String, Object> paraAuditoria(DespliegueDeFuncionalidad despliegue) {
        return despliegue == null ? DespliegueDeFuncionalidad.sinDespliegueParaAuditoria()
                : despliegue.paraAuditoria();
    }
}

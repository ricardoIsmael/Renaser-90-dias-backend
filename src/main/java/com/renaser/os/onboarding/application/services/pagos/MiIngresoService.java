package com.renaser.os.onboarding.application.services.pagos;

import com.renaser.os.onboarding.application.ports.in.pagos.MiIngreso;
import com.renaser.os.onboarding.application.ports.in.pagos.MiIngreso.ClaveDePaso;
import com.renaser.os.onboarding.application.ports.in.pagos.MiIngreso.Paso;
import com.renaser.os.onboarding.application.ports.in.pagos.MiIngresoUseCase;
import com.renaser.os.onboarding.application.ports.in.pagos.VistaDePagos.PagoAnotado;
import com.renaser.os.onboarding.application.ports.out.actor.ConsultarActorPort;
import com.renaser.os.onboarding.application.ports.out.estado.LoadEstadoOnboardingPort;
import com.renaser.os.onboarding.application.ports.out.estado.SaveEstadoOnboardingPort;
import com.renaser.os.onboarding.application.ports.out.mapa.EtapaOnboardingPort;
import com.renaser.os.onboarding.domain.model.diagnostico.Diagnostico;
import com.renaser.os.onboarding.domain.model.estado.EstadoOnboarding;
import com.renaser.os.onboarding.domain.model.pagos.EstadoDeConfirmacion;
import com.renaser.os.onboarding.domain.model.pagos.ModalidadDePago;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;

/**
 * Yo → «Mi ingreso» (spec 032): lo de quien tiene la sesión, nunca lo de otra persona (ninguna operación recibe un id). Sin pago anotado no hay nada que mostrar; con cronograma y sin cuotas todavía (D-315) se muestra, pero sin nada que confirmar. La confirmación
 * del cronograma nunca bloquea nada: solo deja constancia de que la persona lo revisó.
 */
@Service
public class MiIngresoService implements MiIngresoUseCase {

    private final LoadEstadoOnboardingPort cargarEstado;
    private final SaveEstadoOnboardingPort guardarEstado;
    private final ConsultarActorPort actores;
    private final EtapaOnboardingPort etapas;
    private final ArmadorDeVistaDePagos armador;
    private final Clock clock;

    public MiIngresoService(LoadEstadoOnboardingPort cargarEstado, SaveEstadoOnboardingPort guardarEstado, ConsultarActorPort actores,
                            EtapaOnboardingPort etapas, ArmadorDeVistaDePagos armador, Clock clock) {
        this.cargarEstado = cargarEstado;
        this.guardarEstado = guardarEstado;
        this.actores = actores;
        this.etapas = etapas;
        this.armador = armador;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public MiIngreso ver(UserId persona) {
        requireActiva(persona);
        return leer(persona);
    }

    @Override
    @Transactional
    public MiIngreso confirmar(UserId persona, ConfirmacionPedida pedido) {
        requireActiva(persona);
        if (!pedido.revisado()) {
            throw new IllegalArgumentException("Marca que revisaste tus fechas y montos");
        }
        EstadoOnboarding estado = cargarEstado.deUsuario(persona).filter(e -> e.pago().esConCronograma())
                .orElseThrow(() -> new IllegalStateException("No tienes un cronograma de pagos que confirmar"));
        var cronograma = armador.cronogramaDe(persona, estado.pago());
        if (cronograma.cuotas().isEmpty()) {
            // D-315: Administración las registra después; mientras no haya cuotas no hay nada que revisar ni confirmar.
            throw new IllegalStateException("Todavía no tienes cuotas registradas que confirmar");
        }
        String huella = cronograma.huella();
        if (estado.pago().confirmacion(huella) != EstadoDeConfirmacion.CONFIRMADA) {
            estado.confirmarCronograma(pedido.nombre(), huella, clock);
            guardarEstado.guardar(estado);
        }
        return leer(persona);
    }

    private MiIngreso leer(UserId persona) {
        EstadoOnboarding estado = cargarEstado.deUsuario(persona).orElse(null);
        PagoAnotado pagos = armador.armar(persona, estado, false);
        if (pagos == null) {
            return MiIngreso.sinNada();
        }
        return new MiIngreso(pasos(persona, estado, pagos), pagos);
    }

    /** Solo los que le aplican: el Diagnóstico solo si le tocó; el Acuerdo es el de los términos; Pagos, que ya los anotó. */
    private List<Paso> pasos(UserId persona, EstadoOnboarding estado, PagoAnotado pagos) {
        List<Paso> pasos = new ArrayList<>();
        if (estado.conDiagnostico()) {
            pasos.add(new Paso(ClaveDePaso.DIAGNOSTICO, etapas.flujosCompletados(persona).contains(Diagnostico.FLUJO)));
        }
        pasos.add(new Paso(ClaveDePaso.ACUERDO, estado.terminosAceptadosEn() != null));
        pasos.add(new Paso(ClaveDePaso.PAGOS, pagosAlDia(pagos)));
        return pasos;
    }

    /**
     * El paso de Pagos está hecho si pagó completo o si todas sus cuotas figuran pagadas. «Con cronograma» sin ninguna cuota todavía (D-315) NO está hecho: no hay nada
     * pagado (un {@code allMatch} sobre una lista vacía diría que sí).
     */
    private static boolean pagosAlDia(PagoAnotado pagos) {
        return pagos.modalidad() == ModalidadDePago.SIN_CRONOGRAMA
                || (!pagos.cuotas().isEmpty() && pagos.cuotas().stream().allMatch(cuota -> cuota.cuota().pagada()));
    }

    /** Suspendida → 403; inexistente → 404. */
    private void requireActiva(UserId persona) {
        ConsultarActorPort.ActorOnboarding actor = actores.deActor(persona)
                .orElseThrow(() -> new NoSuchElementException("Usuario no encontrado: " + persona));
        if (actor.suspendido()) {
            throw new NotAuthorizedException("Cuenta suspendida");
        }
    }
}

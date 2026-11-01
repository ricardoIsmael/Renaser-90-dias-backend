package com.renaser.os.onboarding.application.services.pagos;

import com.renaser.os.onboarding.application.ports.in.pagos.PagosDeLaPersonaUseCase;
import com.renaser.os.onboarding.application.ports.in.pagos.VistaDePagos;
import com.renaser.os.onboarding.application.ports.out.actor.ConsultarActorPort;
import com.renaser.os.onboarding.application.ports.out.actor.ConsultarActorPort.ActorOnboarding;
import com.renaser.os.onboarding.application.ports.out.estado.LoadEstadoOnboardingPort;
import com.renaser.os.onboarding.application.ports.out.estado.SaveEstadoOnboardingPort;
import com.renaser.os.onboarding.application.ports.out.pagos.CuotasDePagoPort;
import com.renaser.os.onboarding.domain.model.estado.EstadoOnboarding;
import com.renaser.os.onboarding.domain.model.pagos.CronogramaDePagos;
import com.renaser.os.onboarding.domain.model.pagos.Cuota;
import com.renaser.os.onboarding.domain.model.pagos.Moneda;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.IdGenerator;
import com.renaser.os.shared.domain.UserId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

/**
 * Los pagos de una persona vistos por Administración (spec 032). Cada operación comprueba primero quién actúa ({@link GuardiaDePagos}), carga el cronograma, aplica la regla en el
 * dominio ({@link CronogramaDePagos}) y guarda solo lo que cambió. Nada se recalcula solo: el servicio hace exactamente lo que Administración pidió.
 *
 * <p>Corre dentro de la transacción de aceptar una solicitud cuando se anota el pago al aceptar (spec 025): si algo falla, la aceptación entera se deshace.
 */
@Service
public class PagosDeLaPersonaService implements PagosDeLaPersonaUseCase {

    private final GuardiaDePagos guardia;
    private final ConsultarActorPort actores;
    private final LoadEstadoOnboardingPort cargarEstado;
    private final SaveEstadoOnboardingPort guardarEstado;
    private final CuotasDePagoPort cuotas;
    private final ArmadorDeVistaDePagos armador;
    private final IdGenerator ids;
    private final Clock clock;

    public PagosDeLaPersonaService(GuardiaDePagos guardia, ConsultarActorPort actores, LoadEstadoOnboardingPort cargarEstado,
                                   SaveEstadoOnboardingPort guardarEstado, CuotasDePagoPort cuotas, ArmadorDeVistaDePagos armador, IdGenerator ids,
                                   Clock clock) {
        this.guardia = guardia;
        this.actores = actores;
        this.cargarEstado = cargarEstado;
        this.guardarEstado = guardarEstado;
        this.cuotas = cuotas;
        this.armador = armador;
        this.ids = ids;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public VistaDePagos ver(UserId actor, UserId persona) {
        guardia.exigirQuePuedaAdministrar(actor);
        return vista(existente(persona), cargarEstado.deUsuario(persona).orElse(null));
    }

    @Override
    @Transactional
    public VistaDePagos asignar(UserId actor, UserId persona, PedidoDeCronograma pedido) {
        guardia.exigirQuePuedaAdministrar(actor);
        ActorOnboarding quien = existente(persona);
        if (cuotas.deLaPersona(persona).stream().anyMatch(Cuota::pagada)) {
            throw new IllegalStateException("Ya tiene cuotas pagadas: no se puede reemplazar su cronograma. Edita las pendientes o devuelve una a «Pendiente»");
        }
        EstadoOnboarding estado = cargarEstado.deUsuario(persona).orElseGet(() -> EstadoOnboarding.sinEmpezar(persona, clock));
        if (pedido.conCronograma()) {
            armarCronograma(persona, estado, pedido);
        } else {
            cuotas.eliminarTodas(persona);
            estado.anotarSinCronograma(clock);
        }
        return vista(quien, guardarEstado.guardar(estado));
    }

    @Override
    @Transactional
    public VistaDePagos agregarCuota(UserId actor, UserId persona, CuotaPedida cuota) {
        guardia.exigirQuePuedaAdministrar(actor);
        ActorOnboarding quien = existente(persona);
        EstadoOnboarding estado = conCronograma(persona);
        Cuota agregada = armador.cronogramaDe(persona, estado.pago()).agregar(nueva(cuota));
        cuotas.guardar(persona, agregada);
        return vista(quien, estado);
    }

    @Override
    @Transactional
    public VistaDePagos editarCuota(UserId actor, UserId persona, CambioDeCuota cambio) {
        guardia.exigirQuePuedaAdministrar(actor);
        ActorOnboarding quien = existente(persona);
        EstadoOnboarding estado = conCronograma(persona);
        Cuota editada = armador.cronogramaDe(persona, estado.pago()).editar(cambio.cuota(), cambio.nueva().venceEl(), cambio.nueva().monto());
        cuotas.guardar(persona, editada);
        return vista(quien, estado);
    }

    @Override
    @Transactional
    public VistaDePagos quitarCuota(UserId actor, UserId persona, UUID cuota) {
        guardia.exigirQuePuedaAdministrar(actor);
        ActorOnboarding quien = existente(persona);
        EstadoOnboarding estado = conCronograma(persona);
        Cuota quitada = armador.cronogramaDe(persona, estado.pago()).quitar(cuota);
        cuotas.eliminar(persona, quitada.id());
        return vista(quien, estado);
    }

    @Override
    @Transactional
    public VistaDePagos marcarCuota(UserId actor, UserId persona, MarcaDeCuota marca) {
        guardia.exigirQuePuedaAdministrar(actor);
        ActorOnboarding quien = existente(persona);
        EstadoOnboarding estado = conCronograma(persona);
        Cuota marcada = armador.cronogramaDe(persona, estado.pago()).marcar(marca.cuota(), marca.estado(), actor, clock.now());
        cuotas.guardar(persona, marcada);
        return vista(quien, estado);
    }

    /** Valida el cronograma entero ANTES de tocar lo que hubiera, para que un pedido inválido no deje nada a medias. */
    private void armarCronograma(UserId persona, EstadoOnboarding estado, PedidoDeCronograma pedido) {
        Moneda moneda = Moneda.desde(pedido.moneda());
        List<CuotaPedida> pedidas = pedido.cuotas() == null ? List.of() : pedido.cuotas();
        CronogramaDePagos cronograma = CronogramaDePagos.nuevo(moneda, pedidas.stream().map(this::nueva).toList());
        cuotas.eliminarTodas(persona);
        cronograma.cuotas().forEach(cuota -> cuotas.guardar(persona, cuota));
        estado.anotarConCronograma(moneda, clock);
    }

    private Cuota nueva(CuotaPedida pedida) {
        if (pedida == null) {
            throw new IllegalArgumentException("Cada cuota lleva su fecha y su monto");
        }
        return Cuota.nueva(ids.newId(), pedida.venceEl(), pedida.monto(), clock.now());
    }

    private EstadoOnboarding conCronograma(UserId persona) {
        return cargarEstado.deUsuario(persona).filter(estado -> estado.pago().esConCronograma())
                .orElseThrow(() -> new IllegalStateException("Esta persona no tiene un cronograma: asígnaselo primero"));
    }

    private ActorOnboarding existente(UserId persona) {
        return actores.deActor(persona).orElseThrow(() -> new NoSuchElementException("Persona no encontrada: " + persona));
    }

    private VistaDePagos vista(ActorOnboarding persona, EstadoOnboarding estado) {
        return new VistaDePagos(persona.nombre(), armador.armar(persona.id(), estado, true));
    }
}

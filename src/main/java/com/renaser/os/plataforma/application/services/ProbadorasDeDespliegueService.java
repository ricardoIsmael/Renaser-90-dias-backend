package com.renaser.os.plataforma.application.services;

import com.renaser.os.plataforma.application.ports.in.CambiarProbadorasUseCase;
import com.renaser.os.plataforma.application.ports.in.DespliegueEnConsola;
import com.renaser.os.plataforma.application.ports.out.CargarDesplieguesPort;
import com.renaser.os.plataforma.application.ports.out.GuardarDesplieguePort;
import com.renaser.os.plataforma.application.services.AnotadorDeDespliegues.Anotacion;
import com.renaser.os.plataforma.domain.model.despliegue.DespliegueDeFuncionalidad;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.UserSummary;
import com.renaser.os.users.api.UserSummaryFinder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.NoSuchElementException;
import java.util.Optional;

/**
 * Los probadores de un despliegue (contrato fase 2 §3.4): la ven aunque su cubeta quede fuera, también al 0 %. Sumar o
 * quitar deja su fila MANUAL en la auditoría con el número de probadores antes y después y, en el motivo, a quién; no
 * avisa por push. Repetir la operación no hace nada (ni audita).
 */
@Service
class ProbadorasDeDespliegueService implements CambiarProbadorasUseCase {

    static final String SIN_DESPLIEGUE = "Esta funcionalidad no está en despliegue: empieza uno antes de sumar probadores.";

    private final GuardiaDeSoporteTi guardia;
    private final EstadoVigente estado;
    private final CargarDesplieguesPort cargarDesplieguesPort;
    private final GuardarDesplieguePort guardarDesplieguePort;
    private final UserSummaryFinder userSummaryFinder;
    private final AnotadorDeDespliegues anotador;
    private final DesplieguesEnConsola vistas;
    private final Clock clock;

    ProbadorasDeDespliegueService(GuardiaDeSoporteTi guardia, EstadoVigente estado,
                                  CargarDesplieguesPort cargarDesplieguesPort,
                                  GuardarDesplieguePort guardarDesplieguePort, UserSummaryFinder userSummaryFinder,
                                  AnotadorDeDespliegues anotador, DesplieguesEnConsola vistas, Clock clock) {
        this.guardia = guardia;
        this.estado = estado;
        this.cargarDesplieguesPort = cargarDesplieguesPort;
        this.guardarDesplieguePort = guardarDesplieguePort;
        this.userSummaryFinder = userSummaryFinder;
        this.anotador = anotador;
        this.vistas = vistas;
        this.clock = clock;
    }

    @Override
    @Transactional
    public DespliegueEnConsola sumar(ProbadoraCommand command) {
        guardia.exigirConsolaAbierta(command.acceso());
        String clave = ConsolaDeDesplieguesService.definicion(estado, command.clave()).clave();
        UserSummary persona = userSummaryFinder.findById(command.persona())
                .orElseThrow(() -> new NoSuchElementException("Esa persona no existe."));
        DespliegueDeFuncionalidad actual = cargarDesplieguesPort.de(clave)
                .orElseThrow(() -> new IllegalStateException(SIN_DESPLIEGUE));
        if (actual.probadores().contains(persona.id())) {
            return vistas.detalle(clave);
        }
        Instant ahora = clock.now();
        UserId actor = command.acceso().actor();
        if (!guardarDesplieguePort.sumarProbadora(clave, persona.id(), actor, ahora)) {
            return seSumoEnElMedio(clave);
        }
        anotador.probadoras(new Anotacion(actor, clave, actual.paraAuditoria(),
                actual.conProbadora(persona.id()).paraAuditoria(),
                motivo("Probadora sumada", persona.fullName(), persona.id(), command.motivo()), command.dispositivo(),
                ahora));
        return vistas.detalle(clave);
    }

    @Override
    @Transactional
    public void quitar(ProbadoraCommand command) {
        guardia.exigirConsolaAbierta(command.acceso());
        String clave = ConsolaDeDesplieguesService.definicion(estado, command.clave()).clave();
        Optional<DespliegueDeFuncionalidad> actual = cargarDesplieguesPort.de(clave);
        if (actual.isEmpty() || !actual.get().probadores().contains(command.persona())
                || !guardarDesplieguePort.quitarProbadora(clave, command.persona())) {
            return;
        }
        String nombre = userSummaryFinder.findById(command.persona()).map(UserSummary::fullName).orElse(null);
        anotador.probadoras(new Anotacion(command.acceso().actor(), clave, actual.get().paraAuditoria(),
                actual.get().sinProbadora(command.persona()).paraAuditoria(),
                motivo("Probadora quitada", nombre, command.persona(), command.motivo()), command.dispositivo(),
                clock.now()));
    }

    /** No se insertó: o la sumó otra persona recién (no hay nada que hacer) o el despliegue terminó en el medio (409). */
    private DespliegueEnConsola seSumoEnElMedio(String clave) {
        if (cargarDesplieguesPort.de(clave).isEmpty()) {
            throw new IllegalStateException(SIN_DESPLIEGUE);
        }
        return vistas.detalle(clave);
    }

    /** «Probadora sumada: Ana Pérez (id). Motivo: …». La auditoría anota a quién, aunque la cuenta se borre después. */
    static String motivo(String que, String nombre, UserId persona, String motivo) {
        StringBuilder texto = new StringBuilder(que).append(": ");
        texto.append(nombre == null || nombre.isBlank() ? "cuenta" : nombre.strip()).append(" (").append(persona)
                .append(')');
        if (motivo != null && !motivo.isBlank()) {
            texto.append(". Motivo: ").append(motivo.strip());
        }
        return texto.toString();
    }
}

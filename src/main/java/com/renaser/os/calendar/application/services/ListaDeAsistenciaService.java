package com.renaser.os.calendar.application.services;

import com.renaser.os.calendar.application.ports.in.asistencia.PasarListaUseCase;
import com.renaser.os.calendar.application.ports.in.asistencia.PersonaConvocada;
import com.renaser.os.calendar.application.ports.out.asistencia.LoadListaDeAsistenciaPort;
import com.renaser.os.calendar.application.ports.out.asistencia.SaveListaDeAsistenciaPort;
import com.renaser.os.calendar.application.ports.out.persona.ConsultarPersonasPort;
import com.renaser.os.calendar.application.services.AccesoALaListaService.OcurrenciaDeLaLista;
import com.renaser.os.calendar.domain.model.asistencia.CierreDeLista;
import com.renaser.os.calendar.domain.model.asistencia.MarcaDeAsistencia;
import com.renaser.os.calendar.domain.model.asistencia.VentanaDePasarLista;
import com.renaser.os.calendar.domain.model.evento.EventoId;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.UserId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Pasar lista (D-256). Las reglas de tiempo y de cierre (supuestos S-1 a S-3 de D-256):
 * <ul>
 *   <li>se marca solo dentro de {@link VentanaDePasarLista} y con la lista abierta (si no, 409);</li>
 *   <li>cerrar se puede desde que abre la ventana, también después de que se venció;</li>
 *   <li>reabrir para corregir, solo dentro de la ventana (después queda como está).</li>
 * </ul>
 * Solo seguimiento: nada de esto publica un evento ni llama a otro módulo (no da puntos ni toca coherencia,
 * semáforo ni racha).
 */
@Service
public class ListaDeAsistenciaService implements PasarListaUseCase {

    private final AccesoALaListaService acceso;
    private final PersonasConvocadasService personas;
    private final AudienciaDelEventoService audiencia;
    private final LoadListaDeAsistenciaPort loadLista;
    private final SaveListaDeAsistenciaPort saveLista;
    private final ConsultarPersonasPort fichas;
    private final Clock clock;

    ListaDeAsistenciaService(AccesoALaListaService acceso, PersonasConvocadasService personas,
                             AudienciaDelEventoService audiencia, LoadListaDeAsistenciaPort loadLista,
                             SaveListaDeAsistenciaPort saveLista, ConsultarPersonasPort fichas, Clock clock) {
        this.acceso = acceso;
        this.personas = personas;
        this.audiencia = audiencia;
        this.loadLista = loadLista;
        this.saveLista = saveLista;
        this.fichas = fichas;
        this.clock = clock;
    }

    @Override
    public ListaDeAsistencia ver(UserId actor, EventoId eventoId, Instant inicioOcurrencia) {
        return armar(acceso.autorizar(actor, eventoId, inicioOcurrencia));
    }

    @Override
    @Transactional
    public PersonaConvocada marcar(MarcarAsistenciaCommand cmd) {
        OcurrenciaDeLaLista oc = acceso.autorizar(cmd.actor(), cmd.eventoId(), cmd.inicioOcurrencia());
        Instant ahora = clock.now();
        requireSePuedeMarcar(oc, ahora);
        Optional<MarcaDeAsistencia> actual = loadLista.marcaDe(cmd.eventoId(), oc.slot(), cmd.persona());
        if (actual.isEmpty()) {
            requireConvocada(oc, cmd.persona());
        }
        if (cmd.estado() == null) {
            saveLista.quitar(cmd.eventoId(), oc.slot(), cmd.persona());
        } else {
            MarcaDeAsistencia nueva = actual
                    .map(m -> m.remarcar(cmd.estado(), cmd.actor(), ahora))
                    .orElseGet(() -> new MarcaDeAsistencia(cmd.eventoId(), oc.slot(), cmd.persona(), cmd.estado(),
                            cmd.actor(), ahora));
            if (actual.map(m -> m != nueva).orElse(true)) {
                saveLista.guardar(nueva);
            }
        }
        return filaDe(oc, cmd.persona());
    }

    @Override
    @Transactional
    public ListaDeAsistencia cerrar(UserId actor, EventoId eventoId, Instant inicioOcurrencia) {
        OcurrenciaDeLaLista oc = acceso.autorizar(actor, eventoId, inicioOcurrencia);
        Instant ahora = clock.now();
        if (!VentanaDePasarLista.de(oc.ocurrencia()).yaAbrio(ahora)) {
            throw new IllegalStateException("Todavía no se puede pasar lista: se abre 30 min antes del inicio");
        }
        if (loadLista.cierre(eventoId, oc.slot()).isEmpty()) {
            saveLista.cerrar(eventoId, oc.slot(), new CierreDeLista(ahora, actor));
        }
        return armar(oc);
    }

    @Override
    @Transactional
    public ListaDeAsistencia reabrir(UserId actor, EventoId eventoId, Instant inicioOcurrencia) {
        OcurrenciaDeLaLista oc = acceso.autorizar(actor, eventoId, inicioOcurrencia);
        requireDentroDeLaVentana(oc, clock.now());
        saveLista.reabrir(eventoId, oc.slot());
        return armar(oc);
    }

    private void requireSePuedeMarcar(OcurrenciaDeLaLista oc, Instant ahora) {
        requireDentroDeLaVentana(oc, ahora);
        if (loadLista.cierre(oc.evento().id(), oc.slot()).isPresent()) {
            throw new IllegalStateException("La lista está cerrada. Reábrela para corregirla");
        }
    }

    private static void requireDentroDeLaVentana(OcurrenciaDeLaLista oc, Instant ahora) {
        VentanaDePasarLista ventana = VentanaDePasarLista.de(oc.ocurrencia());
        if (!ventana.yaAbrio(ahora)) {
            throw new IllegalStateException("Todavía no se puede pasar lista: se abre 30 min antes del inicio");
        }
        if (!ventana.estaAbierta(ahora)) {
            throw new IllegalStateException("Ya pasó el plazo para pasar lista: se cierra 12 h después del fin");
        }
    }

    /** Solo se marca a quien está en la lista: la audiencia, o quien respondió aunque no esté en ella. */
    private void requireConvocada(OcurrenciaDeLaLista oc, UserId persona) {
        boolean respondio = personas.respondio(oc.evento(), oc.slot(), persona);
        if (!respondio && !audiencia.incluye(oc.evento(), persona)) {
            throw new IllegalArgumentException("Esa persona no está en la lista de este evento");
        }
    }

    private PersonaConvocada filaDe(OcurrenciaDeLaLista oc, UserId persona) {
        return personas.una(oc.evento(), oc.slot(), persona)
                .orElseThrow(() -> new IllegalArgumentException("Esa persona no está en la lista de este evento"));
    }

    private ListaDeAsistencia armar(OcurrenciaDeLaLista oc) {
        VentanaDePasarLista ventana = VentanaDePasarLista.de(oc.ocurrencia());
        CierreDeLista cierre = loadLista.cierre(oc.evento().id(), oc.slot()).orElse(null);
        boolean abiertaAhora = cierre == null && ventana.estaAbierta(clock.now());
        List<PersonaConvocada> lista = personas.de(oc.evento(), oc.slot());
        return new ListaDeAsistencia(oc.slot(), ventana, abiertaAhora, cierre, nombreDe(cierre), lista);
    }

    private String nombreDe(CierreDeLista cierre) {
        if (cierre == null || cierre.cerradaPor() == null) {
            return null;
        }
        var ficha = fichas.porIds(List.of(cierre.cerradaPor())).get(cierre.cerradaPor());
        return ficha == null ? null : ficha.nombre();
    }
}

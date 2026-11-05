package com.renaser.os.calendar.application.services;

import com.renaser.os.calendar.application.ports.out.evento.LoadEventoPort;
import com.renaser.os.calendar.application.ports.out.evento.LoadExcepcionPort;
import com.renaser.os.calendar.application.ports.out.evento.SaveEventoPort;
import com.renaser.os.calendar.application.ports.out.meet.ConfiguracionDeGooglePort;
import com.renaser.os.calendar.application.ports.out.meet.LoadSalaMeetPort;
import com.renaser.os.calendar.application.ports.out.meet.SaveSalaMeetPort;
import com.renaser.os.calendar.application.ports.out.persona.CorreosDePersonasPort;
import com.renaser.os.calendar.application.services.PasosConGoogle.CuentaInutilizable;
import com.renaser.os.calendar.application.services.PasosConGoogle.Operacion;
import com.renaser.os.calendar.domain.model.evento.EstadoEvento;
import com.renaser.os.calendar.domain.model.evento.Evento;
import com.renaser.os.calendar.domain.model.evento.EventoId;
import com.renaser.os.calendar.domain.model.meet.EstadoSalaMeet;
import com.renaser.os.calendar.domain.model.meet.IdDeEventoDeGoogle;
import com.renaser.os.calendar.domain.model.meet.OcurrenciasDeseadas;
import com.renaser.os.calendar.domain.model.meet.PlanDeSalas;
import com.renaser.os.calendar.domain.model.meet.PlanDeSalas.OcurrenciaDeseada;
import com.renaser.os.calendar.domain.model.meet.PlanDeSalas.Paso;
import com.renaser.os.calendar.domain.model.meet.SalaMeet;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.IdGenerator;
import com.renaser.os.shared.domain.UserId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.IntStream;

/**
 * Deja las salas de Meet de un evento como Renaser las quiere (spec 040): mira lo que hay, calcula el plan ({@link PlanDeSalas}) y lo ejecuta fecha por
 * fecha ({@link PasosConGoogle}). Es el único camino a Google: lo usan guardar, editar, borrar, «Reintentar» y el proceso en segundo plano, así que
 * todos se comportan igual.
 *
 * <p><b>Nunca lanza</b> (SC-003): si Google falla, la fila de esa fecha lo anota y el evento de Renaser queda como estaba. Se llama siempre
 * <b>después</b> de guardar y <b>fuera</b> de toda transacción (FR-001): nunca se mantiene una conexión de la base esperando a Google.
 *
 * <p>Un candado por evento evita que una edición y el proceso de fondo trabajen a la vez sobre las mismas filas.
 */
@Component
class SincronizadorDeSalas {

    private static final Logger log = LoggerFactory.getLogger(SincronizadorDeSalas.class);

    /** Tiempo máximo que una petición espera a Google; lo que no alcanza queda como pendiente para el proceso de fondo (SC-002). */
    static final Duration PRESUPUESTO_DE_UNA_PETICION = Duration.ofSeconds(8);

    private final LoadEventoPort loadEventoPort;
    private final LoadExcepcionPort loadExcepcionPort;
    private final SaveEventoPort saveEventoPort;
    private final LoadSalaMeetPort loadSalaPort;
    private final SaveSalaMeetPort saveSalaPort;
    private final ConfiguracionDeGooglePort configuracion;
    private final PasosConGoogle pasos;
    private final Clock clock;
    private final IdGenerator idGenerator;
    private final CorreosDePersonasPort correos;
    /** Candados rayados por evento: dos hilos sobre el mismo evento esperan; los de eventos distintos casi nunca chocan. Sin mapa que crezca. */
    private final ReentrantLock[] candados = IntStream.range(0, 64).mapToObj(i -> new ReentrantLock()).toArray(ReentrantLock[]::new);

    SincronizadorDeSalas(LoadEventoPort loadEventoPort, LoadExcepcionPort loadExcepcionPort, SaveEventoPort saveEventoPort,
                         LoadSalaMeetPort loadSalaPort, SaveSalaMeetPort saveSalaPort, ConfiguracionDeGooglePort configuracion,
                         PasosConGoogle pasos, Clock clock, IdGenerator idGenerator, CorreosDePersonasPort correos) {
        this.correos = correos;
        this.loadEventoPort = loadEventoPort;
        this.loadExcepcionPort = loadExcepcionPort;
        this.saveEventoPort = saveEventoPort;
        this.loadSalaPort = loadSalaPort;
        this.saveSalaPort = saveSalaPort;
        this.configuracion = configuracion;
        this.pasos = pasos;
        this.clock = clock;
        this.idGenerator = idGenerator;
    }

    /**
     * @param forzar          ignorar las esperas de los reintentos (un «Reintentar» manual)
     * @param presupuesto     cuánto se puede esperar a Google; lo que no alcanza queda para el proceso de fondo
     * @return las salas del evento tal como quedaron (vacío si no hay ninguna)
     */
    List<SalaMeet> reconciliar(EventoId eventoId, UserId actor, String correlacion, boolean forzar, Duration presupuesto) {
        ReentrantLock candado = candados[Math.floorMod(eventoId.value().hashCode(), candados.length)];
        candado.lock();
        try {
            return sincronizar(eventoId, new Operacion(actor, correlacion), forzar, clock.now().plus(presupuesto));
        } catch (CuentaInutilizable ex) {
            return loadSalaPort.porEvento(eventoId);
        } catch (RuntimeException ex) {
            // Nunca la causa de Google al usuario: queda en el registro sin datos de la operacion.
            log.warn("[calendar.SincronizadorDeSalas] no se pudo sincronizar el evento {}: {}", eventoId, ex.getClass().getSimpleName());
            return List.of();
        } finally {
            candado.unlock();
        }
    }

    private List<SalaMeet> sincronizar(EventoId eventoId, Operacion operacion, boolean forzar, Instant limite) {
        Optional<Evento> evento = loadEventoPort.byId(eventoId);
        boolean vivo = evento.filter(e -> e.usaSalaDeMeetAutomatica() && e.estado() == EstadoEvento.PUBLICADO).isPresent();
        List<SalaMeet> existentes = loadSalaPort.porEvento(eventoId);
        if (!vivo && existentes.isEmpty()) {
            return existentes;
        }
        Instant ahora = clock.now();
        ZoneId zona = evento.map(Evento::timezone).orElse(ZoneId.of("UTC"));
        Map<LocalDate, OcurrenciaDeseada> deseadas = vivo
                ? OcurrenciasDeseadas.de(evento.get(), loadExcepcionPort.porEvento(eventoId), ahora, configuracion.ventanaEnDias())
                : Map.of();
        LocalDate hoy = ahora.atZone(zona).toLocalDate();
        if (vivo) {
            existentes = acompanarAlEventoDeUnSoloDia(evento.get(), deseadas, existentes, ahora);
            alinearElAcceso(evento.get(), existentes, hoy, ahora);
        }
        List<Paso> plan = PlanDeSalas.calcular(vivo, hoy, deseadas, existentes, ahora, forzar);
        Invitados invitados = new Invitados(evento.orElse(null), operacion.actor());
        for (Paso paso : plan) {
            ejecutar(paso, evento.orElse(null), deseadas, zona, invitados, operacion, limite);
        }
        List<SalaMeet> resultado = loadSalaPort.porEvento(eventoId);
        if (vivo) {
            publicarElEnlaceEnElEvento(evento.get(), resultado, hoy);
        }
        return resultado;
    }

    private void ejecutar(Paso paso, Evento evento, Map<LocalDate, OcurrenciaDeseada> deseadas, ZoneId zona, Invitados invitados,
                          Operacion operacion, Instant limite) {
        boolean sinTiempo = clock.now().isAfter(limite);
        switch (paso.accion()) {
            case CREAR -> {
                SalaMeet sala = salaParaCrear(paso, evento);
                if (sinTiempo) {
                    saveSalaPort.guardar(sala);
                } else {
                    pasos.crear(sala, deseadas.get(paso.fecha()), zona, invitados.alCrear(), operacion);
                }
            }
            case ACTUALIZAR -> {
                if (!sinTiempo) {
                    pasos.actualizar(paso.sala(), deseadas.get(paso.fecha()), zona, invitados.alEditar(), operacion);
                }
            }
            case AJUSTAR_ACCESO -> {
                if (!sinTiempo) {
                    pasos.ajustarAcceso(paso.sala(), operacion);
                }
            }
            case BORRAR -> {
                if (!sinTiempo) {
                    pasos.borrar(paso.sala(), operacion);
                }
            }
        }
    }

    /**
     * Quién va como invitado del evento de Calendar (decisión 6 del dueño, FR-035): quien creó el evento en Renaser y, si es otra persona, quien lo
     * está editando ahora (los dos son ADMIN o ALCHEMIST: solo ellos crean y editan eventos con Meet). Se busca su correo una sola vez por
     * sincronización y solo si hay algo que pedirle a Google. Si no se encuentra el correo, no se invita a nadie y la sala se crea igual.
     */
    private final class Invitados {

        private final Evento evento;
        private final UserId editor;
        private List<String> correosDeLaOperacion;

        Invitados(Evento evento, UserId editor) {
            this.evento = evento;
            this.editor = editor;
        }

        /** Al crear la sala siempre se dice la lista (el proceso de fondo invita al menos a quien creó el evento). */
        List<String> alCrear() {
            return configuracion.invitarAlCreador() ? correos() : List.of();
        }

        /**
         * Al editar, solo cuando una persona edita: el proceso en segundo plano no sabe quién fue y no toca los invitados que Google ya tiene
         * ({@code null}).
         */
        List<String> alEditar() {
            return configuracion.invitarAlCreador() && editor != null ? correos() : null;
        }

        private List<String> correos() {
            if (correosDeLaOperacion == null) {
                correosDeLaOperacion = buscar();
            }
            return correosDeLaOperacion;
        }

        private List<String> buscar() {
            if (evento == null) {
                return List.of();
            }
            LinkedHashSet<UserId> personas = new LinkedHashSet<>();
            if (evento.creadoPor() != null) {
                personas.add(evento.creadoPor());
            }
            if (editor != null) {
                personas.add(editor);
            }
            try {
                Map<UserId, String> encontrados = correos.correosDe(personas);
                return personas.stream().map(encontrados::get).filter(c -> c != null && !c.isBlank()).map(String::trim).distinct().toList();
            } catch (RuntimeException ex) {
                log.warn("[calendar.SincronizadorDeSalas] no se pudo leer el correo de quien organiza el evento {}: {}", evento.id(),
                        ex.getClass().getSimpleName());
                return List.of();
            }
        }
    }

    /** La fila que se va a pedir: la que ya había (reactivada si era una lápida) o una nueva con el id de Google derivado del evento y la fecha. */
    private SalaMeet salaParaCrear(Paso paso, Evento evento) {
        Instant ahora = clock.now();
        if (paso.sala() == null) {
            String idGoogle = IdDeEventoDeGoogle.de(evento.id(), paso.fecha(), evento.esRecurrente());
            return SalaMeet.nueva(idGenerator.newId(), evento.id(), paso.fecha(), idGoogle, evento.accesoSalaMeet(), ahora);
        }
        SalaMeet sala = paso.sala();
        if (sala.estado() == EstadoSalaMeet.BORRADO) {
            sala.reactivar(evento.accesoSalaMeet(), ahora);
        } else {
            sala.elegirAcceso(evento.accesoSalaMeet(), ahora);
        }
        return sala;
    }

    /**
     * Un evento de un solo día que se mueve a otra fecha conserva su sala (y su enlace): solo cambia la fecha con que se la busca. Un evento que se
     * repite no se mueve así; sus fechas nuevas reciben sala y las que ya no existen se borran.
     */
    private List<SalaMeet> acompanarAlEventoDeUnSoloDia(Evento evento, Map<LocalDate, OcurrenciaDeseada> deseadas,
                                                         List<SalaMeet> existentes, Instant ahora) {
        if (evento.esRecurrente() || deseadas.size() != 1) {
            return existentes;
        }
        LocalDate fecha = deseadas.keySet().iterator().next();
        List<SalaMeet> vivas = existentes.stream().filter(s -> s.estado() != EstadoSalaMeet.BORRADO).toList();
        if (vivas.size() != 1 || vivas.get(0).fechaOcurrencia().equals(fecha)) {
            return existentes;
        }
        boolean fechaOcupada = existentes.stream().anyMatch(s -> s.fechaOcurrencia().equals(fecha));
        if (fechaOcupada) {
            return existentes;
        }
        SalaMeet sala = vivas.get(0);
        sala.moverA(fecha, ahora);
        saveSalaPort.guardar(sala);
        return loadSalaPort.porEvento(evento.id());
    }

    /** Cambiar quién puede entrar en un evento ya creado alcanza a las salas de las fechas que todavía no pasaron (FR-023). */
    private void alinearElAcceso(Evento evento, List<SalaMeet> existentes, LocalDate hoy, Instant ahora) {
        for (SalaMeet sala : existentes) {
            boolean difiere = sala.estado() != EstadoSalaMeet.BORRADO && !sala.fechaOcurrencia().isBefore(hoy)
                    && sala.accesoElegido() != evento.accesoSalaMeet();
            if (difiere) {
                sala.elegirAcceso(evento.accesoSalaMeet(), ahora);
                saveSalaPort.guardar(sala);
            }
        }
    }

    /** El evento muestra el enlace de la próxima fecha con sala (FR-017); si es de un solo día, el suyo. */
    private void publicarElEnlaceEnElEvento(Evento evento, List<SalaMeet> salas, LocalDate hoy) {
        String enlace = salas.stream()
                .filter(s -> s.estado() != EstadoSalaMeet.BORRADO && s.enlace() != null && !s.fechaOcurrencia().isBefore(hoy))
                .min(Comparator.comparing(SalaMeet::fechaOcurrencia))
                .map(SalaMeet::enlace)
                .orElseGet(() -> salas.stream()
                        .filter(s -> s.estado() != EstadoSalaMeet.BORRADO && s.enlace() != null)
                        .max(Comparator.comparing(SalaMeet::fechaOcurrencia))
                        .map(SalaMeet::enlace)
                        .orElse(null));
        if (enlace == null ? evento.valorUbicacion() != null : !enlace.equals(evento.valorUbicacion())) {
            saveEventoPort.fijarEnlaceDeMeet(evento.id(), enlace);
        }
    }
}

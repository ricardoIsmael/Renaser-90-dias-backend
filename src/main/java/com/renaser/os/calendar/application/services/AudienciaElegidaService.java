package com.renaser.os.calendar.application.services;

import com.renaser.os.calendar.application.ports.in.audiencia.AudienciaRechazadaException;
import com.renaser.os.calendar.application.ports.in.audiencia.PersonaConvocable;
import com.renaser.os.calendar.application.ports.in.audiencia.ResumenDeAudiencia;
import com.renaser.os.calendar.application.ports.out.audiencia.ColoresDelSemaforoPort;
import com.renaser.os.calendar.application.ports.out.audiencia.PadronDeConvocatoriaPort;
import com.renaser.os.calendar.application.ports.out.audiencia.PadronDeConvocatoriaPort.Convocado;
import com.renaser.os.calendar.application.ports.out.audiencia.PadronDeConvocatoriaPort.GrupoConvocable;
import com.renaser.os.calendar.application.ports.out.celula.ConsultarMiembrosCelulaPort;
import com.renaser.os.calendar.application.ports.out.celula.ConsultarPertenenciaAGrupoPort;
import com.renaser.os.calendar.domain.model.evento.Evento;
import com.renaser.os.calendar.domain.model.evento.RolUsuario;
import com.renaser.os.calendar.domain.model.evento.audiencia.AudienciaElegida;
import com.renaser.os.calendar.domain.model.evento.audiencia.ColorDeAudiencia;
import com.renaser.os.calendar.domain.model.evento.audiencia.Invitado;
import com.renaser.os.calendar.domain.model.evento.audiencia.OrigenDelInvitado;
import com.renaser.os.calendar.domain.model.evento.audiencia.ParteDeAudiencia;
import com.renaser.os.calendar.domain.model.evento.audiencia.QuienConvoca;
import com.renaser.os.calendar.domain.model.evento.audiencia.ReglaDeAudiencia;
import com.renaser.os.calendar.domain.model.evento.audiencia.TipoDeParte;
import com.renaser.os.shared.domain.AlcanceDeFormacion;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.Formacion;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.AlcanceDeFormacionFinder;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * La audiencia elegida de un evento (spec 044): armarla al guardar (validando el alcance de quien guarda y tomando la foto del semáforo), decir quién
 * está dentro ahora, si una persona está dentro y cómo resumirla para quien la creó. Es el ÚNICO lugar con esas reglas: lo usan la lista, el detalle,
 * los recordatorios, el despacho, «Quién respondió» y la vista previa del formulario.
 */
@Component
class AudienciaElegidaService {

    private final PadronDeConvocatoriaPort padron;
    private final ColoresDelSemaforoPort colores;
    private final ConsultarMiembrosCelulaPort celulas;
    private final ConsultarPertenenciaAGrupoPort pertenencia;
    private final AlcanceDeFormacionFinder formaciones;
    private final Clock clock;

    AudienciaElegidaService(PadronDeConvocatoriaPort padron, ColoresDelSemaforoPort colores, ConsultarMiembrosCelulaPort celulas,
                            ConsultarPertenenciaAGrupoPort pertenencia, AlcanceDeFormacionFinder formaciones, Clock clock) {
        this.clock = clock;
        this.padron = padron;
        this.colores = colores;
        this.celulas = celulas;
        this.pertenencia = pertenencia;
        this.formaciones = formaciones;
    }

    // ─── Armar al guardar ────────────────────────────────────────────────────────

    /**
     * Arma la audiencia que se guarda. Valida cada parte contra el alcance de quien guarda (FR-002, FR-004), toma la foto del semáforo de cada color
     * nuevo (FR-010) o conserva la que ya había si no se pidió volver a calcular (FR-011), y rechaza una audiencia que hoy no tiene a nadie (FR-005).
     *
     * @param mentorDeLosAlumnos si el evento lo creó un mentor o una guía, quién (al editar lo puede editar otra persona): de él son «mis alumnos»
     *                           y a sus alumnos se les mira el color. {@code null} si lo creó el líder o Administración
     * @param previa             la audiencia guardada antes (al editar), o vacía
     */
    AudienciaElegida armar(UserId actor, RolUsuario rol, Formacion formacion, List<ParteDeAudiencia> partes, UserId mentorDeLosAlumnos,
                           AudienciaElegida previa, boolean recalcular, Instant ahora) {
        Armado armado = armado(actor, rol, formacion, partes, mentorDeLosAlumnos, previa, recalcular, ahora);
        if (!armado.rechazados.isEmpty()) {
            throw AudienciaRechazadaException.fueraDeTuAlcance(List.copyOf(armado.rechazados));
        }
        AudienciaElegida audiencia = armado.audiencia();
        if (miembros(formacion, audiencia).stream().noneMatch(persona -> !persona.equals(actor))) {
            throw AudienciaRechazadaException.vacia();
        }
        return audiencia;
    }

    /**
     * Lo mismo que {@link #armar} sin guardar nada ni rechazar por vacía: es «Llega a N personas» del formulario (contrato §2.3). La foto del
     * semáforo es la de ahora; nada queda escrito.
     */
    ResumenDeAudiencia vistaPrevia(UserId actor, RolUsuario rol, Formacion formacion, List<ParteDeAudiencia> partes) {
        UserId mentor = rol == RolUsuario.MENTOR ? actor : null;
        Armado armado = armado(actor, rol, formacion, partes, mentor, AudienciaElegida.vacia(), true, clock.now());
        if (!armado.rechazados.isEmpty()) {
            throw AudienciaRechazadaException.fueraDeTuAlcance(List.copyOf(armado.rechazados));
        }
        Set<UserId> todos = miembros(formacion, armado.audiencia());
        todos.remove(actor);
        return new ResumenDeAudiencia(resumenDePartes(formacion, armado.audiencia(), false), todos.size());
    }

    private Armado armado(UserId actor, RolUsuario rol, Formacion formacion, List<ParteDeAudiencia> partes, UserId mentorDeLosAlumnos,
                          AudienciaElegida previa, boolean recalcular, Instant ahora) {
        if (partes == null || partes.isEmpty()) {
            throw AudienciaRechazadaException.invalida("Elige para quién es el evento.");
        }
        Set<TipoDeParte> permitidas = EnumSet.noneOf(TipoDeParte.class);
        permitidas.addAll(QuienConvoca.partesDe(rol));
        // Quien edita el evento de otra persona (el líder, Administración) conserva las partes que ese evento ya tenía.
        previa.reglas().forEach(r -> permitidas.add(r.tipo()));
        if (!previa.invitadosDe(OrigenDelInvitado.PERSONA).isEmpty()) {
            permitidas.add(TipoDeParte.PERSONA);
        }
        Alcance alcance = new Alcance(actor, rol, formacion, mentorDeLosAlumnos, previa);
        Armado armado = new Armado();
        for (ParteDeAudiencia parte : partes) {
            if (!permitidas.contains(parte.tipo())) {
                throw new AudienciaRechazadaException(AudienciaRechazadaException.FUERA_DE_TU_ALCANCE,
                        "No puedes elegir esa opción para un evento.", List.of());
            }
            switch (parte.tipo()) {
                case PERSONA -> armado.persona(parte.id(), alcance.personasQuePuede().contains(UserId.of(parte.id())),
                        OrigenDelInvitado.PERSONA);
                case PERSONAL -> {
                    if (parte.id() == null) {
                        armado.regla(ReglaDeAudiencia.todoElPersonal());
                    } else {
                        armado.persona(parte.id(), alcance.personal().contains(UserId.of(parte.id())), OrigenDelInvitado.PERSONAL);
                    }
                }
                case GRUPO -> {
                    if (alcance.grupos().containsKey(parte.id()) || previa.reglasDe(TipoDeParte.GRUPO).stream()
                            .anyMatch(r -> parte.id().equals(r.grupoId()))) {
                        armado.regla(ReglaDeAudiencia.grupo(parte.id()));
                    } else {
                        armado.rechazados.add(parte.id());
                    }
                }
                case MIS_ALUMNOS -> {
                    if (mentorDeLosAlumnos == null) {
                        throw AudienciaRechazadaException.invalida("«Mis alumnos» es de quien acompaña un grupo.");
                    }
                    armado.regla(ReglaDeAudiencia.alumnosDe(mentorDeLosAlumnos));
                }
                case MENTORES -> armado.regla(ReglaDeAudiencia.mentores());
                case COLOR -> armado.color(parte.color(), alcance, previa, recalcular, ahora);
            }
        }
        return armado;
    }

    /** Lo que quien guarda puede elegir, leído una sola vez y solo si hace falta. */
    private final class Alcance {

        private final UserId actor;
        private final RolUsuario rol;
        private final Formacion formacion;
        private final UserId mentorDeLosAlumnos;
        private final AudienciaElegida previa;
        private Set<UserId> personas;
        private Set<UserId> personal;
        private Map<UUID, GrupoConvocable> grupos;
        private Set<UserId> conSemaforo;

        private Alcance(UserId actor, RolUsuario rol, Formacion formacion, UserId mentorDeLosAlumnos, AudienciaElegida previa) {
            this.actor = actor;
            this.rol = rol;
            this.formacion = formacion;
            this.mentorDeLosAlumnos = mentorDeLosAlumnos;
            this.previa = previa;
        }

        /** El mentor y la guía, sus alumnos; el líder y Administración, las personas de la formación. Más quienes ya estaban invitados. */
        Set<UserId> personasQuePuede() {
            if (personas == null) {
                personas = new LinkedHashSet<>(rol == RolUsuario.MENTOR ? padron.alumnosDe(actor) : padron.personasDeLaFormacion(formacion));
                previa.invitadosDe(OrigenDelInvitado.PERSONA).forEach(i -> personas.add(i.persona()));
            }
            return personas;
        }

        Set<UserId> personal() {
            if (personal == null) {
                personal = padron.personalActivo();
            }
            return personal;
        }

        Map<UUID, GrupoConvocable> grupos() {
            if (grupos == null) {
                grupos = padron.gruposDe(formacion).stream().collect(Collectors.toMap(GrupoConvocable::id, g -> g, (a, b) -> a));
            }
            return grupos;
        }

        /** A quiénes se les mira el color: los alumnos del mentor que creó el evento, o los aprendices de la formación. */
        Set<UserId> candidatosAlColor() {
            if (conSemaforo == null) {
                conSemaforo = mentorDeLosAlumnos != null
                        ? padron.alumnosDe(mentorDeLosAlumnos)
                        : padron.aprendicesDeLaFormacion(formacion);
            }
            return conSemaforo;
        }
    }

    private final class Armado {

        private final List<ReglaDeAudiencia> reglas = new ArrayList<>();
        private final Map<String, Invitado> invitados = new LinkedHashMap<>();
        private final Set<UUID> rechazados = new LinkedHashSet<>();
        private Map<UserId, ColorDeAudiencia> coloresDeHoy;

        void persona(UUID id, boolean permitida, OrigenDelInvitado origen) {
            if (!permitida) {
                rechazados.add(id);
                return;
            }
            Invitado invitado = new Invitado(UserId.of(id), origen, null);
            invitados.putIfAbsent(id + "|" + origen, invitado);
        }

        void regla(ReglaDeAudiencia regla) {
            if (!reglas.contains(regla)) {
                reglas.add(regla);
            }
        }

        void color(ColorDeAudiencia color, Alcance alcance, AudienciaElegida previa, boolean recalcular, Instant ahora) {
            if (reglas.stream().anyMatch(r -> r.tipo() == TipoDeParte.COLOR && r.color() == color)) {
                return;
            }
            Optional<ReglaDeAudiencia> foto = recalcular ? Optional.empty() : previa.fotoDe(color);
            if (foto.isPresent()) {
                reglas.add(foto.get());
                previa.invitadosDe(OrigenDelInvitado.COLOR).stream().filter(i -> i.color() == color)
                        .forEach(i -> invitados.putIfAbsent(i.persona().value() + "|COLOR", i));
                return;
            }
            if (coloresDeHoy == null) {
                coloresDeHoy = colores.colores(alcance.candidatosAlColor());
            }
            reglas.add(ReglaDeAudiencia.color(color, ahora));
            coloresDeHoy.forEach((persona, suColor) -> {
                if (suColor == color) {
                    invitados.putIfAbsent(persona.value() + "|COLOR", new Invitado(persona, OrigenDelInvitado.COLOR, color));
                }
            });
        }

        AudienciaElegida audiencia() {
            return new AudienciaElegida(reglas, List.copyOf(invitados.values()));
        }
    }

    // ─── Quién está dentro ───────────────────────────────────────────────────────

    /**
     * Las personas que hoy están en la audiencia del evento, cada una una vez y sin quien lo creó (FR-014). Lo de una formación se filtra por la
     * formación del evento (spec 024); el personal, no: no pertenece a una.
     */
    Set<UserId> miembros(Evento evento, AudienciaElegida audiencia) {
        return miembros(evento.formacion(), audiencia);
    }

    private Set<UserId> miembros(Formacion formacion, AudienciaElegida audiencia) {
        Set<UserId> deLaFormacion = new LinkedHashSet<>();
        Set<UserId> personal = new LinkedHashSet<>();
        for (Invitado invitado : audiencia.invitados()) {
            (invitado.origen() == OrigenDelInvitado.PERSONAL ? personal : deLaFormacion).add(invitado.persona());
        }
        for (ReglaDeAudiencia regla : audiencia.reglas()) {
            switch (regla.tipo()) {
                case GRUPO -> deLaFormacion.addAll(celulas.miembrosActivos(regla.grupoId()));
                case MIS_ALUMNOS -> deLaFormacion.addAll(padron.alumnosDe(regla.mentorId()));
                case MENTORES -> deLaFormacion.addAll(padron.mentoresDeLaFormacion(formacion));
                case PERSONAL -> personal.addAll(padron.personalActivo());
                case COLOR, PERSONA -> {
                    // El color vive en la lista fija (invitados); una persona suelta no es una regla.
                }
            }
        }
        Set<UserId> resultado = new LinkedHashSet<>(dentroDeLaFormacion(deLaFormacion, formacion));
        resultado.addAll(personal);
        return resultado;
    }

    private Collection<UserId> dentroDeLaFormacion(Set<UserId> candidatos, Formacion formacion) {
        if (candidatos.isEmpty()) {
            return candidatos;
        }
        Set<UserId> pertenecen = formaciones.dentroDelAlcance(candidatos, AlcanceDeFormacion.de(formacion));
        return candidatos.stream().filter(pertenecen::contains).toList();
    }

    /**
     * Si una persona está hoy en la audiencia (para ver el evento y para despachar sus avisos, FR-013 y FR-023). Barato: no arma la audiencia entera.
     * La formación ya la filtró quien llama (el alcance del visor).
     */
    boolean incluye(Evento evento, AudienciaElegida audiencia, UserId persona, RolUsuario rolPersona) {
        if (persona.equals(evento.creadoPor())) {
            return true;
        }
        if (audiencia.personasFijas().contains(persona)) {
            return true;
        }
        for (ReglaDeAudiencia regla : audiencia.reglas()) {
            boolean dentro = switch (regla.tipo()) {
                case GRUPO -> pertenencia.perteneceHoy(regla.grupoId(), persona);
                case MIS_ALUMNOS -> padron.esAlumnoDe(persona, regla.mentorId());
                case MENTORES -> rolPersona == RolUsuario.MENTOR;
                case PERSONAL -> rolPersona == RolUsuario.ADMIN || rolPersona == RolUsuario.ALCHEMIST;
                case COLOR, PERSONA -> false;
            };
            if (dentro) {
                return true;
            }
        }
        return false;
    }

    // ─── Para quien lo creó ──────────────────────────────────────────────────────

    /** «Para quién es», con las cifras de hoy (FR-018). */
    ResumenDeAudiencia resumen(Evento evento, AudienciaElegida audiencia) {
        Set<UserId> todos = miembros(evento, audiencia);
        if (evento.creadoPor() != null) {
            todos.remove(evento.creadoPor());
        }
        return new ResumenDeAudiencia(resumenDePartes(evento.formacion(), audiencia, true), todos.size());
    }

    private List<ResumenDeAudiencia.Parte> resumenDePartes(Formacion formacion, AudienciaElegida audiencia, boolean conNombresDeGrupo) {
        List<ResumenDeAudiencia.Parte> partes = new ArrayList<>();
        Map<UUID, String> nombresDeGrupos = audiencia.tiene(TipoDeParte.GRUPO) && conNombresDeGrupo
                ? padron.gruposDe(formacion).stream().collect(Collectors.toMap(GrupoConvocable::id, GrupoConvocable::nombre, (a, b) -> a))
                : Map.of();
        for (ReglaDeAudiencia regla : audiencia.reglas()) {
            switch (regla.tipo()) {
                case COLOR -> partes.add(new ResumenDeAudiencia.Parte(TipoDeParte.COLOR, null, null, regla.color(),
                        (int) audiencia.invitadosDe(OrigenDelInvitado.COLOR).stream().filter(i -> i.color() == regla.color()).count(),
                        regla.resueltoEn(), List.of()));
                case GRUPO -> partes.add(new ResumenDeAudiencia.Parte(TipoDeParte.GRUPO, regla.grupoId(), nombresDeGrupos.get(regla.grupoId()),
                        null, dentroDeLaFormacion(new LinkedHashSet<>(celulas.miembrosActivos(regla.grupoId())), formacion).size(), null,
                        List.of()));
                case MIS_ALUMNOS -> partes.add(new ResumenDeAudiencia.Parte(TipoDeParte.MIS_ALUMNOS, null, null, null,
                        padron.alumnosDe(regla.mentorId()).size(), null, List.of()));
                case MENTORES -> partes.add(new ResumenDeAudiencia.Parte(TipoDeParte.MENTORES, null, null, null,
                        padron.mentoresDeLaFormacion(formacion).size(), null, List.of()));
                case PERSONAL -> partes.add(new ResumenDeAudiencia.Parte(TipoDeParte.PERSONAL, null, null, null,
                        padron.personalActivo().size(), null, List.of()));
                case PERSONA -> {
                    // no es una regla
                }
            }
        }
        agregarPorNombre(partes, audiencia, OrigenDelInvitado.PERSONA, TipoDeParte.PERSONA);
        agregarPorNombre(partes, audiencia, OrigenDelInvitado.PERSONAL, TipoDeParte.PERSONAL);
        return partes;
    }

    private void agregarPorNombre(List<ResumenDeAudiencia.Parte> partes, AudienciaElegida audiencia, OrigenDelInvitado origen, TipoDeParte tipo) {
        List<UserId> ids = audiencia.invitadosDe(origen).stream().map(Invitado::persona).toList();
        if (ids.isEmpty()) {
            return;
        }
        Map<UserId, Convocado> fichas = padron.fichas(ids);
        List<PersonaConvocable> personas = ids.stream().map(fichas::get).filter(java.util.Objects::nonNull)
                .map(AudienciaElegidaService::aPersona).toList();
        partes.add(new ResumenDeAudiencia.Parte(tipo, null, null, null, personas.size(), null, personas));
    }

    static PersonaConvocable aPersona(Convocado c) {
        return new PersonaConvocable(c.id().value(), c.nombre(), c.fotoUrl(), c.rol(), c.grupo());
    }
}

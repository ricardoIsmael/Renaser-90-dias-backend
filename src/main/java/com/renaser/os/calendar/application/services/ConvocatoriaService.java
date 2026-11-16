package com.renaser.os.calendar.application.services;

import com.renaser.os.calendar.application.ports.in.audiencia.ConvocatoriaUseCase;
import com.renaser.os.calendar.application.ports.in.audiencia.PersonaConvocable;
import com.renaser.os.calendar.application.ports.in.audiencia.ResumenDeAudiencia;
import com.renaser.os.calendar.application.ports.out.audiencia.AudienciaElegidaPort;
import com.renaser.os.calendar.application.ports.out.audiencia.ColoresDelSemaforoPort;
import com.renaser.os.calendar.application.ports.out.audiencia.PadronDeConvocatoriaPort;
import com.renaser.os.calendar.application.ports.out.audiencia.PadronDeConvocatoriaPort.Convocado;
import com.renaser.os.calendar.domain.model.evento.Evento;
import com.renaser.os.calendar.domain.model.evento.RolUsuario;
import com.renaser.os.calendar.domain.model.evento.TipoAudiencia;
import com.renaser.os.calendar.domain.model.evento.audiencia.ColorDeAudiencia;
import com.renaser.os.calendar.domain.model.evento.audiencia.ParteDeAudiencia;
import com.renaser.os.calendar.domain.model.evento.audiencia.QuienConvoca;
import com.renaser.os.calendar.domain.model.evento.audiencia.TipoDeParte;
import com.renaser.os.shared.application.Interruptores;
import com.renaser.os.shared.domain.Formacion;
import com.renaser.os.shared.domain.FormacionDelContenido;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import org.springframework.stereotype.Service;

import java.text.Collator;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * El paso «¿Para quién es?» (spec 044): qué puede elegir cada rol, el buscador de personas, «Llega a N personas» y lo que el detalle dice de la
 * audiencia. El servidor lo decide todo: la app no tiene listas de roles (FR-029).
 */
@Service
class ConvocatoriaService implements ConvocatoriaUseCase {

    static final int TAMANO_MAXIMO = 50;
    private static final Collator POR_NOMBRE = Collator.getInstance(Locale.forLanguageTag("es-PE"));

    static {
        POR_NOMBRE.setStrength(Collator.PRIMARY);
    }

    private final AccesoEventoService acceso;
    private final AudienciaElegidaService audienciaElegida;
    private final AudienciaElegidaPort audienciaPort;
    private final PadronDeConvocatoriaPort padron;
    private final ColoresDelSemaforoPort colores;
    private final Interruptores interruptores;

    ConvocatoriaService(AccesoEventoService acceso, AudienciaElegidaService audienciaElegida, AudienciaElegidaPort audienciaPort,
                        PadronDeConvocatoriaPort padron, ColoresDelSemaforoPort colores, Interruptores interruptores) {
        this.acceso = acceso;
        this.audienciaElegida = audienciaElegida;
        this.audienciaPort = audienciaPort;
        this.padron = padron;
        this.colores = colores;
        this.interruptores = interruptores;
    }

    @Override
    public Opciones opciones(UserId actor, Formacion pedida) {
        RolUsuario rol = acceso.requireProgreso(actor).rol();
        boolean administra = QuienConvoca.administra(rol);
        boolean convoca = QuienConvoca.puedeCrear(rol) && interruptores.prendidaPara(EventoService.CLAVE_CONVOCAR, actor);
        boolean gestiona = administra && interruptores.prendidaPara(EventoService.CLAVE_GESTION, actor);
        if (!convoca && !gestiona) {
            return new Opciones(false, Set.of(), Set.of(), null, false, Map.of(), 0, List.of(), List.of());
        }
        Set<TipoDeParte> partes = convoca ? QuienConvoca.partesDe(rol) : EnumSet.noneOf(TipoDeParte.class);
        Formacion formacion = formacionDe(actor, pedida);
        Map<String, Integer> porColor = new LinkedHashMap<>();
        java.time.LocalDate fechaDelSemaforo = null;
        if (partes.contains(TipoDeParte.COLOR)) {
            for (ColorDeAudiencia color : ColorDeAudiencia.values()) {
                porColor.put(color.name(), 0);
            }
            var lectura = colores.leer(rol == RolUsuario.MENTOR ? audienciaElegida.alumnosEnLaFormacion(actor, formacion) : padron.aprendicesDeLaFormacion(formacion));
            lectura.colores().values().forEach(c -> porColor.merge(c.name(), 1, Integer::sum));
            fechaDelSemaforo = lectura.fecha();
        }
        int misAlumnos = partes.contains(TipoDeParte.MIS_ALUMNOS) ? audienciaElegida.alumnosEnLaFormacion(actor, formacion).size() : 0;
        List<Grupo> grupos = partes.contains(TipoDeParte.GRUPO)
                ? padron.gruposDe(formacion).stream().map(g -> new Grupo(g.id(), g.nombre(), g.integrantes().size()))
                        .sorted(Comparator.comparing(Grupo::nombre, POR_NOMBRE)).toList()
                : List.of();
        List<PersonaConvocable> personal = partes.contains(TipoDeParte.PERSONAL)
                ? ordenadas(padron.fichas(padron.personalActivo()).values().stream().filter(c -> !c.id().equals(actor)).toList())
                : List.of();
        TipoDeParte porDefecto = rol == RolUsuario.MENTOR ? TipoDeParte.MIS_ALUMNOS : null;
        return new Opciones(true, partes, QuienConvoca.tiposDe(rol), porDefecto, administra, porColor, misAlumnos, grupos, personal, fechaDelSemaforo);
    }

    @Override
    public PaginaDePersonas personas(UserId actor, Formacion pedida, String texto, int pagina, int tamano) {
        RolUsuario rol = requireConvoca(actor);
        Formacion formacion = formacionDe(actor, pedida);
        Set<UserId> alcance = rol == RolUsuario.MENTOR ? audienciaElegida.alumnosEnLaFormacion(actor, formacion) : padron.personasDeLaFormacion(formacion);
        Set<UserId> candidatos = new java.util.HashSet<>(alcance);
        if (texto != null && !texto.isBlank()) {
            candidatos.retainAll(padron.buscar(texto));
        }
        candidatos.remove(actor);
        List<PersonaConvocable> todas = ordenadas(padron.fichas(candidatos).values().stream().toList());
        int porPagina = Math.clamp(tamano <= 0 ? 30 : tamano, 1, TAMANO_MAXIMO);
        int desde = Math.max(0, pagina) * porPagina;
        List<PersonaConvocable> estaPagina = desde >= todas.size() ? List.of()
                : todas.subList(desde, Math.min(todas.size(), desde + porPagina));
        return new PaginaDePersonas(estaPagina, Math.max(0, pagina), desde + porPagina < todas.size());
    }

    @Override
    public ResumenDeAudiencia vistaPrevia(UserId actor, Formacion pedida, List<ParteDeAudiencia> partes) {
        RolUsuario rol = requireConvoca(actor);
        return audienciaElegida.vistaPrevia(actor, rol, formacionDe(actor, pedida), partes);
    }

    @Override
    public Optional<ResumenDeAudiencia> resumenPara(UserId actor, Evento evento) {
        if (evento.tipoAudiencia() != TipoAudiencia.SELECCION || !acceso.gestiona(actor, evento)) {
            return Optional.empty();
        }
        return Optional.of(audienciaElegida.resumen(evento, audienciaPort.de(evento.id())));
    }

    @Override
    public Optional<PersonaConvocable> organizador(Evento evento) {
        if (evento.creadoPor() == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(padron.fichas(List.of(evento.creadoPor())).get(evento.creadoPor())).map(AudienciaElegidaService::aPersona);
    }

    private RolUsuario requireConvoca(UserId actor) {
        RolUsuario rol = acceso.requireProgreso(actor).rol();
        if (!QuienConvoca.puedeCrear(rol)) {
            throw new NotAuthorizedException("No tienes permiso para crear eventos");
        }
        interruptores.exigirPrendidaPara(EventoService.CLAVE_CONVOCAR, actor);
        return rol;
    }

    /** La formación en la que nace el evento: la del personal, o la que eligió Administración (la Escuela si no eligió, para las cifras). */
    private Formacion formacionDe(UserId actor, Formacion pedida) {
        try {
            return FormacionDelContenido.paraCrear(acceso.alcanceDe(actor, null), pedida);
        } catch (IllegalArgumentException veVarias) {
            return Formacion.POR_DEFECTO;
        }
    }

    private static List<PersonaConvocable> ordenadas(List<Convocado> convocados) {
        return convocados.stream().filter(Objects::nonNull)
                .sorted(Comparator.comparing(c -> c.nombre() == null ? "" : c.nombre(), POR_NOMBRE))
                .map(AudienciaElegidaService::aPersona).toList();
    }
}

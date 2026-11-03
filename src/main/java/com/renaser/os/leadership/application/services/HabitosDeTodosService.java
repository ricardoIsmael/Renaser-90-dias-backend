package com.renaser.os.leadership.application.services;

import com.renaser.os.evidence.api.EntregaDeEvidencia;
import com.renaser.os.evidence.api.EntregasPorRegistroFinder;
import com.renaser.os.habits.api.CumplimientoDelDiaFinder;
import com.renaser.os.habits.api.CumplimientoDelDiaFinder.RegistroDelDia;
import com.renaser.os.habits.api.HorarioDelDiaFinder;
import com.renaser.os.leadership.application.ports.in.ConsultarHabitosDeTodosUseCase;
import com.renaser.os.leadership.application.services.PersonasDelPrograma.Alumno;
import com.renaser.os.leadership.domain.model.cumplimiento.BusquedaPorNombre;
import com.renaser.os.leadership.domain.model.habitos.EstadoEnElDia;
import com.renaser.os.leadership.domain.model.habitos.ResumenDeUnHabito;
import com.renaser.os.leadership.domain.model.habitos.VentanaDeLosHabitos;
import com.renaser.os.shared.domain.AlcanceDeFormacion;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.Formacion;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.AlcanceDeFormacionFinder;
import com.renaser.os.users.api.ParticipacionPrograma;
import com.renaser.os.users.api.ParticipacionProgramaFinder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * «Hábitos de todos» (spec 029): para un día, por cada hábito del catálogo de las formaciones del líder, quién lo cumplió y quién no. Las cifras salen
 * de {@code registros_habito}, la misma fuente de la ficha, leída UNA vez para todo el padrón (FR-014, FR-017).
 *
 * <p>Autoriza primero ({@link AccesoDeLiderazgo}: MENTOR_LEAD, ADMIN o ALCHEMIST con la cuenta activa; el mentor, la guía, el aprendiz y Soporte TI reciben 403)
 * y lee después. Todo queda dentro del alcance de formación de quien pide (spec 022): los alumnos, los registros y el acceso por id a un hábito; uno de otra
 * formación responde 404, igual que uno inexistente. No registra consulta de nadie (FR-015).
 */
@Service
public class HabitosDeTodosService implements ConsultarHabitosDeTodosUseCase {

    /** La zona por defecto del padrón: con ella se decide qué es «hoy» para acotar la ventana (cada alumno cierra su día en su zona). */
    static final ZoneId ZONA_DE_REFERENCIA = ZoneId.of("America/Lima");
    private static final int TAMANO_MAXIMO = 100;
    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm");

    private final AccesoDeLiderazgo acceso;
    private final PersonasDelPrograma programa;
    private final CumplimientoDelDiaFinder cumplimiento;
    private final HorarioDelDiaFinder horarios;
    private final EntregasPorRegistroFinder entregas;
    private final ParticipacionProgramaFinder participaciones;
    private final AlcanceDeFormacionFinder formaciones;
    private final Clock clock;

    HabitosDeTodosService(AccesoDeLiderazgo acceso, PersonasDelPrograma programa, CumplimientoDelDiaFinder cumplimiento,
                          HorarioDelDiaFinder horarios, EntregasPorRegistroFinder entregas,
                          ParticipacionProgramaFinder participaciones, AlcanceDeFormacionFinder formaciones, Clock clock) {
        this.acceso = acceso;
        this.programa = programa;
        this.cumplimiento = cumplimiento;
        this.horarios = horarios;
        this.entregas = entregas;
        this.participaciones = participaciones;
        this.formaciones = formaciones;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public HabitosDeTodos resumen(ConsultaHabitosDeTodos consulta) {
        acceso.requireLiderazgoActivo(consulta.actorId());
        VentanaDeLosHabitos ventana = VentanaDeLosHabitos.de(clock.now().atZone(ZONA_DE_REFERENCIA).toLocalDate());
        LocalDate fecha = ventana.fechaElegida(consulta.fecha());
        Dia dia = leer(consulta.actorId(), consulta.formacion(), consulta.grupo(), consulta.categoria(), fecha);

        List<HabitoDelDia> habitos = dia.porHabito().values().stream()
                .map(HabitosDeTodosService::resumenDe).flatMap(Optional::stream)
                .sorted(ResumenDeUnHabito.PEOR_PRIMERO).map(HabitosDeTodosService::aHabitoDelDia).toList();
        return new HabitosDeTodos(fecha, ventana.esHoy(fecha), ventana.masAntigua(), ventana.masReciente(), totales(dia, habitos), habitos);
    }

    @Override
    @Transactional(readOnly = true)
    public PersonasDeUnHabito personas(ConsultaPersonasDeUnHabito consulta) {
        acceso.requireLiderazgoActivo(consulta.actorId());
        VentanaDeLosHabitos ventana = VentanaDeLosHabitos.de(clock.now().atZone(ZONA_DE_REFERENCIA).toLocalDate());
        LocalDate fecha = ventana.fechaElegida(consulta.fecha());
        Dia dia = leer(consulta.actorId(), consulta.formacion(), consulta.grupo(), null, fecha);

        List<RegistroDelDia> delHabito = dia.porHabito().get(consulta.habitoId());
        // Un hábito que nadie tiene ese día en este alcance (o de otra formación) es un 404, como uno que no existe.
        ResumenDeUnHabito resumen = delHabito == null ? null : resumenDe(delHabito).orElse(null);
        if (resumen == null) {
            throw new NoSuchElementException("Ese hábito no está disponible");
        }
        Map<UserId, RegistroDelDia> registroDe = delHabito.stream().collect(Collectors.toMap(RegistroDelDia::personaId, Function.identity(), (a, b) -> a));
        List<Alumno> coinciden = dia.alumnos().stream()
                .filter(alumno -> BusquedaPorNombre.coincide(alumno.perfil().fullName(), consulta.busqueda())).toList();
        List<Fila> ordenadas = coinciden.stream().map(alumno -> new Fila(alumno, registroDe.get(alumno.perfil().id())))
                .sorted(Comparator.comparingInt(Fila::orden)
                        .thenComparing(fila -> fila.alumno().perfil().fullName(), BusquedaPorNombre.porNombre())
                        .thenComparing(fila -> fila.alumno().perfil().id().value()))
                .toList();

        int tamano = Math.max(1, Math.min(TAMANO_MAXIMO, consulta.tamano()));
        int pagina = Math.max(0, consulta.pagina());
        long desde = (long) pagina * tamano;
        List<Fila> deLaPagina = desde >= ordenadas.size() ? List.of()
                : ordenadas.subList((int) desde, (int) Math.min(ordenadas.size(), desde + tamano));
        return new PersonasDeUnHabito(fecha, ventana.esHoy(fecha), aHabitoDelDia(resumen), filas(deLaPagina, consulta.habitoId(), fecha),
                pagina, tamano, ordenadas.size());
    }

    // ── Lectura común ──────────────────────────────────────────────────────

    /** Los alumnos con esos filtros y los registros de ESE día de los hábitos del catálogo de su alcance, agrupados por hábito. */
    private Dia leer(UserId actor, Formacion formacion, String grupo, String categoria, LocalDate fecha) {
        AlcanceDeFormacion alcance = formaciones.de(actor).acotadoA(formacion);
        List<Alumno> alumnos = programa.alumnos(alcance, clock.now()).stream().filter(alumno -> delGrupo(alumno, grupo)).toList();
        Set<UserId> ids = alumnos.stream().map(alumno -> alumno.perfil().id()).collect(Collectors.toSet());
        Map<UUID, List<RegistroDelDia>> porHabito = cumplimiento.deVariasPersonas(ids, fecha, alcance).stream()
                .filter(registro -> categoria == null || categoria.isBlank() || categoria.equals(registro.categoriaClave()))
                .collect(Collectors.groupingBy(RegistroDelDia::habitoId, HashMap::new, Collectors.toList()));
        return new Dia(alumnos, porHabito);
    }

    private static boolean delGrupo(Alumno alumno, String filtro) {
        if (filtro == null || filtro.isBlank()) {
            return true;
        }
        if (SIN_GRUPO.equals(filtro)) {
            return alumno.grupo() == null;
        }
        return alumno.grupo() != null && alumno.grupo().grupoId().toString().equalsIgnoreCase(filtro.trim());
    }

    private static EstadoEnElDia estadoDe(RegistroDelDia registro) {
        return EstadoEnElDia.de(registro.estado().cumplido(), registro.estado().vencidoSinCumplir(), registro.opcional());
    }

    /** Vacío si a nadie le tocó (todos eran opcionales sin hacer): ese hábito no aparece (FR-008). */
    private static Optional<ResumenDeUnHabito> resumenDe(List<RegistroDelDia> registros) {
        int cumplieron = 0;
        int pendientes = 0;
        int sinCumplir = 0;
        for (RegistroDelDia registro : registros) {
            switch (estadoDe(registro)) {
                case CUMPLIDO -> cumplieron++;
                case PENDIENTE -> pendientes++;
                case SIN_CUMPLIR -> sinCumplir++;
                case SIN_DATOS -> { }
            }
        }
        if (cumplieron + pendientes + sinCumplir == 0) {
            return Optional.empty();
        }
        RegistroDelDia primero = registros.get(0);
        return Optional.of(new ResumenDeUnHabito(primero.habitoId(), primero.titulo(), primero.categoriaClave(), primero.categoriaCodigo(),
                primero.formacion(), cumplieron, pendientes, sinCumplir));
    }

    private static HabitoDelDia aHabitoDelDia(ResumenDeUnHabito resumen) {
        return new HabitoDelDia(resumen.habitoId(), resumen.titulo(), resumen.categoriaClave(), resumen.categoriaCodigo(),
                resumen.formacion(), resumen.esperados(), resumen.cumplieron(), resumen.pendientes(), resumen.sinCumplir());
    }

    private static Totales totales(Dia dia, List<HabitoDelDia> habitos) {
        Set<UserId> conHabitos = dia.porHabito().values().stream().flatMap(List::stream)
                .filter(registro -> estadoDe(registro).cuentaComoEsperado()).map(RegistroDelDia::personaId).collect(Collectors.toSet());
        return new Totales(dia.alumnos().size(), conHabitos.size(), habitos.stream().mapToInt(HabitoDelDia::esperados).sum(),
                habitos.stream().mapToInt(HabitoDelDia::cumplieron).sum(), habitos.stream().mapToInt(HabitoDelDia::pendientes).sum(),
                habitos.stream().mapToInt(HabitoDelDia::sinCumplir).sum());
    }

    // ── Filas de personas (solo la página que se pide) ─────────────────────

    private List<FilaDePersona> filas(List<Fila> pagina, UUID habitoId, LocalDate fecha) {
        Map<UUID, EntregaDeEvidencia> evidencias = entregas.porRegistros(pagina.stream().map(Fila::registro)
                .filter(registro -> registro != null && registro.estado().cumplido()).map(RegistroDelDia::registroId).toList());
        // Auditoria de consultas del 2026-11-02 (H-01): el programa de cada persona y su hora de ESTE habito se piden UNA vez para toda la pagina. Antes eran
        // `deParticipante` y `deFecha` (unas 20 consultas) por fila: ~630 por pagina de 30 y ~2.100 por una de 100.
        Map<UserId, ParticipacionPrograma> programas = participaciones.deParticipantes(pagina.stream().map(fila -> fila.alumno().perfil().id()).toList());
        Map<UserId, LocalTime> horas = horarios.horaDeUnHabito(pagina.stream().filter(fila -> fila.registro() != null)
                .map(fila -> fila.alumno().perfil().id()).toList(), habitoId, fecha);
        List<FilaDePersona> filas = new ArrayList<>();
        for (Fila fila : pagina) {
            filas.add(filaDe(fila, fecha, evidencias, programas, horas));
        }
        return filas;
    }

    private FilaDePersona filaDe(Fila fila, LocalDate fecha, Map<UUID, EntregaDeEvidencia> evidencias,
                                 Map<UserId, ParticipacionPrograma> programas, Map<UserId, LocalTime> horas) {
        var perfil = fila.alumno().perfil();
        var grupo = fila.alumno().grupo();
        UUID grupoId = grupo == null ? null : grupo.grupoId();
        String nombreDelGrupo = grupo == null ? null : grupo.nombre();
        Optional<ParticipacionPrograma> programaDeLaPersona = Optional.ofNullable(programas.get(perfil.id()));
        if (fila.registro() == null) {
            return new FilaDePersona(perfil.id().value(), perfil.fullName(), perfil.avatarUrl(), grupoId, nombreDelGrupo,
                    EstadoEnElDia.SIN_DATOS.name(), motivoSinRegistro(programaDeLaPersona.orElse(null), fecha), null, null, null);
        }
        EstadoEnElDia estado = estadoDe(fila.registro());
        String motivo = estado == EstadoEnElDia.SIN_DATOS ? "NO_EXIGIBLE" : null;
        ZoneId zona = programaDeLaPersona.map(ParticipacionPrograma::zona).orElse(ZONA_DE_REFERENCIA);
        String cumplioA = fila.registro().completadoEn() == null ? null : HORA.format(fila.registro().completadoEn().atZone(zona));
        EntregaDeEvidencia entrega = evidencias.get(fila.registro().registroId());
        return new FilaDePersona(perfil.id().value(), perfil.fullName(), perfil.avatarUrl(), grupoId, nombreDelGrupo, estado.name(),
                motivo, horas.get(perfil.id()), cumplioA, entrega == null ? null : entrega.evidenciaId());
    }

    /** Antes de su Día 1 («todavía no empezaba») o, si ya había empezado, «no le tocaba o estaba en pausa»: ninguno es un incumplimiento. */
    private static String motivoSinRegistro(ParticipacionPrograma programa, LocalDate fecha) {
        LocalDate diaUno = programa == null ? null : programa.diaUnoElegido();
        return diaUno == null || fecha.isBefore(diaUno) ? "ANTES_DEL_DIA_1" : "NO_LE_TOCABA";
    }

    private record Dia(List<Alumno> alumnos, Map<UUID, List<RegistroDelDia>> porHabito) {
    }

    private record Fila(Alumno alumno, RegistroDelDia registro) {

        int orden() {
            return registro == null ? EstadoEnElDia.SIN_DATOS.ordenEnLaLista() : estadoDe(registro).ordenEnLaLista();
        }
    }
}

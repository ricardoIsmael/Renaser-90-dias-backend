package com.renaser.os.mentoring.application.services;

import com.renaser.os.community.api.PublicacionMuroFinder;
import com.renaser.os.community.api.PublicacionMuroFinder.PublicacionDelAutor;
import com.renaser.os.evidence.api.EntregaDeEvidencia;
import com.renaser.os.evidence.api.EntregasPorRegistroFinder;
import com.renaser.os.habits.api.ObligacionHabito;
import com.renaser.os.habits.api.ObligacionesHistoricasFinder;
import com.renaser.os.mentoring.api.DiaDelAlumno;
import com.renaser.os.mentoring.api.EvidenciaDeObligacion;
import com.renaser.os.mentoring.api.ObligacionDelDia;
import com.renaser.os.mentoring.api.ResumenSemana;
import com.renaser.os.mentoring.api.SemanaDelAlumno;
import com.renaser.os.mentoring.api.SemanaDeSeguimientoFinder;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.ParticipacionPrograma;
import com.renaser.os.users.api.ParticipacionProgramaFinder;
import com.renaser.os.users.api.UserSummary;
import com.renaser.os.users.api.UserSummaryFinder;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;

/**
 * El armado de la semana de una persona, UNO solo para las tres fichas (V22): la del mentor y la de administración,
 * de lunes a domingo, y la del líder (spec 005, R-2), de sábado a viernes. La ventana entra como parámetro: antes
 * esta clase calculaba el lunes.
 *
 * <p>Sale de las obligaciones históricas —un snapshot por día, no una proyección del catálogo de hoy—, así que
 * reprogramar un hábito no reescribe la semana pasada. No autoriza: quien la llama ya decidió que el actor puede
 * mirar a esa persona ({@link SeguimientoService} para el mentor y el administrador, {@code leadership} para el
 * líder).
 *
 * <p>Sin {@code @Transactional} a propósito: es un componente sin proxy, y la transacción de solo lectura es de
 * quien lo llama.
 */
@Component
class ArmadoDeLaSemana implements SemanaDeSeguimientoFinder {

    private static final String COBERTURA_COMPLETA = "COMPLETA";
    private static final String COBERTURA_SIN_DATOS = "SIN_DATOS";

    private static final String ENTREGA_NO_REQUERIDA = "NO_REQUERIDA";
    private static final String ENTREGA_SIN_ENTREGA = "SIN_ENTREGA";
    private static final String ENTREGA_ENTREGADA = "ENTREGADA";

    private final ObligacionesHistoricasFinder obligacionesFinder;
    private final EntregasPorRegistroFinder entregasFinder;
    private final ParticipacionProgramaFinder participacionProgramaFinder;
    private final UserSummaryFinder userSummaryFinder;
    private final PublicacionMuroFinder publicacionMuroFinder;
    private final Clock clock;

    ArmadoDeLaSemana(ObligacionesHistoricasFinder obligacionesFinder, EntregasPorRegistroFinder entregasFinder,
                     ParticipacionProgramaFinder participacionProgramaFinder, UserSummaryFinder userSummaryFinder,
                     PublicacionMuroFinder publicacionMuroFinder, Clock clock) {
        this.obligacionesFinder = obligacionesFinder;
        this.entregasFinder = entregasFinder;
        this.participacionProgramaFinder = participacionProgramaFinder;
        this.userSummaryFinder = userSummaryFinder;
        this.publicacionMuroFinder = publicacionMuroFinder;
        this.clock = clock;
    }

    @Override
    public SemanaDelAlumno semanaDe(UserId personaId, UUID grupoId, LocalDate desde, LocalDate hasta) {
        if (desde.isAfter(hasta)) {
            throw new IllegalArgumentException("La semana empieza despues de terminar: " + desde + " > " + hasta);
        }
        ParticipacionPrograma participacion = participacionProgramaFinder.deParticipante(personaId)
                .orElseThrow(() -> new NoSuchElementException("Alumno no encontrado"));
        return armar(personaId, participacion, grupoId, desde, hasta, clock.now());
    }

    /**
     * La semana de {@code desde} a {@code hasta}, días locales de la persona: sus días son los que se están mirando,
     * no los del servidor ni los de quien mira.
     */
    SemanaDelAlumno armar(UserId alumnoId, ParticipacionPrograma participacion, UUID grupoId, LocalDate desde,
                          LocalDate hasta, Instant ahora) {
        ZoneId zona = participacion.zona();
        List<ObligacionHabito> obligaciones =
                obligacionesFinder.porParticipantesEntre(List.of(alumnoId), desde, hasta);
        Map<UUID, EntregaDeEvidencia> entregas =
                entregasFinder.porRegistros(obligaciones.stream().map(ObligacionHabito::registroId).toList());
        Map<LocalDate, PublicacionDelAutor> postsDelDia = primeraPublicacionPorDia(alumnoId, obligaciones, zona);

        String nombre = userSummaryFinder.findById(alumnoId).map(UserSummary::fullName).orElse(null);
        // Sin una sola obligacion en toda la semana no se afirma "no cumplio": puede que el
        // padron de ese periodo no se haya generado. Es SIN_DATOS, que es otra cosa.
        String cobertura = obligaciones.isEmpty() ? COBERTURA_SIN_DATOS : COBERTURA_COMPLETA;

        return new SemanaDelAlumno(grupoId, alumnoId.value(), nombre, desde, hasta,
                participacion.inscrito() ? participacion.diaPrograma() : null, zona.getId(),
                armarDias(desde, hasta, obligaciones, entregas, postsDelDia),
                resumir(obligaciones, entregas, postsDelDia), cobertura, ahora);
    }

    private List<DiaDelAlumno> armarDias(LocalDate desde, LocalDate hasta, List<ObligacionHabito> obligaciones,
                                         Map<UUID, EntregaDeEvidencia> entregas,
                                         Map<LocalDate, PublicacionDelAutor> postsDelDia) {
        Map<LocalDate, List<ObligacionDelDia>> porFecha = new LinkedHashMap<>();
        Map<LocalDate, Integer> diaDePrograma = new LinkedHashMap<>();
        for (LocalDate fecha = desde; !fecha.isAfter(hasta); fecha = fecha.plusDays(1)) {
            // Todos los dias de la ventana siempre estan, aunque vengan vacios: un dia sin obligaciones es un
            // dato ("no habia nada programado"), no un hueco que la pantalla deba adivinar.
            porFecha.put(fecha, new ArrayList<>());
        }
        for (ObligacionHabito obligacion : obligaciones) {
            porFecha.computeIfAbsent(obligacion.fecha(), f -> new ArrayList<>())
                    .add(aObligacionDelDia(obligacion, entregas.get(obligacion.registroId()),
                            publicacionDe(obligacion, postsDelDia)));
            diaDePrograma.putIfAbsent(obligacion.fecha(), obligacion.diaPrograma());
        }
        return porFecha.entrySet().stream()
                .map(e -> new DiaDelAlumno(e.getKey(), diaDePrograma.get(e.getKey()), e.getValue()))
                .toList();
    }

    /**
     * E-628: el post diario no sube evidencia —se cierra solo al publicar en el Muro—, así que la ficha lo mostraba
     * sin nada que ver. Su evidencia es la PRIMERA publicación de su día en la zona del alumno: la que lo cerró (la
     * política solo pregunta si publicó ese día, y las siguientes no cuentan). Se deriva y no se guarda: vale
     * también para los días anteriores a este arreglo. Una sola consulta por semana, y solo si hay post diario.
     */
    private Map<LocalDate, PublicacionDelAutor> primeraPublicacionPorDia(UserId alumnoId,
                                                                         List<ObligacionHabito> obligaciones,
                                                                         ZoneId zona) {
        List<LocalDate> dias = obligaciones.stream().filter(ObligacionHabito::seCumplePublicandoEnElMuro)
                .map(ObligacionHabito::fecha).sorted().toList();
        if (dias.isEmpty()) {
            return Map.of();
        }
        Instant desde = dias.getFirst().atStartOfDay(zona).toInstant();
        Instant hasta = dias.getLast().plusDays(1).atStartOfDay(zona).toInstant();
        Map<LocalDate, PublicacionDelAutor> primeras = new LinkedHashMap<>();
        for (PublicacionDelAutor publicacion : publicacionMuroFinder.delAutorEntre(alumnoId, desde, hasta)) {
            primeras.putIfAbsent(publicacion.creadaEn().atZone(zona).toLocalDate(), publicacion);
        }
        return primeras;
    }

    private static PublicacionDelAutor publicacionDe(ObligacionHabito obligacion,
                                                     Map<LocalDate, PublicacionDelAutor> postsDelDia) {
        return obligacion.seCumplePublicandoEnElMuro() ? postsDelDia.get(obligacion.fecha()) : null;
    }

    private static ObligacionDelDia aObligacionDelDia(ObligacionHabito obligacion, EntregaDeEvidencia entrega,
                                                      PublicacionDelAutor publicacion) {
        List<EvidenciaDeObligacion> evidencias = evidenciasDe(entrega);
        if (publicacion != null && entrega == null) {
            // La publicacion ES la entrega del post diario: sin archivo ni revision, con su hora.
            return new ObligacionDelDia(obligacion.registroId(), obligacion.titulo(), obligacion.estado().name(),
                    obligacion.requiereEvidencia(), ENTREGA_ENTREGADA, publicacion.creadaEn(), null, null,
                    publicacion.id(), obligacion.respuestaTexto(), evidencias);
        }
        return new ObligacionDelDia(obligacion.registroId(), obligacion.titulo(), obligacion.estado().name(),
                obligacion.requiereEvidencia(), estadoDeEntrega(obligacion, entrega),
                entrega == null ? null : entrega.primeraEntregaEn(),
                entrega == null ? null : entrega.estadoRevision().name(),
                entrega == null ? null : entrega.evidenciaId(),
                publicacion == null ? null : publicacion.id(), obligacion.respuestaTexto(), evidencias);
    }

    private static String estadoDeEntrega(ObligacionHabito obligacion, EntregaDeEvidencia entrega) {
        if (!obligacion.requiereEvidencia()) {
            // Hay habitos que se acreditan de otro modo. Pedirles archivo seria inventar una
            // obligacion que nunca existio (plan.md §7).
            return ENTREGA_NO_REQUERIDA;
        }
        return entrega == null ? ENTREGA_SIN_ENTREGA : ENTREGA_ENTREGADA;
    }

    private static List<EvidenciaDeObligacion> evidenciasDe(EntregaDeEvidencia entrega) {
        if (entrega == null) {
            return List.of();
        }
        return entrega.evidencias().stream()
                .map(archivo -> new EvidenciaDeObligacion(archivo.evidenciaId(), archivo.tipo().name(),
                        archivo.subidaEn()))
                .toList();
    }

    private static ResumenSemana resumir(List<ObligacionHabito> obligaciones,
                                         Map<UUID, EntregaDeEvidencia> entregas,
                                         Map<LocalDate, PublicacionDelAutor> postsDelDia) {
        int cumplidas = (int) obligaciones.stream().filter(o -> o.estado().cumplido()).count();
        int sinCumplir = (int) obligaciones.stream().filter(o -> o.estado().vencidoSinCumplir()).count();
        int conEntrega = (int) obligaciones.stream()
                .filter(o -> entregas.containsKey(o.registroId()) || publicacionDe(o, postsDelDia) != null).count();
        return new ResumenSemana(obligaciones.size(), cumplidas, conEntrega,
                obligaciones.size() - cumplidas - sinCumplir, sinCumplir);
    }
}

package com.renaser.os.leadership.domain.model.consulta;

import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.UserRole;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Cuándo sale y qué dice el resumen diario al mentor, «Tu líder revisó a tu grupo» (spec 005, FR-017 y FR-018, R-8).
 *
 * <p>Sale <b>una vez al día, a las 20:00 de la zona del mentor</b>, con las personas de sus grupos cuya ficha abrió el
 * <b>líder de mentores</b> ese día local. Administración y Alquimista abren la misma ficha y quedan registrados, pero no
 * entran acá: el aviso es del líder (FR-017). Sin consultas, no hay aviso. Nadie se repite.
 *
 * <p>Todo es puro: el reloj y la zona llegan como argumentos ({@code Clock} es de la capa de aplicación). El día es
 * el de la zona del mentor y no el de UTC: una consulta de las 23:59 de Lima ya es lunes en UTC y es del domingo.
 */
public final class ReglaDelResumenDeConsultas {

    /** La hora local en que el resumen sale. El barrido lo intenta dos veces en esa hora (minutos 10 y 40 UTC); la clave evita repetirlo. */
    static final int HORA_DEL_RESUMEN = 20;
    static final int NOMBRES_A_LA_VISTA = 3;
    private static final String PERSONA_SIN_NOMBRE = "Una persona";

    private ReglaDelResumenDeConsultas() {
    }

    /** Si ahora es la hora del resumen para un mentor de esa zona: de las 20:00 a las 20:59 locales. */
    public static boolean toca(Instant ahora, ZoneId zonaDelMentor) {
        return ahora.atZone(zonaDelMentor).getHour() == HORA_DEL_RESUMEN;
    }

    /** El día local del mentor: el de la clave de deduplicación y el que se resume. */
    public static LocalDate diaLocal(Instant instante, ZoneId zonaDelMentor) {
        return instante.atZone(zonaDelMentor).toLocalDate();
    }

    /** El instante en que empieza ese día local; el día termina donde empieza el siguiente. */
    public static Instant inicioDelDia(LocalDate dia, ZoneId zonaDelMentor) {
        return dia.atStartOfDay(zonaDelMentor).toInstant();
    }

    /**
     * Las personas de ese mentor a las que el líder les abrió la ficha ese día local, o vacío si no hay ninguna. Se
     * filtra aunque quien las leyó ya las haya filtrado: esta es la regla, no la consulta.
     */
    public static Optional<Resumen> resumir(DiaDelMentor dia, List<ConsultaDeFicha> consultas) {
        UserId mentor = dia.mentorId();
        List<ConsultaDeFicha> validas = consultas.stream()
                .filter(c -> c.rolConsultor() == UserRole.MENTOR_LEAD)
                .filter(c -> mentor.equals(c.mentorId()))
                .filter(c -> !mentor.equals(c.consultorId()) && !mentor.equals(c.personaId()))
                .filter(c -> diaLocal(c.consultadaEn(), dia.zona()).equals(dia.dia()))
                .sorted(Comparator.comparing(ConsultaDeFicha::consultadaEn))
                .toList();
        return validas.isEmpty() ? Optional.empty() : Optional.of(armar(validas));
    }

    private static Resumen armar(List<ConsultaDeFicha> validas) {
        Set<UserId> personas = validas.stream().map(ConsultaDeFicha::personaId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        return new Resumen(new ArrayList<>(personas), grupoConMasPersonas(validas));
    }

    /** El grupo en que más personas distintas se miraron; en empate, el de la primera consulta del día. */
    private static UUID grupoConMasPersonas(List<ConsultaDeFicha> validas) {
        Map<UUID, Set<UserId>> porGrupo = new LinkedHashMap<>();
        validas.forEach(c -> porGrupo.computeIfAbsent(c.grupoId(), grupo -> new LinkedHashSet<>()).add(c.personaId()));
        return porGrupo.entrySet().stream()
                .max(Comparator.comparingInt(entrada -> entrada.getValue().size()))
                .map(Map.Entry::getKey).orElseThrow();
    }

    /** Una por mentor y día local: repetir la corrida de la hora no crea otro aviso (el índice único de la bandeja). */
    public static UUID clave(UserId mentor, LocalDate diaLocal) {
        return UUID.nameUUIDFromBytes(("consultas-lider:" + mentor + ":" + diaLocal).getBytes(StandardCharsets.UTF_8));
    }

    /** «Hoy revisó a Ana, Luis y Pía» o, con más, «Hoy revisó a Ana, Luis, Pía y 2 más». Nunca más de tres nombres. */
    public static String cuerpo(List<String> nombres) {
        if (nombres.isEmpty()) {
            throw new IllegalArgumentException("El resumen necesita al menos una persona");
        }
        int aLaVista = Math.min(nombres.size(), NOMBRES_A_LA_VISTA);
        int sinNombrar = nombres.size() - aLaVista;
        List<String> primeros = nombres.subList(0, aLaVista);
        String cola = sinNombrar > 0 ? sinNombrar + " más" : null;
        return "Hoy revisó a " + unir(primeros, cola);
    }

    private static String unir(List<String> nombres, String cola) {
        if (cola != null) {
            return String.join(", ", nombres) + " y " + cola;
        }
        if (nombres.size() == 1) {
            return nombres.getFirst();
        }
        return String.join(", ", nombres.subList(0, nombres.size() - 1)) + " y " + nombres.getLast();
    }

    /** El aviso sale por push y no lleva apellidos: del nombre completo, solo el primero. */
    public static String primerNombre(String nombreCompleto) {
        if (nombreCompleto == null || nombreCompleto.isBlank()) {
            return PERSONA_SIN_NOMBRE;
        }
        return nombreCompleto.trim().split("\\s+")[0];
    }

    /** El mentor, su zona y el día local que se resume: lo que hace falta para saber qué consultas le tocan. */
    public record DiaDelMentor(UserId mentorId, ZoneId zona, LocalDate dia) {

        public DiaDelMentor {
            Objects.requireNonNull(mentorId, "mentorId es obligatorio");
            Objects.requireNonNull(zona, "zona es obligatoria");
            Objects.requireNonNull(dia, "dia es obligatorio");
        }

        /** El día local del mentor en el instante {@code ahora}: el de la corrida. */
        public static DiaDelMentor de(UserId mentorId, ZoneId zona, Instant ahora) {
            return new DiaDelMentor(mentorId, zona, diaLocal(ahora, zona));
        }

        public Instant desde() {
            return inicioDelDia(dia, zona);
        }

        public Instant hasta() {
            return inicioDelDia(dia.plusDays(1), zona);
        }

        public UUID clave() {
            return ReglaDelResumenDeConsultas.clave(mentorId, dia);
        }
    }

    /**
     * @param personas    quiénes, sin repetir y en el orden en que se miraron
     * @param grupoDeLaRuta el grupo al que lleva el aviso: «Mi grupo» de ese grupo
     */
    public record Resumen(List<UserId> personas, UUID grupoDeLaRuta) {

        public Resumen {
            personas = List.copyOf(personas);
        }
    }
}

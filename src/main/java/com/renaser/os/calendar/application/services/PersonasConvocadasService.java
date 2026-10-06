package com.renaser.os.calendar.application.services;

import com.renaser.os.calendar.application.ports.in.asistencia.PersonaConvocada;
import com.renaser.os.calendar.application.ports.out.asistencia.LoadListaDeAsistenciaPort;
import com.renaser.os.calendar.application.ports.out.confirmacion.HistorialDeRespuestasPort;
import com.renaser.os.calendar.application.ports.out.confirmacion.LoadConfirmacionPort;
import com.renaser.os.calendar.application.ports.out.persona.ConsultarPersonasPort;
import com.renaser.os.calendar.application.ports.out.persona.ConsultarPersonasPort.Persona;
import com.renaser.os.calendar.domain.model.asistencia.MarcaDeAsistencia;
import com.renaser.os.calendar.domain.model.asistencia.RespuestaAnterior;
import com.renaser.os.calendar.domain.model.confirmacion.Confirmacion;
import com.renaser.os.calendar.domain.model.evento.Evento;
import com.renaser.os.shared.domain.UserId;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Arma la lista de personas de una ocurrencia (D-256): la audiencia del evento (la MISMA de los recordatorios,
 * {@link AudienciaDelEventoService}), más quien respondió o fue marcado aunque ya no esté en ella. Cada fila
 * trae su respuesta vigente, su historial y su marca. Todo en lote: una consulta por tabla, sin N+1.
 */
@Component
class PersonasConvocadasService {

    private final AudienciaDelEventoService audiencia;
    private final LoadConfirmacionPort confirmaciones;
    private final HistorialDeRespuestasPort historial;
    private final LoadListaDeAsistenciaPort lista;
    private final ConsultarPersonasPort personas;

    PersonasConvocadasService(AudienciaDelEventoService audiencia, LoadConfirmacionPort confirmaciones,
                              HistorialDeRespuestasPort historial, LoadListaDeAsistenciaPort lista,
                              ConsultarPersonasPort personas) {
        this.audiencia = audiencia;
        this.confirmaciones = confirmaciones;
        this.historial = historial;
        this.lista = lista;
        this.personas = personas;
    }

    List<PersonaConvocada> de(Evento evento, Instant slot) {
        Map<UserId, Confirmacion> respuestas = confirmaciones.deOcurrencia(evento.id(), slot).stream()
                .collect(Collectors.toMap(Confirmacion::usuarioId, Function.identity(), (a, b) -> b));
        Map<UserId, MarcaDeAsistencia> marcas = lista.marcas(evento.id(), slot).stream()
                .collect(Collectors.toMap(MarcaDeAsistencia::usuarioId, Function.identity(), (a, b) -> b));
        Map<UserId, List<RespuestaAnterior>> historiales = historial.deOcurrencia(evento.id(), slot).stream()
                .collect(Collectors.groupingBy(RespuestaAnterior::usuarioId));

        Set<UserId> ids = new LinkedHashSet<>(audiencia.destinatarios(evento));
        ids.addAll(respuestas.keySet());
        ids.addAll(marcas.keySet());
        Map<UserId, Persona> fichas = personas.porIds(ids);

        return ids.stream()
                .filter(fichas::containsKey)
                .map(id -> fila(fichas.get(id), respuestas.get(id), historiales.getOrDefault(id, List.of()),
                        marcas.get(id)))
                .sorted(Comparator.comparing((PersonaConvocada p) -> p.nombre() == null ? "" : p.nombre(),
                        String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    /**
     * La fila de UNA persona, sin resolver la audiencia entera: es la respuesta de cada toque de «pasar lista».
     * Vacío si la cuenta no existe.
     */
    Optional<PersonaConvocada> una(Evento evento, Instant slot, UserId persona) {
        Persona ficha = personas.porIds(List.of(persona)).get(persona);
        if (ficha == null) {
            return Optional.empty();
        }
        Confirmacion respuesta = confirmaciones.deOcurrencia(evento.id(), slot).stream()
                .filter(c -> c.usuarioId().equals(persona)).findFirst().orElse(null);
        List<RespuestaAnterior> antes = historial.deOcurrencia(evento.id(), slot).stream()
                .filter(r -> r.usuarioId().equals(persona)).toList();
        return Optional.of(fila(ficha, respuesta, antes, lista.marcaDe(evento.id(), slot, persona).orElse(null)));
    }

    boolean respondio(Evento evento, Instant slot, UserId persona) {
        return confirmaciones.estadoDe(evento.id(), slot, persona).isPresent();
    }

    private static PersonaConvocada fila(Persona persona, Confirmacion respuesta, List<RespuestaAnterior> antes,
                                         MarcaDeAsistencia marca) {
        return new PersonaConvocada(persona.id(), persona.nombre(), persona.avatarUrl(),
                respuesta == null ? null : respuesta.estado(), respuesta == null ? null : respuesta.actualizadoEn(),
                antes, marca);
    }
}

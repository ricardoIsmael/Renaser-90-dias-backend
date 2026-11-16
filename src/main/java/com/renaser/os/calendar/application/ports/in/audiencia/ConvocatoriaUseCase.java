package com.renaser.os.calendar.application.ports.in.audiencia;

import com.renaser.os.calendar.domain.model.evento.Evento;
import com.renaser.os.calendar.domain.model.evento.TipoEvento;
import com.renaser.os.calendar.domain.model.evento.audiencia.ParteDeAudiencia;
import com.renaser.os.calendar.domain.model.evento.audiencia.TipoDeParte;
import com.renaser.os.shared.domain.Formacion;
import com.renaser.os.shared.domain.UserId;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** El paso «¿Para quién es?» del formulario y lo que el detalle dice de la audiencia (spec 044, contrato §2 y §3). */
public interface ConvocatoriaUseCase {

    /** Qué puede elegir quien llama (FR-029). Sin permiso, {@code puedeCrear = false} y todo vacío. */
    Opciones opciones(UserId actor, Formacion formacion);

    /** El buscador de «Personas», solo dentro del alcance de quien llama. Nunca dice el color de nadie. */
    PaginaDePersonas personas(UserId actor, Formacion formacion, String texto, int pagina, int tamano);

    /** «Llega a N personas» (FR-033): no guarda nada. */
    ResumenDeAudiencia vistaPrevia(UserId actor, Formacion formacion, List<ParteDeAudiencia> partes);

    /** «Para quién es»: solo para quien creó el evento o lo gestiona (FR-018); vacío para cualquier otro. */
    Optional<ResumenDeAudiencia> resumenPara(UserId actor, Evento evento);

    /** Quién organizó el evento (FR-019); vacío si la cuenta ya no existe. */
    Optional<PersonaConvocable> organizador(Evento evento);

    /**
     * @param partes      las partes que el rol puede elegir
     * @param tipos       los tipos de evento que el rol puede crear
     * @param porDefecto  la parte con la que abre el paso (FR-034), o {@code null}
     * @param todos       si puede elegir «Todos» (lo de siempre)
     * @param colores     cuántos hay hoy de cada color en su alcance
     */
    record Opciones(boolean puedeCrear, Set<TipoDeParte> partes, Set<TipoEvento> tipos, TipoDeParte porDefecto, boolean todos,
                    Map<String, Integer> colores, int misAlumnos, List<Grupo> grupos, List<PersonaConvocable> personal,
                    java.time.LocalDate fechaDelSemaforo) {

        public Opciones(boolean puedeCrear, Set<TipoDeParte> partes, Set<TipoEvento> tipos, TipoDeParte porDefecto, boolean todos,
                        Map<String, Integer> colores, int misAlumnos, List<Grupo> grupos, List<PersonaConvocable> personal) {
            this(puedeCrear, partes, tipos, porDefecto, todos, colores, misAlumnos, grupos, personal, null);
        }
    }

    record Grupo(UUID id, String nombre, int integrantes) {
    }

    record PaginaDePersonas(List<PersonaConvocable> personas, int pagina, boolean hayMas) {
    }
}

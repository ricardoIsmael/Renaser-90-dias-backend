package com.renaser.os.calendar.domain.model.evento.audiencia;

import com.renaser.os.shared.domain.UserId;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * La audiencia de un evento {@code SELECCION} (spec 044): las reglas que se leen en cada momento y la lista fija de personas. Es lo que se guarda;
 * quién está dentro en un momento dado lo resuelve la aplicación, que es la que sabe leer grupos y roles.
 */
public record AudienciaElegida(List<ReglaDeAudiencia> reglas, List<Invitado> invitados) {

    public AudienciaElegida {
        reglas = List.copyOf(reglas);
        invitados = List.copyOf(invitados);
    }

    public static AudienciaElegida vacia() {
        return new AudienciaElegida(List.of(), List.of());
    }

    public boolean estaVacia() {
        return reglas.isEmpty() && invitados.isEmpty();
    }

    /** Las personas de la lista fija, sin repetir (una persona elegida por nombre y por color cuenta una vez, FR-003). */
    public Set<UserId> personasFijas() {
        return invitados.stream().map(Invitado::persona).collect(Collectors.toCollection(LinkedHashSet::new));
    }

    public boolean tiene(TipoDeParte tipo) {
        return reglas.stream().anyMatch(r -> r.tipo() == tipo);
    }

    public List<ReglaDeAudiencia> reglasDe(TipoDeParte tipo) {
        return reglas.stream().filter(r -> r.tipo() == tipo).toList();
    }

    public List<Invitado> invitadosDe(OrigenDelInvitado origen) {
        return invitados.stream().filter(i -> i.origen() == origen).toList();
    }

    /** La foto guardada de un color, si la hay. */
    public Optional<ReglaDeAudiencia> fotoDe(ColorDeAudiencia color) {
        return reglasDe(TipoDeParte.COLOR).stream().filter(r -> r.color() == color).findFirst();
    }
}

package com.renaser.os.onboarding.application.ports.out.terminos;

import com.renaser.os.shared.domain.Formacion;
import com.renaser.os.shared.domain.UserId;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** La constancia de qué versión aceptó cada persona (tabla {@code aceptaciones_de_terminos}, V148, spec 027). Solo se agregan filas. */
public interface AceptacionesDeTerminosPort {

    /**
     * Anota que {@code usuario} aceptó la versión; {@code firmaMediaId} es la firma que dibujó (puede faltar si el archivo se borró) y {@code mostrado} lo que se le
     * mostró en «Mi aceptación» (spec 039).
     */
    void registrar(UUID id, UserId usuario, UUID versionId, Instant en, Long firmaMediaId, LoMostrado mostrado);

    /** El número de la última versión de ESA formación que aceptó la persona; vacío si nunca aceptó una de esa formación. */
    Optional<Integer> ultimaAceptada(UserId usuario, Formacion formacion);

    /** Si aceptó términos alguna vez, de cualquier formación. */
    boolean aceptoAlgunaVez(UserId usuario);

    /** Lo que aceptó la persona, de lo más reciente a lo más viejo, con la versión a la vista. */
    List<Aceptacion> delUsuario(UserId usuario);

    /** Cuántas personas distintas aceptaron cada versión (por su id). */
    Map<UUID, Long> aceptantesPorVersion(Formacion formacion);

    /** Las personas que aceptaron la versión {@code numero} o una posterior de esa formación. */
    Set<UserId> quienesAceptaronDesde(Formacion formacion, int numero);

    /**
     * Lo que se le mostró a la persona en «Mi aceptación» al aceptar (spec 039): constancia de QUÉ firmó además de qué texto. Todo es opcional: lo anterior a esta spec
     * no lo guardó, y quien no tiene un programa que mostrar (personal sin activar) no tiene fechas.
     * El cronograma de pagos no es parte de esto (D-315, decisión del dueño del 2026-11-02): no se firma con los términos.
     */
    record LoMostrado(String programa, LocalDate inicio, LocalDate fin) {

        /** Nada que guardar: una aceptación anterior a la spec 039. */
        public static final LoMostrado NADA = new LoMostrado(null, null, null);

        public boolean hayAlgo() {
            return programa != null || inicio != null || fin != null;
        }
    }

    record Aceptacion(UUID versionId, Formacion formacion, int numero, Instant aceptadaEn, Long firmaMediaId, LoMostrado mostrado) {
    }
}

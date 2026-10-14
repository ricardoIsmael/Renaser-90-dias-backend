package com.renaser.os.plataforma.domain.model.estado;

import com.renaser.os.plataforma.domain.model.catalogo.CatalogoDeFuncionalidades;
import com.renaser.os.plataforma.domain.model.catalogo.DefinicionDeFuncionalidad;
import com.renaser.os.plataforma.domain.model.catalogo.ValorPorDefecto;
import com.renaser.os.users.api.UserRole;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/**
 * La regla de evaluación del contrato §5, en un solo lugar: se sube por la madre ({@code chat} antes que
 * {@code chat.grupos}) y en cada nivel
 * <ol>
 *   <li>apagada para todos gana (aviso de esa fila);</li>
 *   <li>si no, manda la excepción del rol de quien pide (aviso de esa fila);</li>
 *   <li>sin fila, el valor por defecto del catálogo;</li>
 *   <li>en las claves {@code ia.*}, si lo manual está prendido pero el circuito automático está abierto, apagada con el
 *       aviso automático: <b>lo manual manda</b> (FR-017), por eso se mira después.</li>
 * </ol>
 * Una clave que no está en el catálogo no tiene interruptor: prendida.
 *
 * <p>Si la cadena entera está prendida y la pide una persona, se preguntan las {@link ReglaDeDisponibilidad} de la fase
 * 2 (despliegue progresivo, roles personalizados): la primera que la deja fuera la vuelve <b>no disponible</b>. Un
 * apagado manual o del circuito gana siempre: es mantenimiento para todos los que la tienen.
 *
 * <p>El rol se pide solo si algún nivel tiene excepciones por rol: en el caso común (nada apagado) evaluar no consulta
 * a nadie, que es lo que mantiene la consulta por debajo de 2 ms (FR-011).
 */
public final class EvaluacionDeFuncionalidades {

    private final CatalogoDeFuncionalidades catalogo;

    public EvaluacionDeFuncionalidades(CatalogoDeFuncionalidades catalogo) {
        this.catalogo = catalogo;
    }

    /**
     * @param actor      quien pide, o {@code null} para la evaluación global de un proceso sin actor
     * @param automatico el aviso automático de una clave {@code ia.*} apagada por el circuito, o vacío
     */
    public ResultadoDeEvaluacion evaluar(String clave, FotoDeEstados foto, ActorEvaluado actor,
                                         Function<String, Optional<String>> automatico) {
        return evaluar(clave, foto, actor, automatico, List.of());
    }

    /**
     * @param reglas las reglas de disponibilidad, en orden; solo se preguntan con la cadena prendida y con actor
     */
    public ResultadoDeEvaluacion evaluar(String clave, FotoDeEstados foto, ActorEvaluado actor,
                                         Function<String, Optional<String>> automatico,
                                         List<ReglaDeDisponibilidad> reglas) {
        List<DefinicionDeFuncionalidad> cadena = catalogo.cadena(clave);
        for (DefinicionDeFuncionalidad nivel : cadena) {
            Optional<ResultadoDeEvaluacion> apagada = apagadaEnEsteNivel(nivel, foto, actor, automatico);
            if (apagada.isPresent()) {
                return apagada.get();
            }
        }
        if (actor == null || reglas.isEmpty() || cadena.isEmpty()) {
            return ResultadoDeEvaluacion.sinApagar();
        }
        List<String> claves = cadena.stream().map(DefinicionDeFuncionalidad::clave).toList();
        for (ReglaDeDisponibilidad regla : reglas) {
            Optional<String> fuera = regla.noDisponibleEn(claves, actor);
            if (fuera.isPresent()) {
                return ResultadoDeEvaluacion.noDisponible(fuera.get());
            }
        }
        return ResultadoDeEvaluacion.sinApagar();
    }

    private static Optional<ResultadoDeEvaluacion> apagadaEnEsteNivel(DefinicionDeFuncionalidad nivel,
                                                                     FotoDeEstados foto, ActorEvaluado actor,
                                                                     Function<String, Optional<String>> automatico) {
        Optional<EstadoDeFuncionalidad> fila = foto.estadoDe(nivel.clave());
        if (fila.isPresent() && apagadaManualmente(fila.get(), actor)) {
            return Optional.of(ResultadoDeEvaluacion.apagada(nivel.clave(), fila.get().aviso(), false));
        }
        if (fila.isEmpty() && nivel.porDefecto() == ValorPorDefecto.APAGADA) {
            return Optional.of(ResultadoDeEvaluacion.apagada(nivel.clave(), null, false));
        }
        if (nivel.esDeLaIa()) {
            return automatico.apply(nivel.clave())
                    .map(aviso -> ResultadoDeEvaluacion.apagada(nivel.clave(), aviso, true));
        }
        return Optional.empty();
    }

    private static boolean apagadaManualmente(EstadoDeFuncionalidad fila, ActorEvaluado actor) {
        if (!fila.prendidaGlobal()) {
            return true;
        }
        if (fila.apagadaPara().isEmpty() || actor == null) {
            return false;
        }
        UserRole delActor = actor.rol();
        return delActor != null && fila.apagadaPara(delActor);
    }
}

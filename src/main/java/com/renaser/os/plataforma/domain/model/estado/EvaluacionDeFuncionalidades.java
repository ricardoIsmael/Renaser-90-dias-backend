package com.renaser.os.plataforma.domain.model.estado;

import com.renaser.os.plataforma.domain.model.catalogo.CatalogoDeFuncionalidades;
import com.renaser.os.plataforma.domain.model.catalogo.DefinicionDeFuncionalidad;
import com.renaser.os.plataforma.domain.model.catalogo.ValorPorDefecto;
import com.renaser.os.users.api.UserRole;

import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;

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
 * <p>El rol se pide solo si algún nivel tiene excepciones por rol: en el caso común (nada apagado) evaluar no consulta
 * a nadie, que es lo que mantiene la consulta por debajo de 2 ms (FR-011).
 */
public final class EvaluacionDeFuncionalidades {

    private final CatalogoDeFuncionalidades catalogo;

    public EvaluacionDeFuncionalidades(CatalogoDeFuncionalidades catalogo) {
        this.catalogo = catalogo;
    }

    /**
     * @param rol         el rol de quien pide, o {@code null} para la evaluación global de un proceso sin actor
     * @param automatico  el aviso automático de una clave {@code ia.*} apagada por el circuito, o vacío
     */
    public ResultadoDeEvaluacion evaluar(String clave, FotoDeEstados foto, Supplier<UserRole> rol,
                                         Function<String, Optional<String>> automatico) {
        for (DefinicionDeFuncionalidad nivel : catalogo.cadena(clave)) {
            Optional<ResultadoDeEvaluacion> apagada = apagadaEnEsteNivel(nivel, foto, rol, automatico);
            if (apagada.isPresent()) {
                return apagada.get();
            }
        }
        return ResultadoDeEvaluacion.sinApagar();
    }

    private static Optional<ResultadoDeEvaluacion> apagadaEnEsteNivel(DefinicionDeFuncionalidad nivel,
                                                                     FotoDeEstados foto, Supplier<UserRole> rol,
                                                                     Function<String, Optional<String>> automatico) {
        Optional<EstadoDeFuncionalidad> fila = foto.estadoDe(nivel.clave());
        if (fila.isPresent() && apagadaManualmente(fila.get(), rol)) {
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

    private static boolean apagadaManualmente(EstadoDeFuncionalidad fila, Supplier<UserRole> rol) {
        if (!fila.prendidaGlobal()) {
            return true;
        }
        if (fila.apagadaPara().isEmpty() || rol == null) {
            return false;
        }
        UserRole delActor = rol.get();
        return delActor != null && fila.apagadaPara(delActor);
    }
}

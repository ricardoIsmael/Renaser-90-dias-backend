package com.renaser.os.rag.infrastructure.adapter.out.habits;

import com.renaser.os.habits.api.EntradaDiarioFinder;
import com.renaser.os.habits.api.EntradaDiarioSummary;
import com.renaser.os.rag.application.ports.out.espejosombra.LeerEntradasDiarioPort;
import com.renaser.os.shared.domain.UserId;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

/**
 * Implementa {@link LeerEntradasDiarioPort} delegando en el contrato público de
 * {@code habits} (D-50, D-41) — {@code rag} nunca consulta {@code entradas_diario}
 * de frente.
 *
 * <p>Descarta acá las entradas sin contenido y resuelve, por entrada, cuál es el
 * mejor texto disponible: el texto propio si existe, si no la transcripción del
 * audio. Así el servicio de aplicación no repite esta decisión.
 */
@Component
class LeerEntradasDiarioAdapter implements LeerEntradasDiarioPort {

    private final EntradaDiarioFinder entradaDiarioFinder;

    LeerEntradasDiarioAdapter(EntradaDiarioFinder entradaDiarioFinder) {
        this.entradaDiarioFinder = entradaDiarioFinder;
    }

    @Override
    public List<EntradaDiario> deLaSemana(UserId participanteId, LocalDate inicio, LocalDate fin) {
        return entradaDiarioFinder.entradasEntre(participanteId, inicio, fin).stream()
                .filter(EntradaDiarioSummary::tieneContenido)
                .map(LeerEntradasDiarioAdapter::aEntradaDiario)
                .toList();
    }

    private static EntradaDiario aEntradaDiario(EntradaDiarioSummary resumen) {
        String texto = (resumen.contenidoTexto() != null && !resumen.contenidoTexto().isBlank())
                ? resumen.contenidoTexto()
                : resumen.transcripcion();
        return new EntradaDiario(resumen.fecha(), texto);
    }
}

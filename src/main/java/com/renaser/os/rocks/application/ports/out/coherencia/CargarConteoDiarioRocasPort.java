package com.renaser.os.rocks.application.ports.out.coherencia;

import com.renaser.os.rocks.domain.model.coherencia.DiaRocas;
import com.renaser.os.shared.domain.UserId;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * D-43: UNA sola consulta agrupada por (participante, día) para TODOS los
 * participantes pedidos — el adaptador de persistencia nunca puede resolver
 * esto con un bucle de una consulta por participante, es exactamente el
 * incidente ("Too many database connections opened", ~30 cuentas activas)
 * que motivó D-43.
 *
 * <p>Solo devuelve entradas para (participante, día) con al menos una Roca
 * Diaria — un participante sin ninguna en la ventana pedida simplemente no
 * tiene clave en el mapa (o tiene lista vacía); es
 * {@code PorcentajeRocasService} quien decide qué hacer con eso (ventana
 * vacía → 100, ver {@code PorcentajeRocas}).
 */
public interface CargarConteoDiarioRocasPort {

    Map<UserId, List<DiaRocas>> conteoDiarioPorParticipante(Collection<UserId> participantes, LocalDate desde, LocalDate hasta);
}

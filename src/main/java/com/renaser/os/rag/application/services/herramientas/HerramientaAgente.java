package com.renaser.os.rag.application.services.herramientas;

import com.renaser.os.rag.domain.model.herramienta.DefinicionHerramienta;
import com.renaser.os.rag.domain.model.herramienta.InvocacionHerramienta;
import com.renaser.os.rag.domain.model.herramienta.ResultadoHerramienta;
import com.renaser.os.shared.domain.UserId;

/**
 * Una herramienta mas del acompanante, en su propia clase (2026-09-23).
 *
 * <p><b>Por que existe.</b> Las tres primeras herramientas viven en el {@code switch} de
 * {@code HerramientasAgenteService} y en {@code CatalogoHerramientasAgente}. Sumar las que pide el
 * plan del acompanante (tiempo, horarios, rocas, eventos:
 * {@code docs/arquitectura/PROPUESTA_ACOMPANANTE_90_DIAS.md}) en ese mismo {@code switch} lo
 * convertiria en el despachador que el propio catalogo advierte no construir, y obligaria a tocar
 * el mismo archivo por cada herramienta nueva. Cada implementacion es un {@code @Component}; el
 * servicio las recibe todas y las ofrece solo al agente {@code COMPANION}.
 *
 * <p>Las tres originales no se migran en este cambio: funcionan, estan probadas, y moverlas no
 * agrega nada.
 *
 * <p><b>Mismo contrato que las originales:</b>
 * <ul>
 *   <li>el {@code actorId} viene de la conversacion autenticada, nunca de un argumento del modelo;</li>
 *   <li>un fallo se devuelve como {@link ResultadoHerramienta.Fallo} con un motivo apto para
 *       mostrar, nunca como excepcion;</li>
 *   <li>nada de {@code @Transactional} alrededor (C-1);</li>
 *   <li>las horas y los plazos se calculan en codigo, en la zona del participante (regla 02): el
 *       modelo solo parafrasea el resultado.</li>
 * </ul>
 *
 * <p>Los argumentos obligatorios que falten ya los rechaza {@code HerramientasAgenteService} antes
 * de llamar a {@link #ejecutar}.
 */
public interface HerramientaAgente {

    /** Nombre estable, descripcion para el modelo y parametros. */
    DefinicionHerramienta definicion();

    ResultadoHerramienta ejecutar(UserId actorId, InvocacionHerramienta invocacion);
}

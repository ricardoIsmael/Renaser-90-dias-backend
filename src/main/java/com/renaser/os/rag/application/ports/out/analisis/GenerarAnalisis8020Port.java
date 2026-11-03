package com.renaser.os.rag.application.ports.out.analisis;

import com.renaser.os.rag.domain.model.analisis.TextoDelAnalisis;
import com.renaser.os.rag.domain.model.analisis.TipoDePeriodo;

/**
 * Le pide al proveedor de IA el análisis de un periodo (spec 031). Es la IA de siempre de SER —mismo proveedor, mismas credenciales,
 * mismo circuito—, no un cliente nuevo. Una llamada, sin herramientas, que devuelve el texto en cuatro partes.
 *
 * <p><b>Nunca se llama dentro de una transacción</b> (C-1). El pedido lleva datos personales: el adaptador no los loguea.
 */
public interface GenerarAnalisis8020Port {

    /** {@code false} si no hay proveedor configurado (desarrollo, pruebas): quien orquesta no genera ni cuenta intentos. */
    boolean disponible();

    /**
     * @throws com.renaser.os.shared.domain.ProveedorIaNoDisponibleException si el fallo es del proveedor y transitorio (429, 408, 5xx, timeout)
     * @throws IllegalStateException si el proveedor respondió algo que no es un análisis legible
     */
    TextoDelAnalisis generar(PedidoDeAnalisis pedido);

    /** @param datosEnJson el periodo, las cifras calculadas y lo que la persona respondió, ya recortado: lo único que viaja al proveedor */
    record PedidoDeAnalisis(TipoDePeriodo tipo, String datosEnJson) {
    }
}

package com.renaser.os.rag.application.services.analisis;

import com.renaser.os.rag.application.ports.out.analisis.GenerarAnalisis8020Port.PedidoDeAnalisis;
import com.renaser.os.rag.application.services.analisis.ResumenDelCodigoRenaser.MuestraDelRadar;
import com.renaser.os.rag.api.Analisis8020Finder.ResumenDeSemana;
import com.renaser.os.rag.api.Analisis8020Finder.ResumenDelRadar;
import com.renaser.os.rag.domain.model.analisis.TipoDePeriodo;
import com.renaser.os.rag.domain.model.analisis.ValidadorDelTexto;
import com.renaser.os.rocks.api.Pareto8020Finder.AccionDel8020;
import com.renaser.os.rocks.api.Pareto8020Finder.CifrasDel8020;
import tools.jackson.databind.JsonNode;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Arma lo que viaja a la IA y lo que se guarda (spec 031): una sola fuente de cifras. La foto ({@link SnapshotDelAnalisis}) es a la vez
 * lo que se manda, lo que se guarda junto al texto y de donde salen los números que el texto puede nombrar (FR-002 y FR-009): si un número
 * no está en la foto, el texto que lo nombre no se muestra.
 *
 * <p>Al proveedor viaja solo lo necesario (FR-023): cifras, títulos de las acciones, lo que escribió en el Código Renaser y en «Cierra tu
 * día», recortado. Ningún correo, teléfono, documento, dirección ni nombre de la persona.
 */
final class ArmadoDelPedido {

    /** Lo que se manda al proveedor, por clave. Record público de paquete: Jackson lo lee por su constructor. */
    record DatosParaLaIa(String periodo, int numero, LocalDate inicio, LocalDate fin, CifrasDel8020 cifras,
                         List<ResumenDeSemana> semanas, ResumenDelRadar codigoRenaser, List<MuestraDelRadar> escritoEnElCodigoRenaser,
                         List<String> escritoAlCerrarElDia) {
    }

    /** @param numerosPermitidos los números que el texto puede nombrar, normalizados */
    record PedidoArmado(PedidoDeAnalisis pedido, SnapshotDelAnalisis foto, Set<String> numerosPermitidos) {
    }

    private ArmadoDelPedido() {
    }

    static PedidoArmado armar(TipoDePeriodo tipo, InsumosDelAnalisis insumos) {
        SnapshotDelAnalisis foto = new SnapshotDelAnalisis(insumos.acciones().cifras(), insumos.semanas(), insumos.radar());
        List<MuestraDelRadar> escrito = insumos.radar() == null ? List.of()
                : ResumenDelCodigoRenaser.muestras(insumos.respuestas(), insumos.zona());
        DatosParaLaIa datos = new DatosParaLaIa(tipo.name(), insumos.periodo().numero(), insumos.periodo().inicio(),
                insumos.periodo().fin(), foto.cifras(), foto.semanas(), foto.codigoRenaser(), escrito,
                insumos.acciones().notasDeCierre());
        return new PedidoArmado(new PedidoDeAnalisis(tipo, JsonDelAnalisis.escribir(datos)), foto,
                numerosPermitidos(foto, insumos));
    }

    /**
     * Todo número de la foto, los que traen los títulos de las acciones (la persona escribió «30 minutos» y el texto puede repetirlo) y
     * los del propio periodo (su número y los días de sus fechas: el texto puede decir «la semana 3»). No más.
     */
    static Set<String> numerosPermitidos(SnapshotDelAnalisis foto, InsumosDelAnalisis insumos) {
        Set<String> permitidos = new LinkedHashSet<>();
        numerosDe(JsonDelAnalisis.arbol(JsonDelAnalisis.escribir(foto)), permitidos);
        List<AccionDel8020> nombradas = new ArrayList<>(foto.cifras().lasQueMasMovieron());
        nombradas.addAll(foto.cifras().lasQueSeFueron());
        nombradas.forEach(accion -> permitidos.addAll(ValidadorDelTexto.numerosEn(accion.titulo())));
        permitidos.add(String.valueOf(insumos.periodo().numero()));
        permitidos.add(String.valueOf(insumos.periodo().inicio().getDayOfMonth()));
        permitidos.add(String.valueOf(insumos.periodo().fin().getDayOfMonth()));
        return Set.copyOf(permitidos);
    }

    private static void numerosDe(JsonNode nodo, Set<String> numeros) {
        if (nodo.isNumber()) {
            numeros.add(ValidadorDelTexto.normalizar(nodo.asString()));
            return;
        }
        nodo.values().forEach(hijo -> numerosDe(hijo, numeros));
    }
}

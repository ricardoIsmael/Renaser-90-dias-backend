package com.renaser.os.rag.infrastructure.adapter.in.rest.analisis;

import com.renaser.os.rag.api.Analisis8020Finder.VistaDelAnalisis;
import com.renaser.os.rag.application.ports.in.analisis.ConsultarAnalisis8020UseCase;
import com.renaser.os.rag.application.ports.in.analisis.ConsultarAnalisis8020UseCase.ConsultaDeAnalisis;
import com.renaser.os.rag.domain.model.analisis.TipoDePeriodo;
import com.renaser.os.shared.domain.Permission;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.shared.web.security.ActorAutenticado;
import com.renaser.os.shared.web.security.Funcionalidad;
import com.renaser.os.shared.web.security.RequiresPermission;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * {@code GET /api/v1/analisis-80-20?periodo=SEMANA|MES&inicio=YYYY-MM-DD}
 *
 * <p>El 80/20 de la propia persona en una semana o un mes del programa, con el análisis de la IA cuando ya está (spec 031, D-306): lo que
 * lee la hoja «Tu 80/20» de Plan. Solo sobre sí misma —no hay ruta ni parámetro para pedir a otra persona— y solo lectura: abrirlo nunca
 * llama a la IA ni genera nada (FR-006).
 *
 * <p>Pertenece a la funcionalidad {@code plan}, no a {@code ia.analisis}: con el análisis apagado por Soporte TI las cifras se siguen
 * viendo (FR-019); la app esconde el texto con el aviso de mantenimiento. {@code inicio} es el primer día del periodo que devolvieron
 * {@code anterior} y {@code siguiente}; sin él, el periodo en curso. Un periodo que no existe o que no llegó es 400; sin Día 1, 404. Datos
 * de una persona: nada se guarda en caché ({@code no-store}).
 */
@RestController
@RequestMapping("/api/v1/analisis-80-20")
@Funcionalidad("plan")
public class Analisis8020Controller {

    private final ConsultarAnalisis8020UseCase consultar;

    public Analisis8020Controller(ConsultarAnalisis8020UseCase consultar) {
        this.consultar = consultar;
    }

    @RequiresPermission(value = Permission.USE_APP, scope = "la propia persona: siempre sobre sí misma")
    @GetMapping
    public ResponseEntity<VistaDelAnalisis> analisis(@ActorAutenticado UserId actorId,
                                                      @RequestParam(defaultValue = "SEMANA") String periodo,
                                                      @RequestParam(required = false)
                                                      @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inicio) {
        VistaDelAnalisis vista = consultar.deLaPersona(new ConsultaDeAnalisis(actorId, TipoDePeriodo.desde(periodo), inicio));
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(vista);
    }
}

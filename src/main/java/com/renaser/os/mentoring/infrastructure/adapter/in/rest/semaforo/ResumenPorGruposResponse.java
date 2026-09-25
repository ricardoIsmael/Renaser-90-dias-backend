package com.renaser.os.mentoring.infrastructure.adapter.in.rest.semaforo;

import com.renaser.os.mentoring.application.ports.in.ConsultarSemaforoPorGruposUseCase.GrupoDelResumen;
import com.renaser.os.mentoring.application.ports.in.ConsultarSemaforoPorGruposUseCase.ResumenPorGrupos;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * El resumen del semáforo por grupos, en el JSON exacto de docs/arquitectura/SEMAFORO_DEL_APRENDIZ.md
 * §4.4. No tiene ningún campo donde viaje un aprendiz: ni nombre ni id (RL-07 del SDD 002).
 */
public record ResumenPorGruposResponse(LocalDate desde, LocalDate hasta, boolean cerrada,
                                       ConteoPorColorResponse totales, List<GrupoResponse> grupos) {

    public static ResumenPorGruposResponse from(ResumenPorGrupos resumen) {
        return new ResumenPorGruposResponse(resumen.periodo().desde(), resumen.periodo().hasta(),
                resumen.periodo().cerrada(), ConteoPorColorResponse.from(resumen.totales()),
                resumen.grupos().stream().map(GrupoResponse::from).toList());
    }

    /** @param promedio de los aprendices con datos, 1 decimal; null si ninguno tiene. */
    public record GrupoResponse(UUID grupoId, String grupoNombre, String mentorNombre,
                                ConteoPorColorResponse resumen, BigDecimal promedio) {

        static GrupoResponse from(GrupoDelResumen grupo) {
            return new GrupoResponse(grupo.grupoId(), grupo.grupoNombre(), grupo.mentorNombre(),
                    ConteoPorColorResponse.from(grupo.resumen().conteo()), grupo.resumen().promedio());
        }
    }
}

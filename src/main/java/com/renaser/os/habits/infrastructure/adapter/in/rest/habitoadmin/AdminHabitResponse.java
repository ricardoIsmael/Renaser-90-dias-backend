package com.renaser.os.habits.infrastructure.adapter.in.rest.habitoadmin;

import com.renaser.os.habits.application.ports.in.habitoadmin.HabitoDelCatalogo;
import com.renaser.os.habits.domain.model.audiencia.CodigoDeRol;
import com.renaser.os.habits.domain.model.audiencia.VersionDeAudiencia;
import com.renaser.os.habits.domain.model.habito.Habito;

import java.time.Instant;
import java.util.List;

/**
 * Espejo de {@code AdminHabit} (`habitsAdmin.ts`). Proyeccion a mano — nunca la entidad completa (CLAUDE.MD §5.4.1).
 * Trae {@code mandatoryOnIntoxication} para que el formulario de edicion pueda devolverla (E-263).
 *
 * <p>Spec 001 (D-264), campos ADITIVOS: un cliente que no los conoce sigue igual. {@code targetRoles} es la ultima
 * audiencia guardada (vacia = para todos), rija o no todavia; {@code targetRolesEffectiveFrom} es cuando se guardo
 * (corregido 2026-10-12, E2E: la app ya no deduce de esta fecha si «Rige desde mañana»; lo dice
 * {@code targetRolesStartTomorrow}, que solo es true para un CAMBIO de hoy, nunca para la audiencia con la que nace). {@code systemKey} e {@code isDeactivatable} son de solo lectura:
 * avisan que el habito tiene reglas propias en la app.
 */
public record AdminHabitResponse(String id, String title, String description, HabitTypeDto habitType,
                                  HabitCategoryDto category, HabitEvidenceRequirementDto evidenceRequirement,
                                  boolean isOptional, boolean mandatoryOnIntoxication, boolean isActive,
                                  String systemKey, boolean isDeactivatable, List<String> targetRoles,
                                  Instant targetRolesEffectiveFrom, Instant audienceChangedAt,
                                  AudienceChangedBy audienceChangedBy, boolean targetRolesStartTomorrow) {

    public record AudienceChangedBy(String id, String name) {
    }

    public static AdminHabitResponse from(HabitoDelCatalogo delCatalogo) {
        Habito habito = delCatalogo.habito();
        VersionDeAudiencia version = delCatalogo.audiencia().orElse(null);
        return new AdminHabitResponse(habito.id().value().toString(), habito.titulo(), habito.descripcion(),
                HabitTypeDto.from(habito.tipo()), HabitCategoryDto.fromClave(habito.categoriaClave()),
                HabitEvidenceRequirementDto.from(habito.exigenciaEvidencia()), habito.esOpcional(),
                habito.obligatorioEnIntoxicacion(), habito.activo(), habito.claveSistema(), habito.desactivable(),
                delCatalogo.audienciaGuardada().roles().stream().map(CodigoDeRol::valor).sorted().toList(),
                version == null ? null : version.rigeDesde(), version == null ? null : version.cambiadoEn(),
                version == null ? null : new AudienceChangedBy(version.cambiadoPor().value().toString(),
                        delCatalogo.nombreDeQuienCambio()),
                delCatalogo.rigeDesdeManana());
    }
}

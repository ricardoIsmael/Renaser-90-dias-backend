package com.renaser.os.community.application.ports.out.celula;

import com.renaser.os.community.domain.model.celula.Celula;
import com.renaser.os.community.domain.model.celula.CelulaId;
import com.renaser.os.community.domain.model.cohorte.CohorteId;
import com.renaser.os.shared.domain.UserId;

import java.util.List;
import java.util.Optional;

public interface LoadCelulaPort {

    Optional<Celula> porId(CelulaId id);

    List<Celula> porCohorte(CohorteId cohorteId);

    List<Celula> todas();

    /**
     * Las celulas que lidera un mentor (las que lo nombran en `celulas.mentor_id`), por nombre. Resuelve el
     * alcance de un MENTOR sin tocar `perfiles_mentor` (tabla que no es de este modulo): `mentor_id` en
     * `celulas` YA es el `usuario_id`, asi que la busqueda es puramente sobre una tabla propia.
     *
     * <p><b>Corregido 2026-09-27 (E-371).</b> Devolvia {@code Optional<Celula>} y decia que
     * {@code celulas.mentor_id} es UNIQUE, "a lo sumo una". V58 levanto ese UNIQUE (D-141: un mentor
     * puede liderar varios grupos), y con dos grupos la consulta de "uno solo" reventaba con
     * {@code NonUniqueResultException}: 500 en {@code /admin/cells?cohortId=} y en {@code /admin/cohorts}.
     */
    List<Celula> porMentor(UserId mentorId);
}

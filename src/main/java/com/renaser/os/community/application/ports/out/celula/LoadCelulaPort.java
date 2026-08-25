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

    /** La celula que lidera un mentor (`celulas.mentor_id` es UNIQUE — a lo sumo una,
     * V1__baseline_renaser.sql:245). Resuelve el alcance de un MENTOR sin tocar
     * `perfiles_mentor` (tabla que no es de este modulo): `mentor_id` en `celulas` YA es el
     * `usuario_id`, asi que la busqueda es puramente sobre una tabla propia. */
    Optional<Celula> porMentor(UserId mentorId);
}

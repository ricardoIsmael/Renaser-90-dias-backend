package com.renaser.os.users.infrastructure.adapter.in.rest.mentorprofile;

import com.renaser.os.users.domain.model.mentorprofile.EspecialidadMentor;
import com.renaser.os.users.domain.model.mentorprofile.MentorLevel;
import com.renaser.os.users.domain.model.mentorprofile.MentorOperationalStatus;

/** Campos null = "no cambiar". {@code especialidad} (V48): NEGOCIO, MENTE o RELACIONES. */
public record UpdateMentorProfileRequest(MentorLevel newLevel, MentorOperationalStatus newOperationalStatus,
                                          String newBio, EspecialidadMentor especialidad) {
}

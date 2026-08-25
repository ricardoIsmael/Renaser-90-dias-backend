package com.renaser.os.users.infrastructure.adapter.in.rest.mentorprofile;

import com.renaser.os.users.domain.model.mentorprofile.MentorLevel;
import com.renaser.os.users.domain.model.mentorprofile.MentorOperationalStatus;

/** Campos null = "no cambiar". */
public record UpdateMentorProfileRequest(MentorLevel newLevel, MentorOperationalStatus newOperationalStatus,
                                          String newBio) {
}

package com.renaser.os.users.application.ports.out.mentorprofile;

import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.domain.model.mentorprofile.MentorProfile;

import java.util.Optional;

public interface LoadMentorProfilePort {

    Optional<MentorProfile> byUserId(UserId userId);
}

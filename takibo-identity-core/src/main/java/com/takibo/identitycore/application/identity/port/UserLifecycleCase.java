package com.takibo.identitycore.application.identity.port;

import com.takibo.identitycore.application.identity.command.ChangeUserStatusCommand;
import com.takibo.identitycore.application.identity.command.UpdateUserProfileCommand;
import com.takibo.identitycore.integration.space.port.ResolvedSpaceKey;
import com.takibo.identitycore.interfaces.rest.response.UserResponse;

/** Port in : administration du cycle de vie d'un user situé dans un space. */
public interface UserLifecycleCase {

    UserResponse updateProfile(ResolvedSpaceKey key, UpdateUserProfileCommand command);

    UserResponse changeStatus(ResolvedSpaceKey key, ChangeUserStatusCommand command);
}

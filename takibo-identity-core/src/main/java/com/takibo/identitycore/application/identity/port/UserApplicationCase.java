package com.takibo.identitycore.application.identity.port;

import com.takibo.identitycore.application.identity.command.CreateUserCommand;
import com.takibo.identitycore.interfaces.rest.response.UserResponse;


public interface UserApplicationCase {
    UserResponse createUser(CreateUserCommand command);
}

package com.takibo.identitycore.application.auth.port;

import com.takibo.identitycore.application.auth.command.LoginCommand;
import com.takibo.identitycore.interfaces.rest.response.LoginResponse;

/** Port in : login humain situé (email + password + orgCode + spaceCode). */
public interface HumanLoginCase {
    LoginResponse login(LoginCommand command);
}

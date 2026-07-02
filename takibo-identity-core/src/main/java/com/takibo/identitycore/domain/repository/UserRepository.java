package com.takibo.identitycore.domain.repository;

import com.takibo.identitycore.domain.model.EmailAddress;
import com.takibo.identitycore.domain.model.User;
import com.takibo.identitycore.domain.vo.AccountId;
import com.takibo.identitycore.domain.vo.SpaceId;
import com.takibo.identitycore.domain.vo.UserId;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository {

    User save(User user);

    void delete(User user);

    void deleteById(UserId id);

    Page<User> findAllBySpace(SpaceId spaceId, Pageable pageable);

    Optional<User> findById(UserId id);

    Optional<User> findByUsername(SpaceId spaceId, String username);

    Optional<User> findByEmail(SpaceId spaceId, EmailAddress email);

    boolean existsByUsernameIgnoreCaseAndIdNot(SpaceId spaceId, String username, UUID excludeId);

    boolean existsByEmailIgnoreCaseAndIdNot(SpaceId spaceId, EmailAddress email, UUID excludeId);

    boolean existsBySpaceAndAccount(SpaceId spaceId, AccountId accountId);

    Optional<User> findBySpaceAndAccount(SpaceId spaceId, AccountId accountId);


}
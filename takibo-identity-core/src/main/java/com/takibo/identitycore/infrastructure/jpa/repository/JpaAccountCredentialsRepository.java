package com.takibo.identitycore.infrastructure.jpa.repository;

import com.takibo.identitycore.infrastructure.entity.AccountCredentialsEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface JpaAccountCredentialsRepository
        extends JpaRepository<AccountCredentialsEntity, AccountCredentialsEntity.AccountCredentialsId> {
}
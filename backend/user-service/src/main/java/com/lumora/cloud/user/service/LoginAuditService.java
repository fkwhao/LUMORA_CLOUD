package com.lumora.cloud.user.service;

import com.lumora.cloud.user.domain.AuthTypes.ClientType;
import com.lumora.cloud.user.domain.AuthTypes.LoginOutcome;
import com.lumora.cloud.user.persistence.entity.LoginAuditEntity;
import com.lumora.cloud.user.persistence.mapper.LoginAuditMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class LoginAuditService {

    private final LoginAuditMapper mapper;

    public LoginAuditService(LoginAuditMapper mapper) {
        this.mapper = mapper;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(
            Long userId,
            String sessionId,
            String email,
            ClientType clientType,
            LoginOutcome outcome,
            String failureReason,
            RequestMetadata metadata,
            Instant occurredAt
    ) {
        mapper.insert(LoginAuditEntity.create(
                userId,
                sessionId,
                email,
                clientType,
                outcome,
                failureReason,
                metadata.ipAddress(),
                metadata.userAgent(),
                occurredAt
        ));
    }
}

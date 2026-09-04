package com.lumora.cloud.user.audit;

import com.lumora.cloud.user.domain.enums.ClientType;
import com.lumora.cloud.user.domain.enums.LoginOutcome;
import com.lumora.cloud.user.domain.entity.LoginAuditEntity;
import com.lumora.cloud.user.mapper.LoginAuditMapper;
import com.lumora.cloud.user.utils.RequestMetadata;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class LoginAuditService {

    private final LoginAuditMapper mapper;

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

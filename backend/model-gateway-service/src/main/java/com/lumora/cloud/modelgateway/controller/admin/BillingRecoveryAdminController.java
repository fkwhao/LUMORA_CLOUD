package com.lumora.cloud.modelgateway.controller.admin;

import com.lumora.cloud.api.AuthHeaders;
import com.lumora.cloud.modelgateway.recovery.DurableRecoveryJournal;
import com.lumora.cloud.modelgateway.security.ModelGatewayAccess;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@RestController
@Validated
@RequestMapping("/api/admin/model-gateway/recovery")
@RequiredArgsConstructor
public class BillingRecoveryAdminController {
    private final ModelGatewayAccess access;
    private final DurableRecoveryJournal journal;

    public record QueuePage(java.util.List<DurableRecoveryJournal.Entry> items, int total, int page, int pageSize) {}
    public record ResumeRequest(@NotBlank @Size(max = 160) String reason) {}

    @GetMapping
    public Mono<QueuePage> list(ServerWebExchange exchange,
            @RequestParam(defaultValue = "1") @Min(1) @Max(100000) int page) {
        access.requireAdmin(exchange.getRequest().getHeaders());
        return Mono.fromCallable(() -> new QueuePage(journal.page((page - 1) * 20, 20), journal.size(), page, 20))
                .subscribeOn(Schedulers.boundedElastic());
    }

    @PostMapping("/{requestId}/retry")
    @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
    public Mono<Void> retry(ServerWebExchange exchange, @PathVariable @Size(max = 64) String requestId,
            @Valid @RequestBody ResumeRequest request) {
        access.requireAdmin(exchange.getRequest().getHeaders());
        String actor = exchange.getRequest().getHeaders().getFirst(AuthHeaders.USER_ID);
        return Mono.fromRunnable(() -> {
            try { journal.resume(requestId, actor, request.reason().trim()); }
            catch (java.util.NoSuchElementException error) {
                throw new com.lumora.cloud.modelgateway.error.ApiException(org.springframework.http.HttpStatus.NOT_FOUND,
                        "RECOVERY_NOT_FOUND", "恢复记录不存在或已投递，请刷新");
            } catch (java.io.IOException error) { throw new java.io.UncheckedIOException(error); }
        }).subscribeOn(Schedulers.boundedElastic()).then();
    }
}

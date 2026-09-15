package com.zerotrust.rbac.service;

import com.zerotrust.rbac.model.OperationStatus;
import com.zerotrust.rbac.model.PendingOperation;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class PendingOperationService {

    private final ConcurrentHashMap<String, PendingOperation> operations = new ConcurrentHashMap<>();

    public PendingOperation create(String requestedBy, String operationType, String targetResource) {
        String id = UUID.randomUUID().toString();
        PendingOperation op = new PendingOperation(id, requestedBy, operationType, targetResource);
        operations.put(id, op);
        System.out.println("[AUDIT] CRITICAL_REQUEST | user=" + requestedBy
                + " | operation=" + operationType
                + " | target=" + targetResource
                + " | operationId=" + id);
        return op;
    }

    public PendingOperation getById(String operationId) {
        PendingOperation op = operations.get(operationId);
        if (op == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Operation not found: " + operationId);
        }
        return op;
    }

    public List<PendingOperation> listPending() {
        return operations.values().stream()
                .filter(op -> op.getStatus() == OperationStatus.AWAITING_APPROVAL)
                .toList();
    }

    public PendingOperation approve(String operationId, String approvedBy) {
        PendingOperation op = getById(operationId);

        if (op.getStatus() != OperationStatus.AWAITING_APPROVAL) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Operation is not awaiting approval. Current status: " + op.getStatus());
        }

        if (op.getRequestedBy().equals(approvedBy)) {
            System.out.println("[AUDIT] DENIED | operationId=" + operationId
                    + " | reason=Solicitante nao pode aprovar a propria operacao");
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Solicitante nao pode aprovar a propria operacao");
        }

        op.setStatus(OperationStatus.APPROVED);
        op.setResolvedAt(Instant.now());
        op.setResolvedBy(approvedBy);
        System.out.println("[AUDIT] APPROVED | user=" + approvedBy + " | operationId=" + operationId);
        return op;
    }

    public PendingOperation reject(String operationId, String rejectedBy) {
        PendingOperation op = getById(operationId);

        if (op.getStatus() != OperationStatus.AWAITING_APPROVAL) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Operation is not awaiting approval. Current status: " + op.getStatus());
        }

        op.setStatus(OperationStatus.REJECTED);
        op.setResolvedAt(Instant.now());
        op.setResolvedBy(rejectedBy);
        System.out.println("[AUDIT] REJECTED | user=" + rejectedBy + " | operationId=" + operationId);
        return op;
    }
}

package com.zerotrust.rbac.model;

import java.time.Instant;

public class PendingOperation {

    private String id;
    private String requestedBy;
    private String operationType;
    private String targetResource;
    private OperationStatus status;
    private Instant createdAt;
    private Instant resolvedAt;
    private String resolvedBy;

    public PendingOperation() {
    }

    public PendingOperation(String id, String requestedBy, String operationType, String targetResource) {
        this.id = id;
        this.requestedBy = requestedBy;
        this.operationType = operationType;
        this.targetResource = targetResource;
        this.status = OperationStatus.AWAITING_APPROVAL;
        this.createdAt = Instant.now();
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getRequestedBy() {
        return requestedBy;
    }

    public void setRequestedBy(String requestedBy) {
        this.requestedBy = requestedBy;
    }

    public String getOperationType() {
        return operationType;
    }

    public void setOperationType(String operationType) {
        this.operationType = operationType;
    }

    public String getTargetResource() {
        return targetResource;
    }

    public void setTargetResource(String targetResource) {
        this.targetResource = targetResource;
    }

    public OperationStatus getStatus() {
        return status;
    }

    public void setStatus(OperationStatus status) {
        this.status = status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getResolvedAt() {
        return resolvedAt;
    }

    public void setResolvedAt(Instant resolvedAt) {
        this.resolvedAt = resolvedAt;
    }

    public String getResolvedBy() {
        return resolvedBy;
    }

    public void setResolvedBy(String resolvedBy) {
        this.resolvedBy = resolvedBy;
    }
}

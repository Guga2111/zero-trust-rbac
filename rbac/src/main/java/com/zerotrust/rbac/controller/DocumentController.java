package com.zerotrust.rbac.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/documents")
public class DocumentController {

    @GetMapping
    @PreAuthorize("hasAnyRole('READER', 'MANAGER')")
    public String listDocuments() {
        return "List of Confidencial Documents (Access: Reader e Manager)";
    }

    @PostMapping
    @PreAuthorize("hasRole('MANAGER')")
    public String createDocument() {
        return "Document created with success (Access: only Manager)";
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('MANAGER')")
    public String removeDocument(@PathVariable String id) {
        return "Document " + id + " excluded with success (Access: only Manager)";
    }
}
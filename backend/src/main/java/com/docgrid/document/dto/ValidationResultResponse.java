package com.docgrid.document.dto;

public record ValidationResultResponse(String ruleName, String severity, boolean passed, String message) {}

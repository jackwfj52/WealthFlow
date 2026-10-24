package org.jack.wealthflow.dto;

public record AgentProgressEvent(String id, String tool, String label, String status, String detail, String at) {}

package com.merchant.monitor.parser;

import java.time.Instant;

public record ElfLogEntry(
        String remoteHost,
        Instant timestamp,
        String method,
        String uri,
        int statusCode,
        long bytes,
        long latencyMs
) {
}

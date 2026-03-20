package com.merchant.monitor.parser;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class ElfLogParser {

    private static final Logger log = LoggerFactory.getLogger(ElfLogParser.class);

    private static final Pattern LOG_PATTERN = Pattern.compile(
            "^(\\S+) \\S+ \\S+ \\[([^\\]]+)\\] \"(\\S+) (\\S+) \\S+\" (\\d{3}) (\\d+|-) (\\d+)$"
    );

    private static final DateTimeFormatter TIMESTAMP_FORMAT =
            DateTimeFormatter.ofPattern("dd/MMM/yyyy:HH:mm:ss Z", Locale.ENGLISH);

    public List<ElfLogEntry> parse(String logFilePath, Instant from, Instant to) {
        List<ElfLogEntry> entries = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(new FileReader(logFilePath))) {
            String line;
            while ((line = reader.readLine()) != null) {
                Matcher matcher = LOG_PATTERN.matcher(line);
                if (!matcher.matches()) {
                    log.warn("Skipping malformed log line: {}", line);
                    continue;
                }

                try {
                    String remoteHost = matcher.group(1);
                    ZonedDateTime zdt = ZonedDateTime.parse(matcher.group(2), TIMESTAMP_FORMAT);
                    Instant timestamp = zdt.toInstant();
                    String method = matcher.group(3);
                    String uri = matcher.group(4);
                    int statusCode = Integer.parseInt(matcher.group(5));
                    String bytesStr = matcher.group(6);
                    long bytes = "-".equals(bytesStr) ? 0L : Long.parseLong(bytesStr);
                    long latencyMs = Long.parseLong(matcher.group(7));

                    if (!timestamp.isBefore(from) && !timestamp.isAfter(to)) {
                        entries.add(new ElfLogEntry(remoteHost, timestamp, method, uri, statusCode, bytes, latencyMs));
                    }
                } catch (Exception e) {
                    log.warn("Skipping malformed log line: {}", line, e);
                }
            }
        } catch (IOException e) {
            log.error("Failed to read log file: {}", logFilePath, e);
        }

        return entries;
    }
}

package com.fiw.fiw_bosses.config;

/**
 * One problem in a config file. ERROR = the file (or entry) is not loaded at all;
 * WARNING = it loads, but part of it is ignored or replaced with a default.
 */
public record ConfigIssue(Severity severity, String file, String field, String message) {

    public enum Severity { WARNING, ERROR }

    /** "bosses/titan.json → phases[0].abilities[1].type: unknown ability 'slahs'". */
    public String describe() {
        return file + (field == null || field.isEmpty() ? "" : " → " + field) + ": " + message;
    }
}

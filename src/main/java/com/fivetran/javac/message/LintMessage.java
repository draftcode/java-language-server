package com.fivetran.javac.message;

import com.fasterxml.jackson.annotation.JsonValue;

import java.net.URI;
import java.nio.file.Path;
import java.util.Optional;

public class LintMessage {

    private final String uri;

    /**
     * The range to which this diagnostic applies.
     */
    public final Range range;

    /**
     * The human-readable message.
     */
    public final String message;

    /**
     * A human-readable string describing the source of this
     * diagnostic, e.g. 'typescript' or 'super lint'.
     */
    public Optional<String> source = Optional.empty();

    /**
     * The severity, default is [error](#DiagnosticSeverity.Error).
     */
    public final Type severity;

    public LintMessage(String uri, Range range, String message, Type severity) {
        this.uri = uri;
        this.range = range;
        this.message = message;
        this.severity = severity;
    }

    /**
     * Must exactly match vscode.DiagnosticSeverity
     */
    public static enum Type {
        Error, Warning, Information, Hint;
        
        @JsonValue
        public int toJson() {
            return this.ordinal();
        }
    }
}

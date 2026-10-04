package com.example.gsb.leakdetector;

import java.time.Instant;
import java.util.Objects;

/** A single registered resource instance with its creation metadata. */
public final class TrackedResource {

    private final long id;
    private final ResourceType type;
    private final String description;
    private final CallSite callSite;
    private final Instant createdAt;

    public TrackedResource(long id, ResourceType type, String description, CallSite callSite, Instant createdAt) {
        this.id = id;
        this.type = Objects.requireNonNull(type, "type");
        this.description = Objects.requireNonNull(description, "description");
        this.callSite = callSite == null ? CallSite.UNKNOWN : callSite;
        this.createdAt = createdAt == null ? Instant.now() : createdAt;
    }

    public long id() {
        return id;
    }

    public ResourceType type() {
        return type;
    }

    public String description() {
        return description;
    }

    public CallSite callSite() {
        return callSite;
    }

    public Instant createdAt() {
        return createdAt;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof TrackedResource that && this.id == that.id;
    }

    @Override
    public int hashCode() {
        return Long.hashCode(id);
    }

    @Override
    public String toString() {
        return type + " '" + description + "' created at " + callSite;
    }
}

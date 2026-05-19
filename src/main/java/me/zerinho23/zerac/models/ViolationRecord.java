package me.zerinho23.zerac.models;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.UUID;

/**
 * Immutable record of a single anticheat violation flag.
 */
@Getter
@AllArgsConstructor
public class ViolationRecord {

    private final UUID   uuid;
    private final String checkName;
    private final double vl;
    private final String detail;
    private final String server;
    private final long   timestamp;
}

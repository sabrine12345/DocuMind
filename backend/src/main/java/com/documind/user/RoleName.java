package com.documind.user;

/**
 * Platform roles. SUPER_ADMIN is a global role, the other values are granted
 * per workspace through workspace_members.
 */
public enum RoleName {
    SUPER_ADMIN(1),
    ADMIN(2),
    MANAGER(3),
    VALIDATOR(4),
    USER(5);

    private final short id;

    RoleName(int id) {
        this.id = (short) id;
    }

    public short id() {
        return id;
    }
}

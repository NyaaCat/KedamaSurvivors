package cat.nyaa.survivors.model;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory representation of a team's state.
 * Thread-safe for concurrent access.
 */
public class TeamState {

    // Identity
    private final UUID teamId;
    private volatile String name;
    private volatile UUID leaderId;

    // Members
    private final Set<UUID> members = ConcurrentHashMap.newKeySet();
    private final Set<UUID> readyMembers = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Long> disconnectedMembers = new ConcurrentHashMap<>();

    // Invites (invitee UUID -> expiry timestamp)
    private final Map<UUID, Long> pendingInvites = new ConcurrentHashMap<>();

    // Run association
    private volatile UUID runId;

    // Timing
    private volatile long createdAtMillis;

    public TeamState(UUID teamId, String name, UUID leaderId) {
        this.teamId = teamId;
        this.name = name;
        this.leaderId = leaderId;
        this.createdAtMillis = System.currentTimeMillis();
        this.members.add(leaderId);
    }

    // ==================== Member Management ====================

    public boolean addMember(UUID playerId) {
        return members.add(playerId);
    }

    public boolean removeMember(UUID playerId) {
        readyMembers.remove(playerId);
        disconnectedMembers.remove(playerId);
        return members.remove(playerId);
    }

    public boolean isMember(UUID playerId) {
        return members.contains(playerId);
    }

    public Set<UUID> getMembers() {
        return Collections.unmodifiableSet(new HashSet<>(members));
    }

    public int getMemberCount() {
        return members.size();
    }

    public boolean isEmpty() {
        return members.isEmpty();
    }

    // ==================== Ready State ====================

    public void setReady(UUID playerId, boolean ready) {
        if (ready) {
            readyMembers.add(playerId);
        } else {
            readyMembers.remove(playerId);
        }
    }

    public boolean isReady(UUID playerId) {
        return readyMembers.contains(playerId);
    }

    public boolean isAllReady() {
        return readyMembers.containsAll(members);
    }

    public Set<UUID> getReadyMembers() {
        return Collections.unmodifiableSet(new HashSet<>(readyMembers));
    }

    public int getReadyCount() {
        return readyMembers.size();
    }

    public void clearReady() {
        readyMembers.clear();
    }

    // ==================== Disconnect Tracking ====================

    public void markDisconnected(UUID playerId) {
        if (members.contains(playerId)) {
            disconnectedMembers.put(playerId, System.currentTimeMillis());
        }
    }

    public void markReconnected(UUID playerId) {
        disconnectedMembers.remove(playerId);
    }

    public boolean isDisconnected(UUID playerId) {
        return disconnectedMembers.containsKey(playerId);
    }

    public long getDisconnectedTime(UUID playerId) {
        return disconnectedMembers.getOrDefault(playerId, 0L);
    }

    public Set<UUID> getDisconnectedMembers() {
        return Collections.unmodifiableSet(new HashSet<>(disconnectedMembers.keySet()));
    }

    /**
     * Removes members who have exceeded the grace period.
     * @param graceMillis grace period in milliseconds
     * @return set of removed player UUIDs
     */
    public Set<UUID> purgeExpiredDisconnects(long graceMillis) {
        Set<UUID> expired = new HashSet<>();
        long now = System.currentTimeMillis();

        disconnectedMembers.forEach((uuid, disconnectTime) -> {
            if (now - disconnectTime > graceMillis) {
                expired.add(uuid);
            }
        });

        expired.forEach(uuid -> {
            disconnectedMembers.remove(uuid);
            members.remove(uuid);
            readyMembers.remove(uuid);
        });

        return expired;
    }

    // ==================== Invite Management ====================

    public void addInvite(UUID playerId, long expiryMillis) {
        pendingInvites.put(playerId, expiryMillis);
    }

    public boolean hasInvite(UUID playerId) {
        Long expiry = pendingInvites.get(playerId);
        if (expiry == null) return false;
        if (System.currentTimeMillis() > expiry) {
            pendingInvites.remove(playerId);
            return false;
        }
        return true;
    }

    public void removeInvite(UUID playerId) {
        pendingInvites.remove(playerId);
    }

    public void clearExpiredInvites() {
        long now = System.currentTimeMillis();
        pendingInvites.entrySet().removeIf(entry -> now > entry.getValue());
    }

    public Set<UUID> getPendingInvites() {
        clearExpiredInvites();
        return Collections.unmodifiableSet(new HashSet<>(pendingInvites.keySet()));
    }

    // ==================== Wipe Detection ====================

    /**
     * Checks if the team is wiped (all members dead or disconnected past grace).
     * This should be called with a set of alive player UUIDs.
     */
    public boolean isWiped(Set<UUID> alivePlayers, long graceMillis) {
        long now = System.currentTimeMillis();

        for (UUID member : members) {
            // Check if member is alive
            if (alivePlayers.contains(member)) {
                // Check if not disconnected past grace
                Long disconnectTime = disconnectedMembers.get(member);
                if (disconnectTime == null || (now - disconnectTime < graceMillis)) {
                    return false; // At least one member is alive and connected (or within grace)
                }
            }
        }
        return true;
    }

    /**
     * Gets count of connected (not disconnected) members.
     */
    public int getConnectedCount() {
        return members.size() - disconnectedMembers.size();
    }

    // ==================== Leadership ====================

    public boolean isLeader(UUID playerId) {
        return leaderId.equals(playerId);
    }

    /**
     * Transfers leadership to another member.
     * @return true if transfer succeeded
     */
    public boolean transferLeadership(UUID newLeaderId) {
        if (!members.contains(newLeaderId)) {
            return false;
        }
        this.leaderId = newLeaderId;
        return true;
    }

    /**
     * Auto-selects a new leader from remaining members.
     * @return the new leader's UUID, or null if no members remain
     */
    public UUID autoSelectLeader() {
        if (members.isEmpty()) return null;

        // Prefer connected members
        for (UUID member : members) {
            if (!disconnectedMembers.containsKey(member)) {
                this.leaderId = member;
                return member;
            }
        }

        // Fallback to any member
        UUID newLeader = members.iterator().next();
        this.leaderId = newLeader;
        return newLeader;
    }

    // ==================== State Management ====================

    /**
     * Resets team state for a new run.
     */
    public void resetForNewRun() {
        readyMembers.clear();
        disconnectedMembers.clear();
        runId = null;
    }

    // ==================== Getters and Setters ====================

    public UUID getTeamId() { return teamId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public UUID getLeaderId() { return leaderId; }
    public void setLeaderId(UUID leaderId) { this.leaderId = leaderId; }

    public UUID getRunId() { return runId; }
    public void setRunId(UUID runId) { this.runId = runId; }

    public long getCreatedAtMillis() { return createdAtMillis; }
}

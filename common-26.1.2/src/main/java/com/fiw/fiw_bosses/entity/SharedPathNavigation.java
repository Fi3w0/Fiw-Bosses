package com.fiw.fiw_bosses.entity;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.Path;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Ground navigation for bosses and minions with a selectable mode ("pathfinding").
 * <ul>
 *   <li>{@code vanilla} (default): exactly GroundPathNavigation.</li>
 *   <li>{@code shared}: re-plans less often when the target is far, and entities chasing the
 *   same target with the same hitbox and water handling reuse a path a squad-mate computed in
 *   the last second, joining it at the nearest node (each gets its own copy of the nodes).
 *   Entities too far from that path search on their own and become the next leader.</li>
 * </ul>
 */
public class SharedPathNavigation extends GroundPathNavigation {

    private static final int SHARE_TICKS = 20;
    private static final double JOIN_DIST_SQ = 4.0 * 4.0;
    private static final double TARGET_MOVED_SQ = 2.0 * 2.0;
    private static final double KEEP_PATH_SQ = 4.0 * 4.0;
    // Latest path per squad key; entries expire after a few seconds.
    private static final Map<String, SharedPath> SQUADS = new HashMap<>();
    private static long lastCleanup;

    private record SharedPath(Path path, long tick, double tx, double ty, double tz) {}

    private boolean shared;
    private boolean swims;
    private long lastComputeTick = Long.MIN_VALUE / 2;

    public SharedPathNavigation(Mob mob, Level level) {
        super(mob, level);
    }

    public void setMode(boolean shared, boolean swims) {
        this.shared = shared;
        this.swims = swims;
    }

    public boolean isShared() {
        return shared;
    }

    @Override
    public Path createPath(Entity target, int accuracy) {
        if (!shared || target == null || !(level instanceof ServerLevel sl)) return super.createPath(target, accuracy);
        long now = sl.getGameTime();

        // Fewer re-plans: keep the current path while the target is still near where it leads.
        Path current = getPath();
        if (current != null && !current.isDone() && current.getTarget() != null
                && now - lastComputeTick < replanInterval(mob.distanceTo(target))
                && current.getTarget().distToCenterSqr(target.getX(), target.getY(), target.getZ()) <= KEEP_PATH_SQ) {
            return current;
        }

        String key = squadKey(sl, target);
        SharedPath squad = SQUADS.get(key);
        if (squad != null && now - squad.tick() <= SHARE_TICKS
                && target.distanceToSqr(squad.tx(), squad.ty(), squad.tz()) <= TARGET_MOVED_SQ) {
            Path joined = join(squad.path());
            if (joined != null) {
                lastComputeTick = now;
                return joined;
            }
        }

        Path computed = super.createPath(target, accuracy);
        lastComputeTick = now;
        if (computed != null) {
            SQUADS.put(key, new SharedPath(computed, now, target.getX(), target.getY(), target.getZ()));
        }
        cleanup(now);
        return computed;
    }

    /** A copy of the squad path from the node nearest to this entity, or null when none is close. */
    private Path join(Path source) {
        int best = -1;
        double bestDist = JOIN_DIST_SQ;
        for (int i = 0; i < source.getNodeCount(); i++) {
            Node n = source.getNode(i);
            double d = mob.distanceToSqr(n.x + 0.5, n.y, n.z + 0.5);
            if (d < bestDist) {
                bestDist = d;
                best = i;
            }
        }
        if (best < 0) return null;
        List<Node> nodes = new ArrayList<>(source.getNodeCount() - best);
        for (int i = best; i < source.getNodeCount(); i++) {
            Node n = source.getNode(i);
            nodes.add(n.cloneAndMove(n.x, n.y, n.z));
        }
        return new Path(nodes, source.getTarget(), source.canReach());
    }

    private String squadKey(ServerLevel level, Entity target) {
        return System.identityHashCode(level) + "|" + target.getUUID() + "|"
                + Math.round(mob.getBbWidth() * 4) + "x" + Math.round(mob.getBbHeight() * 4) + (swims ? "|swim" : "");
    }

    private static int replanInterval(double distance) {
        if (distance <= 16) return 5;
        if (distance <= 32) return 20;
        return 40;
    }

    private static void cleanup(long now) {
        if (now - lastCleanup < 200) return;
        lastCleanup = now;
        SQUADS.values().removeIf(sp -> now - sp.tick() > 100 || now < sp.tick());
    }
}

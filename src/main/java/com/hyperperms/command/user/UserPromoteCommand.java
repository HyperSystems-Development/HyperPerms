package com.hyperperms.command.user;

import com.hyperperms.HyperPerms;
import com.hyperperms.command.HpSubCommand;
import com.hyperperms.model.Group;
import com.hyperperms.model.Track;
import com.hyperperms.model.User;
import com.hyperperms.util.PlayerResolver;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;

import java.util.concurrent.CompletableFuture;

import static com.hyperperms.command.util.CommandUtil.*;

/**
 * Promotes a user along a promotion track.
 * Usage: /hp user promote &lt;player&gt; &lt;track&gt;
 */
public class UserPromoteCommand extends HpSubCommand {

    private final HyperPerms hyperPerms;
    private final RequiredArg<String> playerArg;
    private final RequiredArg<String> trackArg;

    public UserPromoteCommand(HyperPerms hyperPerms) {
        super("promote", "Promote a user along a track");
        this.hyperPerms = hyperPerms;
        this.playerArg = describeArg("player", "Player name or UUID", ArgTypes.STRING);
        this.trackArg = describeArg("track", "Track name", ArgTypes.STRING);
    }

    @Override
    protected CompletableFuture<Void> execute(CommandContext ctx) {
        String identifier = ctx.get(playerArg);
        String trackName = ctx.get(trackArg);

        // Find track
        Track track = hyperPerms.getTrackManager().getTrack(trackName);
        if (track == null) {
            ctx.sender().sendMessage(Message.raw("Track not found: " + trackName));
            return CompletableFuture.completedFuture(null);
        }

        // Check if track has groups
        if (track.isEmpty()) {
            ctx.sender().sendMessage(Message.raw("Track '" + trackName + "' has no groups defined."));
            return CompletableFuture.completedFuture(null);
        }

        // Find user
        User user = PlayerResolver.resolveOrCreate(hyperPerms, identifier);
        if (user == null) {
            ctx.sender().sendMessage(Message.raw("User not found: " + identifier));
            ctx.sender().sendMessage(Message.raw("Tip: Use UUID for offline players (e.g., from Tebex)"));
            return CompletableFuture.completedFuture(null);
        }

        // Find user's current group on this track
        String currentGroupOnTrack = null;
        for (String groupName : user.getInheritedGroups()) {
            if (track.containsGroup(groupName)) {
                // Use the highest position group if user has multiple groups on track
                if (currentGroupOnTrack == null || track.indexOf(groupName) > track.indexOf(currentGroupOnTrack)) {
                    currentGroupOnTrack = groupName;
                }
            }
        }

        String targetGroup;
        String actionMessage;

        if (currentGroupOnTrack == null) {
            // User not on track - start at first group
            targetGroup = track.getFirstGroup();
            actionMessage = "Added to track at";
        } else {
            // User is on track - get next group
            String nextGroup = track.getNextGroup(currentGroupOnTrack);
            if (nextGroup == null) {
                ctx.sender().sendMessage(
                    Message.raw(user.getFriendlyName() + " is already at the top of track '" + trackName + "' (" + currentGroupOnTrack + ")").color(GOLD)
                );
                return CompletableFuture.completedFuture(null);
            }
            targetGroup = nextGroup;
            actionMessage = "Promoted from " + currentGroupOnTrack + " to";
        }

        // Verify target group exists
        Group group = hyperPerms.getGroupManager().getGroup(targetGroup);
        if (group == null) {
            ctx.sender().sendMessage(Message.raw("Target group '" + targetGroup + "' does not exist. Please create the group first."));
            return CompletableFuture.completedFuture(null);
        }

        // Remove old group from track if present
        if (currentGroupOnTrack != null) {
            user.removeGroup(currentGroupOnTrack);
        }

        // Add new group
        user.addGroup(targetGroup);

        // Save changes
        hyperPerms.getUserManager().saveUser(user).join();
        hyperPerms.getCacheInvalidator().invalidate(user.getUuid());

        ctx.sender().sendMessage(
            Message.raw(actionMessage + " ").color(GRAY)
                .insert(Message.raw(targetGroup).color(GREEN))
                .insert(Message.raw(" for ").color(GRAY))
                .insert(Message.raw(user.getFriendlyName()).color(GREEN))
                .insert(Message.raw(" on track ").color(GRAY))
                .insert(Message.raw(trackName).color(GREEN))
        );

        return CompletableFuture.completedFuture(null);
    }
}

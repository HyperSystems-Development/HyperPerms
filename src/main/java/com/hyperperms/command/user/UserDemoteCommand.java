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
 * Demotes a user along a promotion track.
 * Usage: /hp user demote &lt;player&gt; &lt;track&gt;
 */
public class UserDemoteCommand extends HpSubCommand {

    private final HyperPerms hyperPerms;
    private final RequiredArg<String> playerArg;
    private final RequiredArg<String> trackArg;

    public UserDemoteCommand(HyperPerms hyperPerms) {
        super("demote", "Demote a user along a track");
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
        User user = PlayerResolver.resolve(hyperPerms, identifier);
        if (user == null) {
            ctx.sender().sendMessage(Message.raw("User not found: " + identifier));
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

        if (currentGroupOnTrack == null) {
            ctx.sender().sendMessage(
                Message.raw(user.getFriendlyName() + " is not on track '" + trackName + "'.").color(RED)
            );
            return CompletableFuture.completedFuture(null);
        }

        // Get previous group
        String previousGroup = track.getPreviousGroup(currentGroupOnTrack);
        if (previousGroup == null) {
            ctx.sender().sendMessage(
                Message.raw(user.getFriendlyName() + " is already at the bottom of track '" + trackName + "' (" + currentGroupOnTrack + ")").color(GOLD)
            );
            return CompletableFuture.completedFuture(null);
        }

        // Verify target group exists
        Group group = hyperPerms.getGroupManager().getGroup(previousGroup);
        if (group == null) {
            ctx.sender().sendMessage(Message.raw("Target group '" + previousGroup + "' does not exist. Please create the group first."));
            return CompletableFuture.completedFuture(null);
        }

        // Remove old group from track
        user.removeGroup(currentGroupOnTrack);

        // Add new group
        user.addGroup(previousGroup);

        // Save changes
        hyperPerms.getUserManager().saveUser(user).join();
        hyperPerms.getCache().invalidate(user.getUuid());

        ctx.sender().sendMessage(
            Message.raw("Demoted from " + currentGroupOnTrack + " to ").color(GRAY)
                .insert(Message.raw(previousGroup).color(RED))
                .insert(Message.raw(" for ").color(GRAY))
                .insert(Message.raw(user.getFriendlyName()).color(RED))
                .insert(Message.raw(" on track ").color(GRAY))
                .insert(Message.raw(trackName).color(RED))
        );

        return CompletableFuture.completedFuture(null);
    }
}

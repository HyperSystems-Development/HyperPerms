package com.hyperperms.commands;

import com.hyperperms.HyperPerms;
import com.hyperperms.util.Logger;
import com.hyperperms.web.ChangeApplier;
import com.hyperperms.web.WebEditorService;
import com.hyperperms.web.dto.Change;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.AbstractCommand;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Subcommand for /hp apply <code>.
 * Applies changes from the web editor using the provided apply code.
 */
public class ApplySubCommand extends AbstractCommand {

    private final HyperPerms hyperPerms;
    private final WebEditorService webEditorService;
    private final ChangeApplier changeApplier;
    private final RequiredArg<String> codeArg;

    @SuppressWarnings("this-escape")
    public ApplySubCommand(HyperPerms hyperPerms, WebEditorService webEditorService) {
        super("apply", "Apply changes from the web editor");
        this.hyperPerms = hyperPerms;
        this.webEditorService = webEditorService;
        this.changeApplier = new ChangeApplier(hyperPerms);
        this.codeArg = withRequiredArg("sessionId", "The session ID from the web editor", ArgTypes.STRING);
    }

    @Override
    protected CompletableFuture<Void> execute(CommandContext ctx) {
        String sessionId = ctx.get(codeArg);

        if (sessionId == null || sessionId.isEmpty()) {
            ctx.sender().sendMessage(Message.raw("Usage: /hp apply <session-id>"));
            ctx.sender().sendMessage(Message.raw("Get the session ID from the web editor after making changes."));
            return CompletableFuture.completedFuture(null);
        }

        ctx.sender().sendMessage(Message.raw("Fetching changes from web editor..."));

        // Fetch changes from the API
        webEditorService.fetchChanges(sessionId)
                .thenAccept(result -> {
                    if (!result.isSuccess()) {
                        ctx.sender().sendMessage(Message.raw("Failed to fetch changes: " + result.getError()));
                        return;
                    }

                    List<Change> changes = result.getChanges();
                    if (changes == null || changes.isEmpty()) {
                        ctx.sender().sendMessage(Message.raw("No changes found for this session."));
                        ctx.sender().sendMessage(Message.raw(""));
                        ctx.sender().sendMessage(Message.raw("This can happen if:"));
                        ctx.sender().sendMessage(Message.raw("  - No edits were made in the web editor"));
                        ctx.sender().sendMessage(Message.raw("  - The session expired (sessions last 24 hours)"));
                        ctx.sender().sendMessage(Message.raw("  - The session ID was copied incorrectly"));
                        ctx.sender().sendMessage(Message.raw(""));
                        ctx.sender().sendMessage(Message.raw("Check server logs for 'Changes API response' for details."));
                        return;
                    }

                    ctx.sender().sendMessage(Message.raw("Found " + changes.size() + " change(s). Applying..."));

                    // Apply changes
                    ChangeApplier.ApplyResult applyResult = changeApplier.applyChanges(changes);

                    // Report results
                    ctx.sender().sendMessage(Message.raw(""));
                    ctx.sender().sendMessage(Message.raw("=== Apply Results ==="));
                    ctx.sender().sendMessage(Message.raw("Successful: " + applyResult.getSuccessCount()));
                    ctx.sender().sendMessage(Message.raw("Failed: " + applyResult.getFailureCount()));

                    if (applyResult.hasErrors()) {
                        ctx.sender().sendMessage(Message.raw(""));
                        ctx.sender().sendMessage(Message.raw("Errors:"));
                        for (String error : applyResult.getErrors()) {
                            ctx.sender().sendMessage(Message.raw("  - " + error));
                        }
                    }

                    if (applyResult.getSuccessCount() > 0) {
                        ctx.sender().sendMessage(Message.raw(""));
                        ctx.sender().sendMessage(Message.raw("Changes have been applied and saved."));
                        Logger.info("Applied " + applyResult.getSuccessCount() + " changes from web editor session " + sessionId);
                    }
                })
                .exceptionally(e -> {
                    ctx.sender().sendMessage(Message.raw("Error applying changes: " + e.getMessage()));
                    Logger.warn("Failed to apply changes: " + e.getMessage());
                    return null;
                });

        return CompletableFuture.completedFuture(null);
    }
}

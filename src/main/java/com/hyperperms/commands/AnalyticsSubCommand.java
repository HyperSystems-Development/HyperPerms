package com.hyperperms.commands;

import com.hyperperms.HyperPerms;
import com.hyperperms.analytics.AnalyticsManager;
import com.hyperperms.analytics.AnalyticsSummary;
import com.hyperperms.util.Logger;
import com.hyperperms.util.TimeUtil;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.AbstractCommand;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.OptionalArg;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;

import java.awt.Color;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * Container command for analytics management: /hp analytics
 */
public class AnalyticsSubCommand extends AbstractCommand {

    private static final Color GOLD = new Color(255, 170, 0);
    private static final Color GREEN = new Color(85, 255, 85);
    private static final Color RED = new Color(255, 85, 85);
    private static final Color GRAY = Color.GRAY;
    private static final Color WHITE = Color.WHITE;
    private static final Color AQUA = new Color(85, 255, 255);
    private static final Color YELLOW = new Color(255, 255, 85);

    private static final DateTimeFormatter TIME_FORMAT = 
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final HyperPerms hyperPerms;

    @SuppressWarnings("this-escape")
    public AnalyticsSubCommand(HyperPerms hyperPerms) {
        super("analytics", "Permission analytics and audit logs");
        this.hyperPerms = hyperPerms;

        // Add subcommands
        addSubCommand(new SummarySubCommand());
        addSubCommand(new HotspotsSubCommand());
        addSubCommand(new UnusedSubCommand());
        addSubCommand(new AuditSubCommand());
        addSubCommand(new ExportSubCommand());

        // Help command
        addSubCommand(new AbstractCommand("help", "Show analytics help") {
            @Override
            protected CompletableFuture<Void> execute(CommandContext ctx) {
                ctx.sender().sendMessage(buildHelp());
                return CompletableFuture.completedFuture(null);
            }
        });
    }

    @Override
    protected CompletableFuture<Void> execute(CommandContext ctx) {
        ctx.sender().sendMessage(buildHelp());
        return CompletableFuture.completedFuture(null);
    }

    private Message buildHelp() {
        return Message.raw("--- Analytics Commands ---\n").color(GRAY)
                .insert(Message.raw("  /hp analytics summary").color(GOLD))
                .insert(Message.raw(" - Overview of permission health\n").color(WHITE))
                .insert(Message.raw("  /hp analytics hotspots [limit]").color(GOLD))
                .insert(Message.raw(" - Most checked permissions\n").color(WHITE))
                .insert(Message.raw("  /hp analytics unused [days]").color(GOLD))
                .insert(Message.raw(" - Unused permissions\n").color(WHITE))
                .insert(Message.raw("  /hp analytics audit [holder]").color(GOLD))
                .insert(Message.raw(" - Permission change history\n").color(WHITE))
                .insert(Message.raw("  /hp analytics export [--format]").color(GOLD))
                .insert(Message.raw(" - Export analytics data\n").color(WHITE))
                .insert(Message.raw("----------------------------").color(GRAY));
    }

    private AnalyticsManager getManager(CommandContext ctx) {
        AnalyticsManager manager = hyperPerms.getAnalyticsManager();
        if (manager == null || !manager.isEnabled()) {
            ctx.sender().sendMessage(Message.raw("Analytics is disabled. Enable it in config.json").color(RED));
            return null;
        }
        return manager;
    }

    /**
     * /hp analytics summary
     */
    private class SummarySubCommand extends AbstractCommand {
        SummarySubCommand() {
            super("summary", "Overview of permission health");
        }

        @Override
        protected CompletableFuture<Void> execute(CommandContext ctx) {
            AnalyticsManager manager = getManager(ctx);
            if (manager == null) return CompletableFuture.completedFuture(null);

            ctx.sender().sendMessage(Message.raw("Loading analytics summary...").color(GRAY));

            return manager.getSummary().thenAccept(summary -> {
                List<Message> parts = new ArrayList<>();
                parts.add(Message.raw("--- Analytics Summary ---\n").color(GOLD));
                
                parts.add(Message.raw("  Total Checks: ").color(GRAY)
                        .insert(Message.raw(formatNumber(summary.getTotalChecks()) + "\n").color(WHITE)));
                parts.add(Message.raw("  Grants: ").color(GRAY)
                        .insert(Message.raw(formatNumber(summary.getTotalGrants())).color(GREEN))
                        .insert(Message.raw(" | Denies: ").color(GRAY))
                        .insert(Message.raw(formatNumber(summary.getTotalDenies()) + "\n").color(RED)));
                parts.add(Message.raw("  Grant Rate: ").color(GRAY)
                        .insert(Message.raw(String.format("%.1f%%\n", summary.getGrantRate())).color(AQUA)));
                parts.add(Message.raw("  Unique Permissions: ").color(GRAY)
                        .insert(Message.raw(summary.getUniquePermissions() + "\n").color(WHITE)));
                
                if (summary.getPeriodStartMs() > 0) {
                    String period = formatTime(summary.getPeriodStartMs()) + " to " + formatTime(summary.getPeriodEndMs());
                    parts.add(Message.raw("  Period: ").color(GRAY)
                            .insert(Message.raw(period + "\n").color(GRAY)));
                }
                
                parts.add(Message.raw("-------------------------").color(GRAY));
                ctx.sender().sendMessage(Message.join(parts.toArray(new Message[0])));
            }).exceptionally(e -> {
                ctx.sender().sendMessage(Message.raw("Error loading summary: " + e.getMessage()).color(RED));
                return null;
            });
        }
    }

    /**
     * /hp analytics hotspots [limit]
     */
    private class HotspotsSubCommand extends AbstractCommand {
        private final OptionalArg<Integer> limitArg;

        HotspotsSubCommand() {
            super("hotspots", "Most frequently checked permissions");
            this.limitArg = withOptionalArg("limit", "Number to show (default: 10)", ArgTypes.INTEGER);
        }

        @Override
        protected CompletableFuture<Void> execute(CommandContext ctx) {
            AnalyticsManager manager = getManager(ctx);
            if (manager == null) return CompletableFuture.completedFuture(null);

            int limit = limitArg.get(ctx) != null ? limitArg.get(ctx) : 10;

            return manager.getHotspots(limit).thenAccept(hotspots -> {
                if (hotspots.isEmpty()) {
                    ctx.sender().sendMessage(Message.raw("No permission checks recorded yet.").color(GRAY));
                    return;
                }

                List<Message> parts = new ArrayList<>();
                parts.add(Message.raw("--- Top " + hotspots.size() + " Most Checked Permissions ---\n").color(GOLD));
                
                int rank = 1;
                for (AnalyticsSummary.PermissionStats stats : hotspots) {
                    String line = String.format("  %d. %s\n", rank++, stats.permission());
                    parts.add(Message.raw(line).color(WHITE));
                    
                    String details = String.format("     Checks: %s | Grant Rate: %.1f%%\n",
                            formatNumber(stats.checkCount()), stats.getGrantRate());
                    parts.add(Message.raw(details).color(GRAY));
                }
                
                parts.add(Message.raw("------------------------------------------").color(GRAY));
                ctx.sender().sendMessage(Message.join(parts.toArray(new Message[0])));
            }).exceptionally(e -> {
                ctx.sender().sendMessage(Message.raw("Error loading hotspots: " + e.getMessage()).color(RED));
                return null;
            });
        }
    }

    /**
     * /hp analytics unused [days]
     */
    private class UnusedSubCommand extends AbstractCommand {
        private final OptionalArg<Integer> daysArg;

        UnusedSubCommand() {
            super("unused", "Permissions not checked recently");
            this.daysArg = withOptionalArg("days", "Days since last check (default: 30)", ArgTypes.INTEGER);
        }

        @Override
        protected CompletableFuture<Void> execute(CommandContext ctx) {
            AnalyticsManager manager = getManager(ctx);
            if (manager == null) return CompletableFuture.completedFuture(null);

            int days = daysArg.get(ctx) != null ? daysArg.get(ctx) : 30;

            return manager.getUnusedPermissions(days).thenAccept(unused -> {
                if (unused.isEmpty()) {
                    ctx.sender().sendMessage(Message.raw("No unused permissions found (checked within " + days + " days).").color(GREEN));
                    return;
                }

                List<Message> parts = new ArrayList<>();
                parts.add(Message.raw("--- Unused Permissions (>" + days + " days) ---\n").color(YELLOW));
                
                int shown = Math.min(unused.size(), 20);
                for (int i = 0; i < shown; i++) {
                    parts.add(Message.raw("  - " + unused.get(i) + "\n").color(GRAY));
                }
                
                if (unused.size() > 20) {
                    parts.add(Message.raw("  ... and " + (unused.size() - 20) + " more\n").color(GRAY));
                }
                
                parts.add(Message.raw("Total: " + unused.size() + " unused permissions\n").color(WHITE));
                parts.add(Message.raw("--------------------------------------").color(GRAY));
                ctx.sender().sendMessage(Message.join(parts.toArray(new Message[0])));
            }).exceptionally(e -> {
                ctx.sender().sendMessage(Message.raw("Error loading unused permissions: " + e.getMessage()).color(RED));
                return null;
            });
        }
    }

    /**
     * /hp analytics audit [holder]
     */
    private class AuditSubCommand extends AbstractCommand {
        private final OptionalArg<String> holderArg;

        AuditSubCommand() {
            super("audit", "Permission change history");
            this.holderArg = withOptionalArg("holder", "group:name or user:uuid (or omit for recent)", ArgTypes.STRING);
        }

        @Override
        protected CompletableFuture<Void> execute(CommandContext ctx) {
            AnalyticsManager manager = getManager(ctx);
            if (manager == null) return CompletableFuture.completedFuture(null);

            String holder = holderArg.get(ctx);

            CompletableFuture<List<AnalyticsSummary.AuditEntry>> future;
            String title;

            if (holder != null && holder.contains(":")) {
                String[] parts = holder.split(":", 2);
                String type = parts[0].toLowerCase();
                String id = parts[1];
                future = manager.getAuditLog(type, id, 20);
                title = "Audit Log for " + type + ":" + id;
            } else {
                future = manager.getRecentAuditLog(20);
                title = "Recent Audit Log";
            }

            return future.thenAccept(entries -> {
                if (entries.isEmpty()) {
                    ctx.sender().sendMessage(Message.raw("No audit entries found.").color(GRAY));
                    return;
                }

                List<Message> parts = new ArrayList<>();
                parts.add(Message.raw("--- " + title + " ---\n").color(GOLD));
                
                for (AnalyticsSummary.AuditEntry entry : entries) {
                    String time = formatTime(entry.timestamp());
                    Color actionColor = getActionColor(entry.action());
                    
                    parts.add(Message.raw("  [" + time + "] ").color(GRAY)
                            .insert(Message.raw(entry.action()).color(actionColor))
                            .insert(Message.raw(" " + (entry.permission() != null ? entry.permission() : "(all)")).color(WHITE))
                            .insert(Message.raw("\n").color(WHITE)));
                    
                    String details = "    -> " + entry.holderType() + ":" + entry.holderId();
                    if (entry.executor() != null) {
                        details += " by " + entry.executor();
                    }
                    details += " (" + entry.source() + ")\n";
                    parts.add(Message.raw(details).color(GRAY));
                }
                
                parts.add(Message.raw("--------------------------------------").color(GRAY));
                ctx.sender().sendMessage(Message.join(parts.toArray(new Message[0])));
            }).exceptionally(e -> {
                ctx.sender().sendMessage(Message.raw("Error loading audit log: " + e.getMessage()).color(RED));
                return null;
            });
        }

        private Color getActionColor(String action) {
            return switch (action) {
                case "ADD" -> GREEN;
                case "REMOVE", "CLEAR", "EXPIRE" -> RED;
                case "UPDATE" -> YELLOW;
                default -> WHITE;
            };
        }
    }

    /**
     * /hp analytics export [--format=json|csv]
     */
    private class ExportSubCommand extends AbstractCommand {
        private final OptionalArg<String> formatArg;

        ExportSubCommand() {
            super("export", "Export analytics data");
            this.formatArg = withOptionalArg("format", "Format: json or csv (default: json)", ArgTypes.STRING);
        }

        @Override
        protected CompletableFuture<Void> execute(CommandContext ctx) {
            AnalyticsManager manager = getManager(ctx);
            if (manager == null) return CompletableFuture.completedFuture(null);

            String formatStr = formatArg.get(ctx);
            String format = "json";
            if (formatStr != null) {
                String lower = formatStr.toLowerCase().replace("--", "").replace("format=", "");
                if (lower.equals("csv")) {
                    format = "csv";
                }
            }

            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            Path exportPath = hyperPerms.getDataDirectory().resolve("exports/analytics_" + timestamp + "." + format);
            
            ctx.sender().sendMessage(Message.raw("Exporting analytics data...").color(YELLOW));

            String finalFormat = format;
            return manager.export(finalFormat, exportPath.toString()).thenAccept(success -> {
                if (success) {
                    ctx.sender().sendMessage(Message.raw("Analytics exported to: " + exportPath.getFileName()).color(GREEN));
                } else {
                    ctx.sender().sendMessage(Message.raw("Failed to export analytics").color(RED));
                }
            }).exceptionally(e -> {
                ctx.sender().sendMessage(Message.raw("Error exporting: " + e.getMessage()).color(RED));
                return null;
            });
        }
    }

    // ==================== Utility Methods ====================

    private String formatNumber(long num) {
        if (num >= 1_000_000) {
            return String.format("%.1fM", num / 1_000_000.0);
        } else if (num >= 1_000) {
            return String.format("%.1fK", num / 1_000.0);
        }
        return String.valueOf(num);
    }

    private String formatTime(long timestamp) {
        if (timestamp <= 0) return "N/A";
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(timestamp), ZoneId.systemDefault())
                .format(TIME_FORMAT);
    }
}

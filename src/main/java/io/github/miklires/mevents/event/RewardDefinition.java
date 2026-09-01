package io.github.miklires.mevents.event;

import java.util.List;
import java.util.regex.Pattern;

public record RewardDefinition(String id, double weight, List<String> commands) {
    private static final Pattern SAFE_ID = Pattern.compile("[a-z0-9][a-z0-9_-]{0,63}");

    public RewardDefinition {
        if (id == null || !SAFE_ID.matcher(id).matches()) throw new IllegalArgumentException("unsafe reward id");
        if (!Double.isFinite(weight) || weight <= 0 || weight > 1_000_000) throw new IllegalArgumentException("reward weight must be positive");
        commands = List.copyOf(commands);
        if (commands.isEmpty()) throw new IllegalArgumentException("reward requires at least one command");
        if (commands.size() > 32) throw new IllegalArgumentException("reward has too many commands");
        for (String command : commands) {
            if (command == null || command.isBlank() || command.length() > 512 || command.indexOf('\n') >= 0 || command.indexOf('\r') >= 0) {
                throw new IllegalArgumentException("invalid reward command");
            }
            if (command.startsWith("/")) throw new IllegalArgumentException("reward commands must not start with '/'");
        }
    }
}

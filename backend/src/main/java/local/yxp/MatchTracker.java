package local.yxp;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Reads the round history that the game publishes to BattleLog.json. */
final class MatchTracker {
    private static final int MAX_LOG_BYTES = 8 * 1024 * 1024;
    private static final int MAX_ROUND = 512;

    record CardSlot(String name, Integer grade, Integer realm, Integer cardId) {}
    record Player(String username, String character, String sect, Integer realm,
                  Integer destiny, Integer destinyDelta, Integer baseMaxHp,
                  Integer experienceAtRefresh, Integer physiqueAtRefresh,
                  Integer physiqueCapAtRefresh, String opponent, List<CardSlot> cards) {}
    record Round(int number, List<Player> players) {}
    record Snapshot(String status, String matchId, String updatedAt, List<Round> rounds) {}

    private record CardKey(String name, int realm, int rarity) {}
    private record ParsedLog(String matchId, Map<Integer, Round> rounds, boolean incomplete) {}

    private final Path log;
    private final Map<CardKey, Integer> uniqueCardIds = new HashMap<>();
    private final TreeMap<Integer, Round> rounds = new TreeMap<>();
    private BasicFileAttributes lastRead;
    private String matchId;
    private String updatedAt;
    private String status = "WAITING";
    private Snapshot cached = new Snapshot(status, null, null, List.of());

    MatchTracker(Path log, Rules rules) {
        this.log = log;
        var candidates = new HashMap<CardKey, List<Integer>>();
        for (var card : rules.cards) {
            var key = new CardKey(card.name(), card.level(), card.rarity());
            candidates.computeIfAbsent(key, unused -> new ArrayList<>()).add(card.id());
        }
        candidates.forEach((key, ids) -> {
            if (ids.size() == 1) uniqueCardIds.put(key, ids.getFirst());
        });
    }

    static Path gameLog() {
        String override = System.getProperty("yxp.gameDataDir");
        if (override != null && !override.isBlank()) return Path.of(override, "BattleLog.json");
        return Path.of(System.getProperty("user.home"), "AppData", "LocalLow",
                "DarkSunStudio", "YiXianPai", "BattleLog.json");
    }

    synchronized Snapshot snapshot() {
        try {
            var before = Files.readAttributes(log, BasicFileAttributes.class);
            if (sameVersion(before, lastRead)) return publish(status);
            if (!before.isRegularFile() || before.size() > MAX_LOG_BYTES) {
                return publish("UNREADABLE");
            }

            byte[] bytes;
            try (var input = Files.newInputStream(log)) {
                bytes = input.readNBytes(MAX_LOG_BYTES + 1);
            }
            var after = Files.readAttributes(log, BasicFileAttributes.class);
            if (bytes.length > MAX_LOG_BYTES) return publish("UNREADABLE");
            if (!sameVersion(before, after) || bytes.length != after.size()) {
                return publish("UPDATING");
            }

            ParsedLog parsed = parse(new String(bytes, StandardCharsets.UTF_8));
            if (parsed == null) {
                if (bytes.length == 0) return publish("EMPTY");
                for (byte value : bytes) if (value == '\n') return publish("UNRECOGNIZED");
                return publish("UPDATING");
            }
            if (!parsed.matchId().equals(matchId)) rounds.clear();
            matchId = parsed.matchId();
            rounds.putAll(parsed.rounds());
            updatedAt = Instant.ofEpochMilli(after.lastModifiedTime().toMillis()).toString();
            lastRead = after;
            status = parsed.incomplete() ? "UPDATING" : rounds.isEmpty() ? "FIRST_ROUND" : "READY";
            cached = new Snapshot(status, matchId, updatedAt, List.copyOf(rounds.values()));
            return cached;
        } catch (NoSuchFileException absent) {
            lastRead = null;
            return publish("MISSING");
        } catch (IOException | SecurityException unavailable) {
            return publish("UNREADABLE");
        }
    }

    private Snapshot publish(String displayStatus) {
        if (!cached.status().equals(displayStatus)) {
            cached = new Snapshot(displayStatus, matchId, updatedAt, cached.rounds());
        }
        return cached;
    }

    private static boolean sameVersion(BasicFileAttributes a, BasicFileAttributes b) {
        return b != null && a.size() == b.size() && a.lastModifiedTime().equals(b.lastModifiedTime())
                && java.util.Objects.equals(a.fileKey(), b.fileKey());
    }

    private ParsedLog parse(String logText) {
        int headerEnd = logText.indexOf('\n');
        if (headerEnd < 0) return null;
        String id = logText.substring(0, headerEnd).replace("\uFEFF", "").strip();
        if (!id.matches("-?[0-9]{1,19}")) return null;
        var parsed = new TreeMap<Integer, Round>();
        boolean incomplete = false;
        int start = headerEnd + 1;
        int end;
        while ((end = logText.indexOf('\n', start)) >= 0) {
            String line = logText.substring(start, end).strip();
            start = end + 1;
            if (line.isEmpty()) continue;
            try {
                var row = JsonParser.parseString(line);
                if (!row.isJsonObject()) continue;
                Round round = readRound(row.getAsJsonObject());
                if (round != null) parsed.put(round.number(), round);
            } catch (JsonParseException invalidLine) {
                // A damaged row does not prevent reading subsequent rounds.
            }
        }
        if (start < logText.length()) incomplete = true;
        return new ParsedLog(id, parsed, incomplete);
    }

    private Round readRound(JsonObject row) {
        Integer number = integer(row, "round");
        JsonArray playerRows = array(row, "players");
        if (number == null || number < 1 || number > MAX_ROUND
                || playerRows == null || playerRows.size() > 32) return null;
        var players = new ArrayList<Player>();
        for (var item : playerRows) {
            if (!item.isJsonObject()) continue;
            var player = item.getAsJsonObject();
            var cards = new ArrayList<CardSlot>();
            var cardRows = array(player, "usedCards");
            if (cardRows != null) {
                for (int slot = 0; slot < Math.min(cardRows.size(), 32); slot++) {
                    cards.add(readCard(cardRows.get(slot)));
                }
            }
            players.add(new Player(string(player, "username"), string(player, "character"),
                    string(player, "sect"), integer(player, "level"), integer(player, "life"),
                    integer(player, "lifeDelta"), integer(player, "maxHp"), integer(player, "exp"),
                    integer(player, "tiPo"), integer(player, "maxTiPo"), string(player, "opponentUsername"),
                    List.copyOf(cards)));
        }
        return players.isEmpty() ? null : new Round(number, List.copyOf(players));
    }

    private CardSlot readCard(JsonElement element) {
        if (!element.isJsonObject()) return new CardSlot(null, null, null, null);
        var row = element.getAsJsonObject();
        String name = string(row, "name");
        Integer rarity = integer(row, "rarity");
        Integer realm = integer(row, "level");
        if (name == null || name.isBlank()) return new CardSlot(null, null, null, null);
        Integer id = rarity != null && realm != null ? uniqueCardIds.get(new CardKey(name, realm, rarity)) : null;
        Integer grade = rarity != null && rarity >= 0 && rarity < 10 ? rarity + 1 : null;
        return new CardSlot(name, grade, realm, id);
    }

    private static JsonArray array(JsonObject row, String key) {
        var item = row.get(key);
        return item != null && item.isJsonArray() ? item.getAsJsonArray() : null;
    }

    private static String string(JsonObject row, String key) {
        var item = row.get(key);
        return item != null && item.isJsonPrimitive() && item.getAsJsonPrimitive().isString()
                ? item.getAsString() : null;
    }

    private static Integer integer(JsonObject row, String key) {
        var item = row.get(key);
        if (item == null || !item.isJsonPrimitive() || !item.getAsJsonPrimitive().isNumber()) return null;
        try {
            return item.getAsBigDecimal().intValueExact();
        } catch (ArithmeticException | NumberFormatException invalid) {
            return null;
        }
    }
}

package local.yxp;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

final class CachedBattleRecords {
    record Side(String uid, String name, int characterId, int hp, int maxHp, int realm, int destiny, int experience,
                int career, int physique, int physiqueCap, List<Integer> cards, List<Integer> hand,
                List<Integer> talents, List<Models.SeasonChoice> seasonTalents, Map<String,Integer> plantBuffs,
                Integer embryoGrowth, Integer deathBattleWill) {}
    record Round(int number, Side own, Side enemy, String first, String winner, int destinyLoss) {}
    record Record(int selectedRound, List<Round> rounds) {}
    private static final int[] BASE_HP = {0, 40, 45, 52, 62, 75, 100};

    static Record read(String code, Rules rules) throws IOException {
        if (!code.matches("[0-9a-zA-Z]{1,13}")) throw new Engine.Rejected("INVALID_INPUT", "请输入有效战绩码");
        long raw = Long.parseLong(code, 36);
        String digits = Long.toString(raw ^ 0x5FF17843B6B1FL);
        Set<Long> keys = new LinkedHashSet<>();
        try { keys.add(Long.parseLong(digits.substring(0,1) + new StringBuilder(digits.substring(1)).reverse())); }
        catch (NumberFormatException ignored) {}
        keys.add(raw);
        var root = Path.of(System.getenv().getOrDefault("USERPROFILE", System.getProperty("user.home")),
                "AppData", "LocalLow", "DarkSunStudio", "YiXianPai", "userLocalDatas");
        if (Files.isDirectory(root)) try (var accounts = Files.list(root)) {
            var folders = accounts.filter(Files::isDirectory).toList();
            for (long key : keys) {
                String filename = Long.toString(key - key % 1000 + key % 10, 36) + ".bin";
                for (var account : folders) for (var folder : List.of("recentBattleDatas", "starBattleDatas", "downloadBattleDatas")) {
                    var file = account.resolve(folder).resolve(filename);
                    if (!Files.isRegularFile(file)) continue;
                    try (var input = Files.newInputStream(file)) {
                        byte[] data = input.readNBytes(8_000_001);
                        if (data.length > 8_000_000) continue;
                        return decode(data, (int)(key % 1000 / 10), rules);
                    } catch (IllegalArgumentException | EOFException ignored) {}
                }
            }
        }
        throw new Engine.Rejected("NOT_FOUND", "本机没有可读取的这份战绩。可先在游戏内打开该战绩，再回来导入。");
    }

    static Record decode(byte[] data, int selected, Rules rules) throws IOException {
        var record = new Message(data);
        var rounds = new ArrayList<Round>();
        String anonymous = "record-player-" + (record.number(8) + 1);
        String owner = record.string(18);
        for (var row : record.messages(100)) {
            var a = side(row.child(1), rules);
            var b = side(row.child(2), rules);
            if (a.uid().isEmpty() && b.uid().isEmpty()) continue;
            boolean reverse = !b.uid().isEmpty() && (b.uid().equals(owner) || b.uid().equals(anonymous)
                    || owner.isEmpty() && b.uid().equals(row.string(11)));
            rounds.add(new Round(row.number(7), reverse ? b : a, reverse ? a : b,
                    row.string(8), row.string(9), row.number(6)));
        }
        if (rounds.isEmpty()) throw new EOFException("No cached rounds");
        return new Record(selected, rounds.stream().sorted(Comparator.comparingInt(Round::number)).toList());
    }

    private static Side side(Message player, Rules rules) throws IOException {
        var pub = player.child(1);
        var last = pub.child(200);
        var season = player.child(2).child(20);
        var options = season.dictionary(3);
        var privateIds = season.messages(1).stream().map(m -> m.number(3)).filter(id -> id > 0).toList();
        var ids = last.packed(17);
        if (ids.isEmpty()) ids = privateIds;
        var choices = ids.stream().filter(id -> !SeasonTalentEffects.isSkippedChoice(id) && id > 0).distinct().map(id -> {
            var talent = rules.seasonById.get(id);
            Integer setting = options.get(id);
            if (setting == null && privateIds.contains(id)) setting = 0;
            return new Models.SeasonChoice(id, talent != null && talent.slotRequired() && setting != null ? setting + 1 : null,
                    talent != null && talent.toggleable() && setting != null ? setting != 0 : null);
        }).toList();
        int realm = pub.number(6);
        int hp = (realm >= 0 && realm < BASE_HP.length ? BASE_HP[realm] : 0) + last.number(2);
        var buffs = last.dictionary(9);
        int physique = buffs.getOrDefault(10023, 0);
        return new Side(pub.string(1), pub.string(2), pub.number(12), hp, hp + physique, realm, last.number(1),
                last.number(3), pub.number(8), physique, buffs.getOrDefault(10024,0),
                last.packed(6), last.packed(7), last.packed(5), choices, PlantRefinements.fromCache(buffs),
                last.dictionary(8).get(92), buffs.get(17));
    }

    private static final class Message {
        private final Map<Integer,List<Object>> fields = new HashMap<>();
        Message(byte[] data) throws IOException {
            var input = new ByteArrayInputStream(data);
            while (input.available() > 0) {
                long tag = varint(input);
                int field = (int)(tag >>> 3);
                if (field <= 0) throw new EOFException();
                Object value = switch ((int)(tag & 7)) {
                    case 0 -> varint(input);
                    case 1 -> bytes(input, 8);
                    case 2 -> bytes(input, varint(input));
                    case 5 -> bytes(input, 4);
                    default -> throw new EOFException();
                };
                fields.computeIfAbsent(field, ignored -> new ArrayList<>()).add(value);
            }
        }
        private List<Object> values(int field) { return fields.getOrDefault(field, List.of()); }
        private Object first(int field) { return values(field).isEmpty() ? null : values(field).getFirst(); }
        int number(int field) { return first(field) instanceof Long n ? n.intValue() : 0; }
        String string(int field) { return first(field) instanceof byte[] b ? new String(b, StandardCharsets.UTF_8) : ""; }
        Message child(int field) throws IOException { return new Message(first(field) instanceof byte[] b ? b : new byte[0]); }
        List<Message> messages(int field) throws IOException {
            var result = new ArrayList<Message>();
            for (var value : values(field)) if (value instanceof byte[] b) result.add(new Message(b));
            return result;
        }
        List<Integer> packed(int field) throws IOException {
            var result = new ArrayList<Integer>();
            for (var value : values(field)) {
                if (value instanceof Long n) result.add(n.intValue());
                if (value instanceof byte[] b) {
                    var input = new ByteArrayInputStream(b);
                    while (input.available() > 0) result.add((int)varint(input));
                }
            }
            return result;
        }
        Map<Integer,Integer> dictionary(int field) throws IOException {
            var map = new HashMap<Integer,Integer>();
            for (var entry : messages(field)) map.put(entry.number(1), entry.number(2));
            return map;
        }
        private static byte[] bytes(ByteArrayInputStream input, long length) throws IOException {
            if (length < 0 || length > input.available()) throw new EOFException();
            return input.readNBytes((int)length);
        }
        private static long varint(ByteArrayInputStream input) throws IOException {
            long result = 0;
            for (int shift = 0; shift < 64; shift += 7) {
                int next = input.read();
                if (next < 0) break;
                result |= (long)(next & 127) << shift;
                if (next < 128) return result;
            }
            throw new EOFException();
        }
    }
}

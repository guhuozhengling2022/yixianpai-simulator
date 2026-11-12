package local.yxp;

import com.google.gson.*;
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpExchange;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.Executors;
import static local.yxp.Models.*;

public final class Server {
    private static final Gson JSON=new GsonBuilder().disableHtmlEscaping().create();
    private static final Set<String> ROOT_KEYS=Set.of("rulesetId","mode","initializationStage","firstActorId","players","battleParams");
    private static final Set<String> PLAYER_KEYS=Set.of("id","name","characterId","hp","maxHp","anima","defense","swordIntent","cards","talentIds","buffs","hand","seasonTalents","battleRound","cardOutcomes","singleUseCardLimit");
    private static final Set<String> NUMBERS=Set.of("characterId","hp","maxHp","anima","defense","swordIntent");
    private static void keys(JsonObject obj,Set<String> allowed) {
        for(String k:obj.keySet()) if(!allowed.contains(k)) throw new Engine.Rejected("INVALID_INPUT","未知字段："+k);
    }
    private static void integer(JsonElement e,String key) {
        if(e==null || !e.isJsonPrimitive() || !e.getAsJsonPrimitive().isNumber() || !e.toString().matches("-?\\d+") ) throw new Engine.Rejected("INVALID_INPUT",key+" 必须为整数");
        try {e.getAsBigDecimal().intValueExact();}catch(ArithmeticException ex){throw new Engine.Rejected("INVALID_INPUT",key+" 超出整数范围");}
    }
    private static Request parse(String body) {
        var parsed=JsonParser.parseString(body);if(!parsed.isJsonObject())throw new Engine.Rejected("INVALID_INPUT","请求必须为JSON对象");var obj=parsed.getAsJsonObject(); keys(obj,ROOT_KEYS);
        for(String k:List.of("rulesetId","mode","initializationStage","firstActorId")) {
            var v=obj.get(k);if(v==null||!v.isJsonPrimitive()||!v.getAsJsonPrimitive().isString()) throw new Engine.Rejected("INVALID_INPUT","缺少字符串字段："+k);
        }
        if(!obj.has("players")||!obj.get("players").isJsonArray()) throw new Engine.Rejected("INVALID_INPUT","players 必须为数组");
        for (var entry : obj.getAsJsonArray("players")) validatePlayerFields(entry.getAsJsonObject());
        if(obj.has("battleParams")&&!obj.get("battleParams").isJsonNull()){
            if(!obj.get("battleParams").isJsonArray())throw new Engine.Rejected("INVALID_INPUT","battleParams必须为整数数组");
            for(var item:obj.getAsJsonArray("battleParams"))integer(item,"battleParam");
        }
        return JSON.fromJson(obj,Request.class);
    }
    private static void validatePlayerFields(JsonObject player) {
        keys(player, PLAYER_KEYS);
        for (String key : NUMBERS) integer(player.get(key), key);
        if (player.has("singleUseCardLimit") && !player.get("singleUseCardLimit").isJsonNull()) {
            integer(player.get("singleUseCardLimit"), "持续与消耗牌上限");
        }
        for (String key : List.of("id", "name")) {
            var value = player.get(key);
            if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
                throw new Engine.Rejected("INVALID_INPUT", key + " 必须为字符串");
            }
        }
        if (!player.has("cards") || !player.get("cards").isJsonArray()) {
            throw new Engine.Rejected("INVALID_INPUT", "cards 必须为数组");
        }
        for (var card : player.getAsJsonArray("cards")) integer(card, "cardId");
        if (player.has("cardOutcomes") && !player.get("cardOutcomes").isJsonNull()) {
            if (!player.get("cardOutcomes").isJsonArray()) throw new Engine.Rejected("INVALID_INPUT", "指定结果必须为数组");
            for (var entry : player.getAsJsonArray("cardOutcomes")) {
                if (!entry.isJsonObject()) throw new Engine.Rejected("INVALID_INPUT", "卡槽指定结果格式错误");
                var outcome = entry.getAsJsonObject();
                keys(outcome, Set.of("slot", "cardId", "values"));
                integer(outcome.get("slot"), "slot");
                integer(outcome.get("cardId"), "cardId");
                if (!outcome.has("values") || !outcome.get("values").isJsonArray())
                    throw new Engine.Rejected("INVALID_INPUT", "卡槽指定结果必须为整数数组");
                for (var value : outcome.getAsJsonArray("values")) integer(value, "指定结果");
            }
        }
        if (player.has("battleRound") && !player.get("battleRound").isJsonNull()) integer(player.get("battleRound"), "battleRound");
        if (player.has("seasonTalents") && !player.get("seasonTalents").isJsonNull()) {
            if (!player.get("seasonTalents").isJsonArray()) throw new Engine.Rejected("INVALID_INPUT", "修道者仙命必须为数组");
            for (var entry : player.getAsJsonArray("seasonTalents")) {
                if (!entry.isJsonObject()) throw new Engine.Rejected("INVALID_INPUT", "修道者仙命必须为对象");
                var choice = entry.getAsJsonObject();
                keys(choice, Set.of("id", "slot", "disabled"));
                integer(choice.get("id"), "seasonTalentId");
                if (choice.has("slot") && !choice.get("slot").isJsonNull()) integer(choice.get("slot"), "slot");
                if (choice.has("disabled") && !choice.get("disabled").isJsonNull()
                        && (!choice.get("disabled").isJsonPrimitive() || !choice.get("disabled").getAsJsonPrimitive().isBoolean()))
                    throw new Engine.Rejected("INVALID_INPUT", "disabled 必须为布尔值");
            }
        }
        if (player.has("talentIds") && !player.get("talentIds").isJsonNull()) {
            if (!player.get("talentIds").isJsonArray()) throw new Engine.Rejected("INVALID_INPUT", "talentIds 必须为数组");
            for (var id : player.getAsJsonArray("talentIds")) integer(id, "talentId");
        }
        if (player.has("buffs") && !player.get("buffs").isJsonNull()) {
            if (!player.get("buffs").isJsonObject()) throw new Engine.Rejected("INVALID_INPUT", "buffs 必须为对象");
            for (var entry : player.getAsJsonObject("buffs").entrySet()) integer(entry.getValue(), entry.getKey());
        }
    }
    private static void damagePreview(HttpExchange x, Engine engine) throws IOException {
        byte[] body = x.getRequestBody().readNBytes(65537);
        if (body.length > 65536) {
            send(x, 413, Map.of("status", "INVALID_INPUT", "message", "请求超过64KB"));
            return;
        }
        var obj = JsonParser.parseString(new String(body, StandardCharsets.UTF_8)).getAsJsonObject();
        keys(obj, Set.of("rulesetId", "player"));
        var version = obj.get("rulesetId");
        if (version == null || !version.isJsonPrimitive() || !version.getAsJsonPrimitive().isString()
                || !obj.has("player") || !obj.get("player").isJsonObject()) {
            throw new Engine.Rejected("INVALID_INPUT", "请提供规则版本和己方配置");
        }
        var player = obj.getAsJsonObject("player");
        validatePlayerFields(player);
        if (player.has("hand") && !player.get("hand").isJsonNull()) {
            if (!player.get("hand").isJsonArray()) throw new Engine.Rejected("INVALID_INPUT", "hand 必须为数组");
            for (var id : player.getAsJsonArray("hand")) integer(id, "handCardId");
        }
        send(x, 200, engine.previewDamage(version.getAsString(), JSON.fromJson(player, Player.class)));
    }
    private static void send(HttpExchange x,int status,Object data) throws IOException {
        byte[] b=JSON.toJson(data).getBytes(StandardCharsets.UTF_8);
        x.getResponseHeaders().set("Content-Type","application/json; charset=utf-8");
        x.getResponseHeaders().set("Cache-Control","no-store");
        x.sendResponseHeaders(status,b.length);x.getResponseBody().write(b);x.close();
    }
    private static Map<String,Long> coverage(Rules rules,int sect){
        var cards=rules.cards.stream().filter(c->c.sect()==sect).toList();
        return Map.of("total",(long)cards.size(),"combat",cards.stream().filter(Rules.Card::supported).count(),"outsideBattle",cards.stream().filter(Rules.Card::preparationSupported).count(),"covered",cards.stream().filter(c->c.supported()||c.preparationSupported()).count());
    }
    public static void main(String[] args) throws IOException {
        int port=args.length>0?Integer.parseInt(args[0]):8080;
        var rules=new Rules();var engine=new Engine(rules);var preparation=new PreparationService(rules);
        var tracker=new MatchTracker(MatchTracker.gameLog(),rules);
        var searches=new DeckSearchJobs(new DeckSearch(rules,engine));
        var server=HttpServer.create(new InetSocketAddress("127.0.0.1",port),0);
        server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        server.createContext("/api/", x -> {
            try {
                String path=x.getRequestURI().getPath();
                if(path.equals("/api/match-tracker") && x.getRequestMethod().equals("GET")) {
                    send(x,200,tracker.snapshot());
                } else if(path.equals("/api/cached-battle") && x.getRequestMethod().equals("GET")) {
                    send(x,200,CachedBattleRecords.read(Objects.toString(x.getRequestURI().getQuery(), ""), rules));
                } else if(path.equals("/api/definitions") && x.getRequestMethod().equals("GET")) {
                    var definitions=new LinkedHashMap<String,Object>(Map.of("statusDefinitions",Rules.STATUS_NAMES,"rulesetId",Rules.ID,"cards",rules.cards,"characters",rules.characters,"talents",rules.talents,"supportedCount",rules.cards.stream().filter(Rules.Card::supported).count(),"preparationSupportedCount",rules.cards.stream().filter(Rules.Card::preparationSupported).count(),"coverage",Map.of("cloud",coverage(rules,1),"five",coverage(rules,3),"star",coverage(rules,2),"forge",coverage(rules,4)),"careerCoverage",rules.cards.stream().filter(CareerEffects::isCareer).collect(java.util.stream.Collectors.groupingBy(Rules.Card::career,java.util.stream.Collectors.collectingAndThen(java.util.stream.Collectors.toList(),cards->Map.of("total",cards.size(),"combat",cards.stream().filter(Rules.Card::supported).count(),"outsideOnly",cards.stream().filter(c->!c.supported()&&c.preparationSupported()).count())))),"scope","四门派和独立副职静态恢复；部分效果需battleParams，抽牌结果需显式提供；按勾选仙命结算角色能力；其余目录明确标记未支持，尚未官方实战校准"));definitions.put("talentCatalog",rules.talentCatalog);definitions.put("talentSupportedCount",rules.talents.size());
                    definitions.put("damagePreview", true);
                    definitions.put("deckSearch", true);
                    definitions.put("cachedBattleImport", true);
                    definitions.put("cardOutcomeInputs", true);
                    definitions.put("automaticInitiative", true);
                    definitions.put("balancePatch", "2026-11-12");
                    definitions.put("singleUseCardLimit", true);
                    definitions.put("plantRefinements", Arrays.stream(PlantRefinements.values()).map(plant ->
                            Map.of("key", plant.key, "cardId", plant.cardId, "name", rules.byId.get(plant.cardId).name())).toList());
                    definitions.put("seasonTalents", rules.seasonTalents.stream().map(t -> Map.of(
                            "id", t.id(), "name", t.name(), "description", t.description(), "effectRound", t.effectRound(),
                            "battle", t.battle(), "supported", t.supported(), "slotRequired", t.slotRequired(), "toggleable", t.toggleable(),
                            "available", t.available(), "requiredCharacter", t.requiredCharacter())).toList());
                    send(x,200,definitions);
                } else if(path.equals("/api/screenshot-import")&&x.getRequestMethod().equals("POST")){
                    byte[] bytes=x.getRequestBody().readNBytes(12582913);if(bytes.length>12582912){send(x,413,Map.of("message","截图不能超过12MB"));return;}
                    send(x,200,ScreenshotImport.recognize(bytes,"mode=game".equals(x.getRequestURI().getRawQuery())?"game":"record"));
                } else if (path.equals("/api/damage-preview") && x.getRequestMethod().equals("POST")) {
                    damagePreview(x, engine);
                } else if (path.equals("/api/deck-search") && x.getRequestMethod().equals("POST")) {
                    byte[] body = x.getRequestBody().readNBytes(65537);
                    if (body.length > 65536) {
                        send(x, 413, Map.of("status", "INVALID_INPUT", "message", "请求超过64KB"));
                        return;
                    }
                    send(x, 202, searches.start(parse(new String(body, StandardCharsets.UTF_8))));
                } else if (path.startsWith("/api/deck-search/")
                        && Set.of("GET", "DELETE").contains(x.getRequestMethod())) {
                    String id = path.substring("/api/deck-search/".length());
                    var view = searches.get(id, x.getRequestMethod().equals("DELETE"));
                    if (view == null) send(x, 404, Map.of("message", "求解记录已失效，请重新求解。"));
                    else send(x, 200, view);
                } else if(path.equals("/api/simulations") && x.getRequestMethod().equals("POST")) {
                    byte[] body=x.getRequestBody().readNBytes(65537);
                    if(body.length>65536){send(x,413,Map.of("status","INVALID_INPUT","message","请求超过64KB"));return;}
                    send(x,200,engine.simulate(parse(new String(body,StandardCharsets.UTF_8))));
                } else if(path.equals("/api/career-preparations")&&x.getRequestMethod().equals("POST")){
                    byte[] body=x.getRequestBody().readNBytes(65537);if(body.length>65536){send(x,413,Map.of("message","请求超过64KB"));return;}
                    var obj=JsonParser.parseString(new String(body,StandardCharsets.UTF_8)).getAsJsonObject();keys(obj,Set.of("rulesetId","sourceCardId","hand","deck","outcomes","targetSlots"));
                    integer(obj.get("sourceCardId"),"sourceCardId");
                    for(String key:List.of("hand","deck","outcomes","targetSlots")){if(obj.has(key)){if(!obj.get(key).isJsonArray())throw new Engine.Rejected("INVALID_INPUT",key+"必须为数组");for(var value:obj.getAsJsonArray(key))integer(value,key);}}
                    send(x,200,new CareerPreparationService(rules).resolve(JSON.fromJson(obj,CareerPreparationService.Input.class)));
                } else if(path.equals("/api/preparations")&&x.getRequestMethod().equals("POST")){
                    byte[] body=x.getRequestBody().readNBytes(65537);if(body.length>65536){send(x,413,Map.of("message","请求超过64KB"));return;}
                    var obj=JsonParser.parseString(new String(body,StandardCharsets.UTF_8)).getAsJsonObject();keys(obj,Set.of("rulesetId","sourceCardId","deck","outcomes"));
                    integer(obj.get("sourceCardId"),"sourceCardId");
                    if(!obj.has("rulesetId")||!obj.get("rulesetId").isJsonPrimitive()||!obj.getAsJsonPrimitive("rulesetId").isString())throw new Engine.Rejected("INVALID_INPUT","rulesetId必须为字符串");
                    for(String key:List.of("deck","outcomes")){if(!obj.has(key)||!obj.get(key).isJsonArray())throw new Engine.Rejected("INVALID_INPUT",key+"必须为数组");for(var value:obj.getAsJsonArray(key))integer(value,key);}
                    send(x,200,preparation.resolve(JSON.fromJson(obj,PreparationService.Input.class)));
                } else send(x,404,Map.of("message","接口不存在或请求方法不匹配"));
            } catch(Engine.Rejected e){send(x,422,Map.of("status",e.status,"message",e.getMessage()));}
            catch(JsonParseException|IllegalStateException|ClassCastException|NumberFormatException e){send(x,400,Map.of("status","INVALID_INPUT","message","JSON格式或字段类型错误"));}
            catch(Exception e){e.printStackTrace();send(x,500,Map.of("status","ERROR","message","服务器内部错误"));}
        });
        Path web=Path.of("frontend/dist").toAbsolutePath().normalize();
        server.createContext("/",x -> {
            if(!x.getRequestMethod().equals("GET")){send(x,405,Map.of("message","只支持GET"));return;}
            Path file=web.resolve(x.getRequestURI().getPath().substring(1)).normalize();
            if(!file.startsWith(web)){send(x,403,Map.of("message","禁止访问"));return;}
            if(Files.isDirectory(file))file=file.resolve("index.html");
            if(!Files.isRegularFile(file)){send(x,404,Map.of("message","前端未构建，请先运行 scripts/build.ps1"));return;}
            String name=file.toString();String type=name.endsWith(".html")?"text/html; charset=utf-8":name.endsWith(".js")?"text/javascript; charset=utf-8":name.endsWith(".png")?"image/png":name.endsWith(".css")?"text/css; charset=utf-8":"application/octet-stream";
            x.getResponseHeaders().set("Content-Type",type);x.getResponseHeaders().set("X-Content-Type-Options","nosniff");
            x.sendResponseHeaders(200,Files.size(file));try(var out=x.getResponseBody()){Files.copy(file,out);}x.close();
        });
        server.start();System.out.println("YiXianPai simulator: http://127.0.0.1:"+port);
    }
}

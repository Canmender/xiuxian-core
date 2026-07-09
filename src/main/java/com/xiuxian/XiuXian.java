package com.xiuxian;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;

public class XiuXian extends JavaPlugin implements CommandExecutor, Listener {
    private final Map<UUID, PlayerData> playerData = new HashMap<>();
    private final Set<UUID> silentPlayers = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Long> lct = new HashMap<>();
    
    private int intervalSeconds = 15, baseLingqi = 1;
    private double lingqiCoefficient = 0.1, minLingqi = 1;
    private int islandRange = 200, levelCooldown = 120;
    private int realmLevels = 100, chongLevels = 10;
    private String formula;
    private double xpBase = 80, xpRate = 1.014;
    

    
    
    private static final String G = ChatColor.GREEN.toString(), A = ChatColor.AQUA.toString(),
                                Y = ChatColor.GRAY.toString(), W = ChatColor.YELLOW.toString(),
                                LA = ChatColor.LIGHT_PURPLE.toString();
    static final String[] CN = {"\u4E00","\u4E8C","\u4E09","\u56DB","\u4E94",
                                 "\u516D","\u4E03","\u516B","\u4E5D","\u5341"};

    private Object islandsManager = null;
    private Method getIslandMethod = null;
    private World ow = null;
    private File levelDbDir = null;
    
    List<String> ltRealms = new ArrayList<>(), xfRealms = new ArrayList<>();
    private String lingqiMessage, liantiName, xiufaName, breakthroughMessage, zeroLevelName, maxLevelSuffix;
    private File dataDir;

    static class PlayerData {
        String activeType;
        int liantiLevel, xiufaLevel;
        double liantiXp, xiufaXp, mana;
        
        PlayerData(String activeType, int liantiLevel, double liantiXp, int xiufaLevel, double xiufaXp, double mana) {
            this.activeType = activeType;
            this.liantiLevel = liantiLevel;
            this.liantiXp = liantiXp;
            this.xiufaLevel = xiufaLevel;
            this.xiufaXp = xiufaXp;
            this.mana = mana;
        }
        
        int getLevel() { return activeType.equals("lianti") ? liantiLevel : xiufaLevel; }
        double getXp() { return activeType.equals("lianti") ? liantiXp : xiufaXp; }
        void setXp(double xp) { if (activeType.equals("lianti")) liantiXp = xp; else xiufaXp = xp; }
        void setLevel(int level) { if (activeType.equals("lianti")) liantiLevel = level; else xiufaLevel = level; }
    }

    @Override
    public void onEnable() {
        saveDefaultConfig();
        reloadConfig();
        loadConfig();
        
        dataDir = new File(getDataFolder(), "data");
        if (!dataDir.exists()) dataDir.mkdirs();
        
        getCommand("xw").setExecutor(this);
        Bukkit.getPluginManager().registerEvents(this, this);
        
        initBentoBox();
        registerPAPI();
        
        getLogger().info("XiuXianCore enabled");
        
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            for (Player p : Bukkit.getOnlinePlayers()) {
                try { process(p); } catch (Exception e) { getLogger().warning("Process error: " + e.getMessage()); }
            }
        }, 80L, intervalSeconds * 20L);
        
        // Action bar display
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            for (Player p : Bukkit.getOnlinePlayers()) {
                try {
                    PlayerData data = playerData.get(p.getUniqueId());
                    if (data != null) {
                        String msg = "\u00a77\u4fee\u4e3a\u4e2d...";
                        p.spigot().sendMessage(net.md_5.bungee.api.ChatMessageType.ACTION_BAR, net.md_5.bungee.api.chat.TextComponent.fromLegacyText(msg));
                    }
                } catch (Exception e) {}
            }
        }, 20L, 20L);
        

    }

    @Override
    public void onDisable() {
        for (Player p : Bukkit.getOnlinePlayers()) savePlayerData(p.getUniqueId());
        playerData.clear(); lct.clear(); silentPlayers.clear();
    }

    // ========== PlaceholderAPI ==========
    
    private void registerPAPI() {
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new XiuXianExpansion(this).register();
            getLogger().info("PAPI placeholders registered");
        }
    }
    
    public static class XiuXianExpansion extends PlaceholderExpansion {
        private final XiuXian plugin;
        public XiuXianExpansion(XiuXian plugin) { this.plugin = plugin; }
        @Override public String getIdentifier() { return "cultivation"; }
        @Override public String getAuthor() { return "XiuXianCore"; }
        @Override public String getVersion() { return "3.4"; }
        @Override public boolean persist() { return true; }
        
        @Override
        public String onPlaceholderRequest(Player player, String identifier) {
            if (player == null) return "";
            PlayerData data = plugin.playerData.get(player.getUniqueId());
            if (data == null) data = new PlayerData("lianti", 0, 0, 0, 0, 0);
            
            switch (identifier) {
                case "level": return String.valueOf(data.getLevel());
                case "realm": return plugin.getRealm(data.getLevel(), data.activeType.equals("lianti"));
                case "xp": return String.format("%.1f", data.getXp());
                case "xp_needed": return String.format("%.0f", plugin.getXpForLevel(data.getLevel() + 1));
                case "progress":
                    if (data.getLevel() >= 1300) return "100.0";
                    double needed = plugin.getXpForLevel(data.getLevel() + 1);
                    return String.format("%.1f", data.getXp() / needed * 100);
                case "type": return data.activeType.equals("lianti") ? "\u70BC\u4F53" : "\u6CD5\u4FEE";
                case "type_raw": return data.activeType;
                case "mana": return "0";
                case "max_mana": return "0";
                case "mana_percent": return "0";
                case "hp": return "0";
                case "armor": return "0";
                case "attack": return "0";
                case "lianti_level": return String.valueOf(data.liantiLevel);
                case "xiufa_level": return String.valueOf(data.xiufaLevel);
                case "lianti_realm": return plugin.getRealm(data.liantiLevel, true);
                case "xiufa_realm": return plugin.getRealm(data.xiufaLevel, false);
                default: return null;
            }
        }
    }

    // ========== 配置加载 ==========
    
    private void loadConfig() {
        FileConfiguration cfg = getConfig();
        intervalSeconds = cfg.getInt("interval-seconds", 15);
        baseLingqi = cfg.getInt("base-lingqi", 1);
        lingqiCoefficient = cfg.getDouble("lingqi-coefficient", 0.1);
        minLingqi = cfg.getDouble("min-lingqi", 1);
        formula = cfg.getString("formula", "{base} + {level} * {coeff}");
        islandRange = cfg.getInt("island-range", 200);
        levelCooldown = cfg.getInt("level-cooldown", 120);
        realmLevels = cfg.getInt("realm-levels", 100);
        chongLevels = cfg.getInt("chong-levels", 10);
        xpBase = cfg.getDouble("xp-base", 80);
        xpRate = cfg.getDouble("xp-rate", 1.014);
        

        
        ltRealms = cfg.getStringList("lian-ti-realms");
        xfRealms = cfg.getStringList("fa-xiu-realms");
        if (ltRealms.isEmpty()) ltRealms = Arrays.asList("\u901A\u8109","\u953B\u9AA8","\u7EC3\u817F","\u5143\u6B66","\u795E\u529B","\u7834\u865A","\u6DF7\u5143","\u5927\u6210","\u6D85\u69C3","\u771F\u6B66","\u91D1\u76F8","\u592A\u4E0A","\u7F57\u5929");
        if (xfRealms.isEmpty()) xfRealms = Arrays.asList("\u7EC3\u6C14","\u7B51\u57FA","\u7ED3\u4E39","\u5143\u5A75","\u5316\u795E","\u8FD4\u865A","\u5408\u4F53","\u5927\u4E58","\u6E21\u52AB","\u771F\u4ED9","\u91D1\u4ED9","\u592A\u4E59","\u5927\u7F57");
        
        lingqiMessage = cfg.getString("lingqi-message", "&a+{amount} &b{type}");
        liantiName = cfg.getString("lianti-name", "&a\u70BC\u4F53\u4FEE\u4E3A");
        xiufaName = cfg.getString("xiufa-name", "&b\u6CD5\u4FEE\u4FEE\u4E3A");
        breakthroughMessage = cfg.getString("breakthrough-message", "&e{player} &7\u5F53\u524D\u5883\u754C\u63D0\u5347");
        zeroLevelName = cfg.getString("zero-level-name", "\u51E1\u4EBA\u4E4B\u8EAB");
        maxLevelSuffix = cfg.getString("max-level-suffix", "\u00B7\u5706\u6EE1");
    }

    private void initBentoBox() {
        levelDbDir = new File("plugins" + File.separator + "BentoBox" + File.separator + "database" + File.separator + "IslandLevels");
        Bukkit.getScheduler().runTaskLater(this, () -> {
            try {
                Plugin bentoBoxPlugin = Bukkit.getPluginManager().getPlugin("BentoBox");
                if (bentoBoxPlugin == null) { getLogger().warning("BentoBox not found"); return; }
                islandsManager = bentoBoxPlugin.getClass().getMethod("getIslands").invoke(bentoBoxPlugin);
                getIslandMethod = islandsManager.getClass().getMethod("getIsland", World.class, UUID.class);
                ow = Bukkit.getWorld(getConfig().getString("island-world", "oneblock_world"));
                getLogger().info("BentoBox connected");
            } catch (Exception e) { getLogger().warning("BentoBox init failed: " + e.getMessage()); }
        }, 100L);
    }

    // ========== 命令系统 ==========
    
    @Override
    public boolean onCommand(CommandSender s, Command c, String l, String[] a) {
        // 管理员命令
        if (a.length > 0 && (a[0].equals("addxp") || a[0].equals("setlevel") || a[0].equals("addlevel"))) {
            if (!s.hasPermission("xiuxian.admin")) { s.sendMessage("\u6CA1\u6709\u6743\u9650"); return true; }
            if (a.length < 3) { s.sendMessage("\u7528\u6CD5: /xw " + a[0] + " <\u73A9\u5BB6> <\u6570\u503C>"); return true; }
            Player target = Bukkit.getPlayer(a[1]);
            if (target == null) { s.sendMessage("\u73A9\u5BB6 " + a[1] + " \u4E0D\u5728\u7EBF"); return true; }
            PlayerData tData = getOrCreateData(target.getUniqueId());
            try {
                double amount = Double.parseDouble(a[2]);
                if (a[0].equals("addxp")) { tData.setXp(tData.getXp() + amount); s.sendMessage("\u7ED9\u4E88 " + target.getName() + " " + amount + " \u7ECF\u9A8C"); }
                else if (a[0].equals("setlevel")) { tData.setLevel((int) amount); tData.setXp(0); s.sendMessage("\u8BBE\u7F6E " + target.getName() + " \u7B49\u7EA7\u4E3A " + (int) amount); }
                else if (a[0].equals("addlevel")) { tData.setLevel(tData.getLevel() + (int) amount); s.sendMessage("\u7ED9\u4E88 " + target.getName() + " +" + (int) amount + " \u7B49\u7EA7"); }
                savePlayerData(target.getUniqueId());
                process(target);
            } catch (NumberFormatException e) { s.sendMessage("\u6570\u503C\u65E0\u6548"); }
            return true;
        }
        
        if (!(s instanceof Player)) { s.sendMessage("\u53EA\u6709\u73A9\u5BB6\u53EF\u4EE5\u4F7F\u7528\u6B64\u547D\u4EE4"); return true; }
        Player p = (Player) s;
        UUID u = p.getUniqueId();
        PlayerData data = getOrCreateData(u);
        
        if (a.length == 0) {
            showStatus(p, data);
            return true;
        }
        if (a[0].equals("leixing")) {
            p.sendMessage(Y + "\u5F53\u524D\u4FEE\u70BC: " + (data.activeType.equals("lianti") ? G + "\u70BC\u4F53" : A + "\u6CD5\u4FEE"));
            p.sendMessage(Y + "\u70BC\u4F53\u7B49\u7EA7: " + data.liantiLevel + " | \u6CD5\u4FEE\u7B49\u7EA7: " + data.xiufaLevel);
            return true;
        }
        if (a[0].startsWith("l")) { data.activeType = "lianti"; savePlayerData(u); p.sendMessage(G + "\u5207\u6362\u5F53\u524D\u4FEE\u70BC\u5230\u70BC\u4F53"); }
        else if (a[0].startsWith("x")) { data.activeType = "xiufa"; savePlayerData(u); p.sendMessage(A + "\u5207\u6362\u5F53\u524D\u4FEE\u70BC\u5230\u6CD5\u4FEE"); }
        else { p.sendMessage(Y + "/xw [lianti|xiufa|leixing]"); }
        return true;
    }
    
    private void showStatus(Player p, PlayerData data) {
        String ltRealm = getRealm(data.liantiLevel, true);
        String xfRealm = getRealm(data.xiufaLevel, false);
        double ltProgress = data.liantiLevel >= 1300 ? 100 : (data.liantiXp / getXpForLevel(data.liantiLevel + 1) * 100);
        double xfProgress = data.xiufaLevel >= 1300 ? 100 : (data.xiufaXp / getXpForLevel(data.xiufaLevel + 1) * 100);
        
        p.sendMessage(LA + "\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501");
        p.sendMessage(W + "\u70BC\u4F53 " + G + ltRealm + " ");
        p.sendMessage(Y + "\u7ECF\u9A8C: " + W + String.format("%.1f", data.liantiXp) + " / " + String.format("%.0f", getXpForLevel(data.liantiLevel + 1)) + " (" + String.format("%.1f%%", ltProgress) + ")");
        p.sendMessage(LA + "\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500");
        p.sendMessage(W + "\u6CD5\u4FEE " + A + xfRealm + " ");
        p.sendMessage(Y + "\u7ECF\u9A8C: " + W + String.format("%.1f", data.xiufaXp) + " / " + String.format("%.0f", getXpForLevel(data.xiufaLevel + 1)) + " (" + String.format("%.1f%%", xfProgress) + ")");
        p.sendMessage(LA + "\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500");
        p.sendMessage(Y + "\u5F53\u524D\u4FEE\u70BC: " + (data.activeType.equals("lianti") ? G + "\u70BC\u4F53" : A + "\u6CD5\u4FEE"));
        // 真元由XiuXianCombat管理
        p.sendMessage(LA + "\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501");
    }

    // ========== 核心处理 ==========
    
    private void process(Player p) {
        UUID u = p.getUniqueId();
        PlayerData data = getOrCreateData(u);
        String n = p.getName();
        
        long il = getIl(p);
        long nw = System.currentTimeMillis() / 1000;
        long ls = lct.getOrDefault(u, 0L);
        int of = Math.abs(u.hashCode() % levelCooldown);
        if (ls == 0) { lct.put(u, nw - of); }
        else if ((nw - ls) > levelCooldown) {
            lct.put(u, nw);
            if (isOnOwnIsland(p)) { silentPerformCommand(p, "is level"); il = getIl(p); }
        }
        
        double raw = evaluateFormula(baseLingqi, il, lingqiCoefficient);
        if (raw < minLingqi) raw = minLingqi;
        
        // 给主修类型加经验
        data.setXp(data.getXp() + raw);
        
        String xwName = data.activeType.equals("lianti") ? colorize(liantiName) : colorize(xiufaName);
        String msg = lingqiMessage.replace("{amount}", String.format("%.1f", raw)).replace("{type}", xwName);
        p.sendMessage(colorize(msg));
        
        int oldLevel = data.getLevel();
        while (data.getLevel() < 1300 && data.getXp() >= getXpForLevel(data.getLevel() + 1)) {
            data.setXp(data.getXp() - getXpForLevel(data.getLevel() + 1));
            data.setLevel(data.getLevel() + 1);
        }
        
        if (data.getLevel() > oldLevel) {
            String realmType = data.activeType.equals("lianti") ? "\u70BC\u4F53" : "\u6CD5\u4FEE";
            String newRealm = getRealm(data.getLevel(), data.activeType.equals("lianti"));
            if ((data.getLevel() / chongLevels) > (oldLevel / chongLevels)) {
                p.sendMessage(LA + "\u2605 " + W + realmType + "\u7A81\u7834\u81F3 " + newRealm + LA + " \u2605");
            }
            if ((data.getLevel() / realmLevels) > (oldLevel / realmLevels)) {
                Bukkit.broadcastMessage(colorize(breakthroughMessage.replace("{player}", n)));
                p.sendMessage(LA + "\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550");
                p.sendMessage(LA + "\u2605 " + W + realmType + "\u5927\u5883\u754C\u7A81\u7834\uFF01" + LA + " \u2605");
                p.sendMessage(LA + "\u5F53\u524D\u5883\u754C: " + W + newRealm);
                p.sendMessage(LA + "\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550");
            }
        }
        
    }

    // ========== 真元系统 ==========
    



    
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player p = event.getPlayer();
        PlayerData data = getOrCreateData(p.getUniqueId());
        // 属性由XiuXianCombat处理
    }
    
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        savePlayerData(uuid);
        playerData.remove(uuid);
        lct.remove(uuid);
    }

    // ========== 公开API（供其他插件调用） ==========
    
    /**
     * 给玩家添加修为（供其他插件调用）
     * @param uuid 玩家UUID
     * @param amount 修为数量
     * @param isLianti true=体修, false=法修
     */
    public void addXp(UUID uuid, double amount, boolean isLianti) {
        PlayerData data = playerData.get(uuid);
        if (data == null) return;
        
        if (isLianti) {
            data.liantiXp += amount;
        } else {
            data.xiufaXp += amount;
        }
    }
    
    /**
     * 检查并执行升级（供其他插件调用）
     */
    public void checkLevelUp(UUID uuid) {
        PlayerData data = playerData.get(uuid);
        if (data == null) return;
        
        int oldLevel = data.getLevel();
        while (data.getLevel() < 1300 && data.getXp() >= getXpForLevel(data.getLevel() + 1)) {
            data.setXp(data.getXp() - getXpForLevel(data.getLevel() + 1));
            data.setLevel(data.getLevel() + 1);
        }
        
        if (data.getLevel() > oldLevel) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) {
                String realmType = data.activeType.equals("lianti") ? "\u70BC\u4F53" : "\u6CD5\u4FEE";
                String newRealm = getRealm(data.getLevel(), data.activeType.equals("lianti"));
                p.sendMessage(LA + "\u2605 " + W + realmType + "\u7A81\u7834\u81F3 " + newRealm + LA + " \u2605");
            }
        }
        

    }
    
    // ========== 工具方法 ==========
    
    String getRealm(int level, boolean isLianti) {
        if (level <= 0) return zeroLevelName;
        List<String> realms = isLianti ? ltRealms : xfRealms;
        int idx = (level - 1) / realmLevels;
        if (idx < 0 || idx >= realms.size()) return "";
        int lir = level - idx * realmLevels;
        if (lir < 1) return realms.get(idx);
        if (lir == realmLevels) return realms.get(idx) + maxLevelSuffix;
        int ci = (lir - 1) / chongLevels, ji = (lir - 1) % chongLevels;
        if (ji == chongLevels - 1) return realms.get(idx) + "\u00B7" + CN[ci] + "\u91CD\u00B7\u5341\u7EA7";
        return realms.get(idx) + "\u00B7" + CN[ci] + "\u91CD\u00B7" + CN[ji] + "\u7EA7";
    }

    double getXpForLevel(int level) { return xpBase * Math.pow(xpRate, level); }

    private PlayerData getOrCreateData(UUID uuid) { return playerData.computeIfAbsent(uuid, this::loadPlayerData); }
    
    private PlayerData loadPlayerData(UUID uuid) {
        File file = new File(dataDir, uuid.toString() + ".json");
        if (file.exists()) {
            try {
                JsonObject json = JsonParser.parseReader(new FileReader(file)).getAsJsonObject();
                return new PlayerData(
                    json.has("activeType") ? json.get("activeType").getAsString() : "lianti",
                    json.has("liantiLevel") ? json.get("liantiLevel").getAsInt() : 0,
                    json.has("liantiXp") ? json.get("liantiXp").getAsDouble() : 0,
                    json.has("xiufaLevel") ? json.get("xiufaLevel").getAsInt() : 0,
                    json.has("xiufaXp") ? json.get("xiufaXp").getAsDouble() : 0,
                    json.has("mana") ? json.get("mana").getAsDouble() : 0
                );
            } catch (Exception e) { getLogger().warning("Load failed: " + uuid); }
        }
        return new PlayerData("lianti", 0, 0, 0, 0, 0);
    }
    
    private void savePlayerData(UUID uuid) {
        PlayerData data = playerData.get(uuid);
        if (data == null) return;
        try {
            JsonObject json = new JsonObject();
            json.addProperty("activeType", data.activeType);
            json.addProperty("liantiLevel", data.liantiLevel);
            json.addProperty("liantiXp", data.liantiXp);
            json.addProperty("xiufaLevel", data.xiufaLevel);
            json.addProperty("xiufaXp", data.xiufaXp);
            json.addProperty("mana", data.mana);
            FileWriter writer = new FileWriter(new File(dataDir, uuid.toString() + ".json"));
            writer.write(json.toString());
            writer.close();
        } catch (Exception e) { getLogger().warning("Save failed: " + uuid); }
    }

    private long getIl(Player p) {
        if (islandsManager == null || ow == null || getIslandMethod == null || levelDbDir == null) return 0;
        try {
            Object island = getIslandMethod.invoke(islandsManager, ow, p.getUniqueId());
            if (island == null) return 0;
            String uid = (String) island.getClass().getMethod("getUniqueId").invoke(island);
            File f = new File(levelDbDir, uid + ".json");
            if (!f.exists()) return 0;
            String json = new String(Files.readAllBytes(f.toPath()));
            int i = json.indexOf("\"level\""); if (i < 0) return 0;
            int c = json.indexOf(':', i), cm = json.indexOf(',', c), br = json.indexOf('}', c);
            int e = (cm > 0 && cm < br) ? cm : br; if (e < 0) return 0;
            return Long.parseLong(json.substring(c + 1, e).trim());
        } catch (Exception e) { return 0; }
    }

    private boolean isOnOwnIsland(Player p) {
        if (islandsManager == null || ow == null || getIslandMethod == null) return false;
        if (!p.getWorld().equals(ow)) return false;
        try {
            Object island = getIslandMethod.invoke(islandsManager, ow, p.getUniqueId());
            if (island == null) return false;
            Location center = (Location) island.getClass().getMethod("getCenter").invoke(island);
            if (center == null) return false;
            Location playerLoc = p.getLocation();
            if (center.getWorld() != null && center.getWorld().equals(playerLoc.getWorld()))
                return center.distance(playerLoc) <= islandRange;
        } catch (Exception e) {}
        return false;
    }

    @EventHandler
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        if (silentPlayers.contains(event.getPlayer().getUniqueId())) event.setCancelled(true);
    }

    private void silentPerformCommand(Player p, String command) {
        UUID uuid = p.getUniqueId();
        silentPlayers.add(uuid);
        try { p.performCommand(command); } finally { silentPlayers.remove(uuid); }
    }

    private double evaluateFormula(double base, long level, double coeff) {
        try {
            String expr = formula.replace("{base}", String.valueOf(base)).replace("{level}", String.valueOf(level)).replace("{coeff}", String.valueOf(coeff));
            return new ExpressionParser(expr).parse();
        } catch (Exception e) { return base + level * coeff; }
    }

    private static class ExpressionParser {
        private final String expr; private int pos = -1, ch;
        ExpressionParser(String expr) { this.expr = expr.replaceAll("\\s+", ""); }
        void nextChar() { ch = (++pos < expr.length()) ? expr.charAt(pos) : -1; }
        boolean eat(int c) { while (ch == ' ') nextChar(); if (ch == c) { nextChar(); return true; } return false; }
        double parse() { nextChar(); double x = parseExpr(); if (pos < expr.length()) throw new RuntimeException("Unexpected"); return x; }
        double parseExpr() { double x = parseTerm(); for (;;) { if (eat('+')) x += parseTerm(); else if (eat('-')) x -= parseTerm(); else return x; } }
        double parseTerm() { double x = parseFactor(); for (;;) { if (eat('*')) x *= parseFactor(); else if (eat('/')) x /= parseFactor(); else return x; } }
        double parseFactor() {
            if (eat('+')) return parseFactor(); if (eat('-')) return -parseFactor();
            double x; int s = this.pos;
            if (eat('(')) { x = parseExpr(); eat(')'); }
            else if ((ch >= '0' && ch <= '9') || ch == '.') { while ((ch >= '0' && ch <= '9') || ch == '.') nextChar(); x = Double.parseDouble(expr.substring(s, this.pos)); }
            else if (ch >= 'a' && ch <= 'z') { while (ch >= 'a' && ch <= 'z') nextChar(); String f = expr.substring(s, this.pos); if (eat('(')) { x = parseExpr(); eat(')'); } else x = parseFactor(); switch (f) { case "sqrt": x = Math.sqrt(x); break; case "abs": x = Math.abs(x); break; case "floor": x = Math.floor(x); break; case "ceil": x = Math.ceil(x); break; case "round": x = Math.round(x); break; default: throw new RuntimeException("Unknown: " + f); } }
            else throw new RuntimeException("Unexpected: " + (char) ch);
            if (eat('^')) x = Math.pow(x, parseFactor()); return x;
        }
    }

    private String colorize(String msg) {
        return msg.replace("&0", ChatColor.BLACK.toString()).replace("&1", ChatColor.DARK_BLUE.toString())
                  .replace("&2", ChatColor.DARK_GREEN.toString()).replace("&3", ChatColor.DARK_AQUA.toString())
                  .replace("&4", ChatColor.DARK_RED.toString()).replace("&5", ChatColor.DARK_PURPLE.toString())
                  .replace("&6", ChatColor.GOLD.toString()).replace("&7", ChatColor.GRAY.toString())
                  .replace("&8", ChatColor.DARK_GRAY.toString()).replace("&9", ChatColor.BLUE.toString())
                  .replace("&a", ChatColor.GREEN.toString()).replace("&b", ChatColor.AQUA.toString())
                  .replace("&c", ChatColor.RED.toString()).replace("&d", ChatColor.LIGHT_PURPLE.toString())
                  .replace("&e", ChatColor.YELLOW.toString()).replace("&f", ChatColor.WHITE.toString());
    }
}

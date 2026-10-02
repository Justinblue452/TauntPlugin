package com.example.tauntplugin;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.destroystokyo.paper.profile.ProfileProperty;
import org.bukkit.plugin.java.JavaPlugin;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

/**
 * 通过 Mojang 官方 API 获取正版玩家的皮肤纹理值。
 *
 * API 端点: https://sessionserver.mojang.com/session/minecraft/profile/{uuid}?unsigned=false
 */
public class SkinFetcher {

    private final JavaPlugin plugin;
    private final HttpClient httpClient;

    // 缓存：ProfileProperty(textures)
    private volatile ProfileProperty cachedSkin;

    public SkinFetcher(JavaPlugin plugin) {
        this.plugin = plugin;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    /**
     * 异步获取指定 UUID 正版玩家的皮肤纹理。
     * 结果会自动缓存到 cachedSkin。
     *
     * @param uuid 玩家 UUID（带或不带连字符均可）
     * @return CompletableFuture，完成时返回 ProfileProperty，失败时返回 empty
     */
    public CompletableFuture<Optional<ProfileProperty>> fetchSkin(String uuid) {
        String cleanUuid = uuid.replace("-", "");
        String url = "https://sessionserver.mojang.com/session/minecraft/profile/"
                + cleanUuid + "?unsigned=false";

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(15))
                .header("User-Agent", "TauntPlugin/1.0")
                .GET()
                .build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
                .thenApply(response -> {
                    if (response.statusCode() != 200) {
                        plugin.getLogger().warning("获取皮肤失败，HTTP 状态码: " + response.statusCode());
                        return Optional.<ProfileProperty>empty();
                    }
                    return parseSkinProperty(response.body());
                })
                .thenApply(opt -> {
                    opt.ifPresent(property -> {
                        cachedSkin = property;
                        plugin.getLogger().info("✅ 成功获取并缓存皮肤纹理");
                    });
                    return opt;
                })
                .exceptionally(ex -> {
                    plugin.getLogger().log(Level.WARNING, "获取皮肤时发生异常", ex);
                    return Optional.empty();
                });
    }

    /**
     * 解析 Mojang API 返回的 JSON，提取 textures 属性。
     */
    private Optional<ProfileProperty> parseSkinProperty(String json) {
        try {
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();

            if (!root.has("properties")) {
                plugin.getLogger().warning("返回数据中没有 properties 字段");
                return Optional.empty();
            }

            JsonArray properties = root.getAsJsonArray("properties");
            for (int i = 0; i < properties.size(); i++) {
                JsonObject prop = properties.get(i).getAsJsonObject();
                String name = prop.get("name").getAsString();
                if ("textures".equals(name)) {
                    String value = prop.get("value").getAsString();
                    String signature = prop.get("signature").getAsString();
                    return Optional.of(new ProfileProperty("textures", value, signature));
                }
            }

            plugin.getLogger().warning("properties 中没有找到 textures 字段");
            return Optional.empty();
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "解析皮肤 JSON 失败", e);
            return Optional.empty();
        }
    }

    /**
     * 获取已缓存的皮肤。可能为 null（尚未获取成功）。
     */
    public ProfileProperty getCachedSkin() {
        return cachedSkin;
    }

    /**
     * 是否已经成功获取过皮肤。
     */
    public boolean hasCachedSkin() {
        return cachedSkin != null;
    }
}
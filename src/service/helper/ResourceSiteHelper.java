package service.helper;

import arc.files.Fi;
import arc.util.Log;
import mindustry.io.MapIO;
import mindustry.maps.Map;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import static mindustry.Vars.*;

/**
 * <a href="https://github.com/MinRi2/WayzerMapBrowser">MinRi2's wz resource site mod</a>
 */
public class ResourceSiteHelper {
    public static final String webRoot = "https://api.mindustry.top";
    public static final String[] MULTIPLIER_RULES = {"solarMultiplier", "blockHealthMultiplier", "blockDamageMultiplier", "buildCostMultiplier", "deconstructRefundMultiplier", "unitHealthMultiplier", "unitBuildSpeedMultiplier"};
    public static final String[] BOOLEAN_RULES = {"attackMode", "coreIncinerates", "waveTimer"};
    public static final String[] MODE_TAGS = {"Survive", "Pvp", "Attack", "Sandbox", "Editor", "Unknown"};
    public static final String[] SORT_TAGS = {"updateTime", "createTime", "download", "rating", "like"};
    public static final int[] VERSION_TAGS = {3, 4, 5, 7, 8};

    public static final Fi wayzerMapFolder = dataDirectory.child("wayzer");

    public static CompletableFuture<Map> mapFromResourceSiteAsync(int id) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                URL url = new URL(webRoot + "/maps/" + id + ".msav");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(3000);
                conn.setReadTimeout(5000);

                int code = conn.getResponseCode();
                if (code != HttpURLConnection.HTTP_OK) {
                    throw new IOException("下载失败：HTTP " + code);
                }

                byte[] data;
                try (InputStream input = conn.getInputStream();
                     ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                    byte[] buffer = new byte[4096];
                    int len;
                    while ((len = input.read(buffer)) != -1) {
                        output.write(buffer, 0, len);
                    }
                    data = output.toByteArray();
                }

                Fi fi = wayzerMapFolder.child("map-" + id + ".msav");
                fi.writeBytes(data, false);

                return MapIO.createMap(fi, true);
            } catch (IOException e) {
                throw new CompletionException(e);
            }
        });
    }

    public static CompletableFuture<String> mapInfoFromResourceSiteAsync(int id) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                URL url = new URL(webRoot + "/maps/thread/" + id + "/latest");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(3000);
                conn.setReadTimeout(5000);

                if (conn.getResponseCode() != HttpURLConnection.HTTP_OK) {
                    throw new IOException("HTTP " + conn.getResponseCode());
                }

                try (InputStream input = conn.getInputStream(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                    byte[] buffer = new byte[4096];
                    int len;
                    while ((len = input.read(buffer)) != -1) {
                        output.write(buffer, 0, len);
                    }
                    return output.toString();
                }

            } catch (IOException e) {
                throw new CompletionException(e);
            }
        });
    }
}

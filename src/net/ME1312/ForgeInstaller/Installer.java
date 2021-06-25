package net.ME1312.ForgeInstaller;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.StandardCopyOption;
import java.util.Set;
import java.util.jar.Manifest;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public class Installer {
    public static void main(String[] args) {
        if (args.length != 1) {
            System.out.println("Args: <installer>");
            return;
        }

        System.out.println("Minecraft Forge Installer Installer 1.0");

        File file = new File(args[0]);
        File tmp = new File(args[0] + ".tmp");
        if (!file.exists()) {
            System.out.println("Could not find \"" + args[0] + '\"');
            System.exit(1);
        } else try (
                ZipInputStream zip = new ZipInputStream(new FileInputStream(file));
                ZipOutputStream zop = new ZipOutputStream(new FileOutputStream(tmp, false));
        ) {
            System.out.println("Opened " + file.getName());
            System.out.println();
            for (ZipEntry entry; (entry = zip.getNextEntry()) != null; ) {
                String path = entry.getName();

                if (path.equals("META-INF/MANIFEST.MF")) {
                    System.out.println("Cleaning MANIFEST.MF ...");
                    System.out.println();
                    StringBuilder edited = new StringBuilder();
                    String[] original = readAll(new InputStreamReader(zip)).replace("\r", "").replace("\n ", "").split("\n");
                    Pattern pattern = Pattern.compile("^(Manifest-Version|Main-Class):\\s*(.*)$");
                    for (String property : original) {
                        Matcher m = pattern.matcher(property);
                        if (m.find()) {
                            edited.append(m.group(1));
                            edited.append(": ");
                            edited.append(m.group(2));
                            edited.append('\n');
                        }
                    }
                    zop.putNextEntry(new ZipEntry(path));
                    zop.write(edited.toString().getBytes(StandardCharsets.UTF_8));
                } else if (path.startsWith("META-INF/") && path.endsWith(".SF")) {
                    System.out.println("Removing " + path.substring(9) + " ...");
                    System.out.println();
                } else if (path.indexOf('/') == -1 && path.endsWith(".json")) {
                    System.out.println("Updating " + path + " ...");
                    JSONObject json = (JSONObject) convert(new JSONObject(readAll(new InputStreamReader(zip))));
                    System.out.println();
                    zop.putNextEntry(new ZipEntry(path));
                    zop.write(json.toString(4).getBytes(StandardCharsets.UTF_8));
                } else {
                    zop.putNextEntry(entry);
                    byte[] b = new byte[4096];
                    for (int i; (i = zip.read(b)) != -1; ) {
                        zop.write(b, 0, i);
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
            System.exit(2);
        }

        try {
            Files.move(tmp.toPath(), file.toPath(), LinkOption.NOFOLLOW_LINKS, StandardCopyOption.REPLACE_EXISTING);
            System.out.println("Minecraft Forge is ready to install");
            System.out.println();
        } catch (IOException e) {
            e.printStackTrace();
            System.exit(2);
        }
    }

    private static Object convert(Object obj) throws IOException {
        if (obj instanceof JSONObject) {
            Set<String> keys = ((JSONObject) obj).keySet();
            boolean isLibrary = false;
            for (String key : keys) {
                Object name;
                Object value = ((JSONObject) obj).get(key);
                if (key.equals("url") && keys.contains("name") && value instanceof String && (name = ((JSONObject) obj).get("name")) instanceof String && isURL((String) value) && isMaven((String) name)) {
                    String unresolved = (String) value;
                    if (!unresolved.endsWith("/")) unresolved += "/";

                    String path = deMaven((String) name);
                    unresolved += path;
                    isLibrary = true;

                    System.out.println();
                    String resolved = resolve(unresolved);
                    ((JSONObject) obj).put("url", resolved.substring(0, resolved.length() - path.length()));
                } else {
                    if (key.equals("url") && keys.contains("path") && value instanceof String && ((JSONObject) obj).get("path") instanceof String && isURL((String) value)) {
                        isLibrary = true;
                    }
                    ((JSONObject) obj).put(key, convert(value));
                }
            }

            if (isLibrary) {
                if (keys.contains("checksums") && ((JSONObject) obj).get("checksums") instanceof JSONArray) {
                    ((JSONObject) obj).remove("checksums");
                }
                if (keys.contains("sha1") && ((JSONObject) obj).get("sha1") instanceof String) {
                    ((JSONObject) obj).remove("sha1");
                }
                if (keys.contains("size") && ((JSONObject) obj).get("size") instanceof Number) {
                    ((JSONObject) obj).remove("size");
                }
            }
        } else if (obj instanceof JSONArray) {
            for (int i = 0; i < ((JSONArray) obj).length(); ++i) {
                ((JSONArray) obj).put(i, convert(((JSONArray) obj).get(i)));
            }
        } else if (obj instanceof String) {
            if (isURL((String) obj)) {
                System.out.println();
                return resolve((String) obj);
            }
        }
        return obj;
    }

    private static String resolve(String url) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setRequestMethod("HEAD");
        conn.setInstanceFollowRedirects(false);
        conn.setReadTimeout(30000);

        int status = conn.getResponseCode();
        System.out.println("[" + status + "] " + url);

        String redirect = null;
        switch (status) {
            case 301: // Moved Permanently
            case 302: // Found
            case 303: // See Other
            case 307: // Temporary Redirect
            case 308: // Permanent Redirect
                redirect = conn.getHeaderField("Location");
        }

        conn.disconnect();
        if (redirect != null) {
            return resolve(redirect);
        } else {
            return url;
        }
    }

    private static String deMaven(String descriptor) {
        String[] parts = descriptor.split(":");
        String domain = parts[0];
        String name = parts[1];
        String ext;
        int last = parts.length - 1;
        int idx = parts[last].indexOf('@');
        if (idx == -1) {
            ext = "jar";
        } else {
            ext = parts[last].substring(idx + 1);
            parts[last] = parts[last].substring(0, idx);
        }
        String version = parts[2];
        String classifier = (parts.length > 3)? parts[3] : null;
        String file = name + '-' + version;
        if (classifier != null)
            file += '-' + classifier;
        file += '.' + ext;
        return domain.replace('.', '/') + '/' + name + '/' + version + '/' + file;
    }

    private static boolean isMaven(String s) {
        int count = 0;
        for (int i = 0; (i = s.indexOf(':', i)) != -1; ++i, ++count);
        return count >= 2;
    }

    private static boolean isURL(String s) {
        return s.startsWith("http://") || s.startsWith("https://");
    }

    private static String readAll(Reader rd) throws IOException {
        StringBuilder sb = new StringBuilder();
        int cp;
        while ((cp = rd.read()) != -1) {
            sb.append((char) cp);
        }
        return sb.toString();
    }
}

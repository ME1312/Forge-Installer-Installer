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
import java.util.HashMap;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public class Installer {
    private static final HashMap<String, ResolvedURL> resolved = new HashMap<String, ResolvedURL>();
    private static final HashMap<String, String> checksums = new HashMap<String, String>();

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
                    zop.putNextEntry(new ZipEntry(path));
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
            ResolvedURL library = null;
            for (String key : keys) {
                Object name;
                Object value = ((JSONObject) obj).get(key);
                if (key.equals("url") && keys.contains("name") && value instanceof String && (name = ((JSONObject) obj).get("name")) instanceof String && isURL((String) value) && isMaven((String) name)) {
                    String unresolved = (String) value;
                    if (!unresolved.endsWith("/")) unresolved += "/";

                    String path = deMaven((String) name);
                    unresolved += path;

                    System.out.println();
                    library = resolve(unresolved);
                    ((JSONObject) obj).put(key, library.url.substring(0, library.url.length() - path.length()));
                } else if (key.equals("url") && keys.contains("path") && value instanceof String && ((JSONObject) obj).get("path") instanceof String && isURL((String) value)) {
                    System.out.println();
                    library = resolve((String) value);
                    ((JSONObject) obj).put(key, library.url);
                } else {
                    ((JSONObject) obj).put(key, convert(value));
                }
            }

            if (library != null) {
                if (keys.contains("sha1") && ((JSONObject) obj).get("sha1") instanceof String) {
                    final String url = library.url + ".sha1";
                    if (!checksums.containsKey(url)) {
                        try (InputStream sha1 = (library.isOK())? download(url) : null) {
                            if (sha1 != null) {
                                checksums.put(url, readAll(new InputStreamReader(sha1)));
                            }
                        }
                    }

                    if (checksums.containsKey(url)) {
                        ((JSONObject) obj).put("sha1", checksums.get(url));
                    } else {
                        ((JSONObject) obj).remove("sha1");
                    }
                }
                if (keys.contains("size") && ((JSONObject) obj).get("size") instanceof Number) {
                    if (library.size != -1) {
                        ((JSONObject) obj).put("size", library.size);
                    } else {
                        ((JSONObject) obj).remove("size");
                    }
                }
                if (keys.contains("checksums") && ((JSONObject) obj).get("checksums") instanceof JSONArray) {
                    ((JSONObject) obj).remove("checksums");
                }
            }
        } else if (obj instanceof JSONArray) {
            for (int i = 0; i < ((JSONArray) obj).length(); ++i) {
                ((JSONArray) obj).put(i, convert(((JSONArray) obj).get(i)));
            }
        } else if (obj instanceof String) {
            if (isURL((String) obj)) {
                System.out.println();
                return resolve((String) obj).url;
            }
        }
        return obj;
    }

    private static ResolvedURL resolve(String url) throws IOException {
        if (!resolved.containsKey(url)) {
            HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setRequestMethod("HEAD");
            conn.setInstanceFollowRedirects(false);
            conn.setReadTimeout(Integer.getInteger("mcfii.timeout", 30000));

            final int status = conn.getResponseCode();
            final long size = conn.getContentLengthLong();
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
                if (redirect.startsWith("/")) {
                    int index = url.indexOf('/', 8);
                    redirect = url.substring(0, (index == -1)? url.length() : index) + redirect;
                }
                resolved.put(url, resolve(redirect));
            } else {
                resolved.put(url, new ResolvedURL(url, status, size));
            }
        }

        return resolved.get(url);
    }

    private static InputStream download(String url) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setRequestMethod("GET");
        conn.setInstanceFollowRedirects(true);
        conn.setReadTimeout(Integer.getInteger("mcfii.timeout", 30000));

        final int status = conn.getResponseCode();
        if (status == 200 || status == 203) {
            System.out.println("[GET] " + url);
            InputStream stream = conn.getInputStream();
            return new InputStream() {
                @Override
                public int read() throws IOException {
                    return stream.read();
                }

                @Override
                public int read(byte[] b, int off, int len) throws IOException {
                    return stream.read(b, off, len);
                }

                @Override
                public void close() throws IOException {
                    stream.close();
                    conn.disconnect();
                }
            };
        } else {
            return null;
        }
    }

    private static String deMaven(String s) {
        String[] parts = s.split(":");
        String domain = parts[0];
        String name = parts[1];
        String ext;
        int last = parts.length - 1;
        int index = parts[last].indexOf('@');
        if (index == -1) {
            ext = "jar";
        } else {
            ext = parts[last].substring(index + 1);
            parts[last] = parts[last].substring(0, index);
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

package net.ME1312.ForgeInstaller;

public class ResolvedURL {
    public final String url;
    public final int status;
    public final long size;

    ResolvedURL(String url, int status, long size) {
        this.url = url;
        this.status = status;
        this.size = size;
    }
}

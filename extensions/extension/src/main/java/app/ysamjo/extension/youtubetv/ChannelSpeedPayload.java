package app.ysamjo.extension.youtubetv;

/**
 * Per-channel playback-speed overrides injected into the Cobalt web app.
 *
 * <p>YouTube TV runs as a leanback web app inside the Cobalt engine. When a user opens a
 * channel, the player inherits the global speed setting set by TizenTube's speed mod
 * ({@code videoSpeed} via {@code setClientSettingEndpoint}). Some channels — notably live
 * sports streams from sportstudio, Sky Sport, Sky Sport DE, DAZN, DAZN DE, RTL Sport, NBA and
 * NFL — must always play at normal speed regardless of any global override. This class injects
 * a JavaScript snippet that reads the current channel from the URL and enforces the
 * per-channel speed before the player starts.
 *
 * <p>Detection is by URL path ({@code /channel/<id>}) which is stable across app versions.
 * If the {@code setClientSettingEndpoint} bridge is not present (TizenTube speed mod not
 * installed), the snippet degrades silently without affecting playback.
 */
public final class ChannelSpeedPayload {

    private ChannelSpeedPayload() {
    }

    // ---------------------------------------------------------------------------
    // Channel → speed rules, synchron mit den Standardregeln der Chrome-Erweiterung.
    // Speed values must be bare Java numbers (no quotes).
    // ---------------------------------------------------------------------------

    private static final String SPORTSTUDIO_CHANNEL_ID = "UCE0RhriywrL8XhXbo87tMKQ";
    private static final String SKY_SPORT_CHANNEL_ID = "UCAvNr5DMVncoJnJAotXOjOQ";
    private static final String SKY_SPORT_DE_CHANNEL_ID = "UCuOwl-VgqBHOzR8hWnonBcA";
    private static final String DAZN_CHANNEL_ID = "UCdi3n18zcIZeTk_x9qvSgZw";
    private static final String DAZN_DE_CHANNEL_ID = "UC-XArLCeEw91r9gmaDi-Ajw";
    private static final String RTL_SPORT_CHANNEL_ID = "UC8WYi3XQXsf-6FNvqoEvxag";
    private static final String NBA_CHANNEL_ID = "UCWJ2lWNubArHWmf3FIHbfcQ";
    private static final String NFL_CHANNEL_ID = "UCDVYQ4Zhbm3S2dlz7P1GBDg";

    private static final float SPORTSTUDIO_SPEED = 1.0f;
    private static final float SKY_SPORT_SPEED = 1.0f;
    private static final float SKY_SPORT_DE_SPEED = 1.0f;
    private static final float DAZN_SPEED = 1.0f;
    private static final float DAZN_DE_SPEED = 1.0f;
    private static final float RTL_SPORT_SPEED = 1.0f;
    private static final float NBA_SPEED = 1.0f;
    private static final float NFL_SPEED = 1.0f;

    /**
     * Injected script that reads the current channel from the URL and calls the speed bridge.
     * Degrades silently if the bridge is not present (TizenTube speed mod not installed).
     *
     * <p>The script is guarded by the callers' own flag so re-injection in the same document
     * is always a no-op.
     */
    public static final String SCRIPT =
            "var __yscs=window.__ysamjoChannelSpeed||(function(){"
            + "var r={};"
            + "r['" + SPORTSTUDIO_CHANNEL_ID + "']=" + SPORTSTUDIO_SPEED + ";"
            + "r['" + SKY_SPORT_CHANNEL_ID + "']=" + SKY_SPORT_SPEED + ";"
            + "r['" + SKY_SPORT_DE_CHANNEL_ID + "']=" + SKY_SPORT_DE_SPEED + ";"
            + "r['" + DAZN_CHANNEL_ID + "']=" + DAZN_SPEED + ";"
            + "r['" + DAZN_DE_CHANNEL_ID + "']=" + DAZN_DE_SPEED + ";"
            + "r['" + RTL_SPORT_CHANNEL_ID + "']=" + RTL_SPORT_SPEED + ";"
            + "r['" + NBA_CHANNEL_ID + "']=" + NBA_SPEED + ";"
            + "r['" + NFL_CHANNEL_ID + "']=" + NFL_SPEED + ";"
            + "window.__ysamjoChannelSpeed=r;"
            + "var m=location.pathname.match(/\\/channel\\/([^/]+)/);"
            + "if(!m) return;"
            + "var id=m[1],s=r[id];"
            + "if(typeof s==='number'){"
            + "if(typeof setClientSettingEndpoint==='function'){"
            + "setClientSettingEndpoint('videoSpeed',s);"
            + "}else if(window.configWrite&&typeof configWrite==='function'){"
            + "configWrite('videoSpeed',s);"
            + "}"
            + "}"
            + "})();";
}

package app.ysamjo.extension.youtubetv;

/**
 * Per-channel playback-speed overrides injected into the Cobalt web app.
 *
 * <p>YouTube TV runs as a leanback web app inside the Cobalt engine. When a user opens a
 * channel, the player inherits the global speed setting set by TizenTube's speed mod
 * ({@code videoSpeed} via {@code setClientSettingEndpoint}). Some channels — notably live
 * sports streams from sportstudio, Sportschau, Sky Sport, DAZN, RTL Sport, Prime Video Sport,
 * Eurosport, SPORT1, ran, kicker, Magenta Sport, NFL, NBA, MotoGP, Formula 1, Bundesliga, DFB
 * and UEFA — must always play at normal speed regardless of any global override. This class
 * injects a JavaScript snippet that reads the current channel from the URL and enforces the
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
    private static final String SPORTSCHAU_CHANNEL_ID = "UCRFsyeKu07-LnHDG44O6uCA";
    private static final String SKY_SPORT_CHANNEL_ID = "UCAvNr5DMVncoJnJAotXOjOQ";
    private static final String SKY_SPORT_DE_CHANNEL_ID = "UCuOwl-VgqBHOzR8hWnonBcA";
    private static final String SKY_SPORT_AUSTRIA_CHANNEL_ID = "UC5dCegXiWjE57wKrhWnCo0g";
    private static final String SKY_SPORT_NEWS_CHANNEL_ID = "UC1a3OGagvVFWPK1NAI0PcMQ";
    private static final String DAZN_CHANNEL_ID = "UCdi3n18zcIZeTk_x9qvSgZw";
    private static final String DAZN_DE_CHANNEL_ID = "UC-XArLCeEw91r9gmaDi-Ajw";
    private static final String RTL_SPORT_CHANNEL_ID = "UC8WYi3XQXsf-6FNvqoEvxag";
    private static final String PRIME_VIDEO_SPORT_CHANNEL_ID = "UCK2izXoHvraUFaPMU5B7vMQ";
    private static final String EUROSPORT_DE_CHANNEL_ID = "UCk452F_ypT9Q8b1S38-Ok8A";
    private static final String SPORT1_CHANNEL_ID = "UCPj_Vtw0hKnhJapQuATBUrA";
    private static final String RAN_CHANNEL_ID = "UCiMAc_TN1kSO3ErMtxUXfMA";
    private static final String KICKER_CHANNEL_ID = "UCQEHrmmI8kKJ6kAiQdQUjgg";
    private static final String MAGENTA_SPORT_CHANNEL_ID = "UChr7ZXDFiPOEl543HM4Aj8g";
    private static final String NFL_CHANNEL_ID = "UCDVYQ4Zhbm3S2dlz7P1GBDg";
    private static final String NBA_CHANNEL_ID = "UCWJ2lWNubArHWmf3FIHbfcQ";
    private static final String MOTOGP_CHANNEL_ID = "UC8pYaQzbBBXg9GIOHRvTmDQ";
    private static final String FORMULA1_CHANNEL_ID = "UCB_qr75-ydFVKSF9Dmo6izg";
    private static final String BUNDESLIGA_CHANNEL_ID = "UC6UL29enLNe4mqwTfAyeNuw";
    private static final String DFB_CHANNEL_ID = "UCfMo0xj-sbdzHuzxvKdu1hw";
    private static final String UEFA_CHANNEL_ID = "UCyGa1YEx9ST66rYrJTGIKOw";

    private static final float SPORTSTUDIO_SPEED = 1.0f;
    private static final float SPORTSCHAU_SPEED = 1.0f;
    private static final float SKY_SPORT_SPEED = 1.0f;
    private static final float SKY_SPORT_DE_SPEED = 1.0f;
    private static final float SKY_SPORT_AUSTRIA_SPEED = 1.0f;
    private static final float SKY_SPORT_NEWS_SPEED = 1.0f;
    private static final float DAZN_SPEED = 1.0f;
    private static final float DAZN_DE_SPEED = 1.0f;
    private static final float RTL_SPORT_SPEED = 1.0f;
    private static final float PRIME_VIDEO_SPORT_SPEED = 1.0f;
    private static final float EUROSPORT_DE_SPEED = 1.0f;
    private static final float SPORT1_SPEED = 1.0f;
    private static final float RAN_SPEED = 1.0f;
    private static final float KICKER_SPEED = 1.0f;
    private static final float MAGENTA_SPORT_SPEED = 1.0f;
    private static final float NFL_SPEED = 1.0f;
    private static final float NBA_SPEED = 1.0f;
    private static final float MOTOGP_SPEED = 1.0f;
    private static final float FORMULA1_SPEED = 1.0f;
    private static final float BUNDESLIGA_SPEED = 1.0f;
    private static final float DFB_SPEED = 1.0f;
    private static final float UEFA_SPEED = 1.0f;

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
            + "r['" + SPORTSCHAU_CHANNEL_ID + "']=" + SPORTSCHAU_SPEED + ";"
            + "r['" + SKY_SPORT_CHANNEL_ID + "']=" + SKY_SPORT_SPEED + ";"
            + "r['" + SKY_SPORT_DE_CHANNEL_ID + "']=" + SKY_SPORT_DE_SPEED + ";"
            + "r['" + SKY_SPORT_AUSTRIA_CHANNEL_ID + "']=" + SKY_SPORT_AUSTRIA_SPEED + ";"
            + "r['" + SKY_SPORT_NEWS_CHANNEL_ID + "']=" + SKY_SPORT_NEWS_SPEED + ";"
            + "r['" + DAZN_CHANNEL_ID + "']=" + DAZN_SPEED + ";"
            + "r['" + DAZN_DE_CHANNEL_ID + "']=" + DAZN_DE_SPEED + ";"
            + "r['" + RTL_SPORT_CHANNEL_ID + "']=" + RTL_SPORT_SPEED + ";"
            + "r['" + PRIME_VIDEO_SPORT_CHANNEL_ID + "']=" + PRIME_VIDEO_SPORT_SPEED + ";"
            + "r['" + EUROSPORT_DE_CHANNEL_ID + "']=" + EUROSPORT_DE_SPEED + ";"
            + "r['" + SPORT1_CHANNEL_ID + "']=" + SPORT1_SPEED + ";"
            + "r['" + RAN_CHANNEL_ID + "']=" + RAN_SPEED + ";"
            + "r['" + KICKER_CHANNEL_ID + "']=" + KICKER_SPEED + ";"
            + "r['" + MAGENTA_SPORT_CHANNEL_ID + "']=" + MAGENTA_SPORT_SPEED + ";"
            + "r['" + NFL_CHANNEL_ID + "']=" + NFL_SPEED + ";"
            + "r['" + NBA_CHANNEL_ID + "']=" + NBA_SPEED + ";"
            + "r['" + MOTOGP_CHANNEL_ID + "']=" + MOTOGP_SPEED + ";"
            + "r['" + FORMULA1_CHANNEL_ID + "']=" + FORMULA1_SPEED + ";"
            + "r['" + BUNDESLIGA_CHANNEL_ID + "']=" + BUNDESLIGA_SPEED + ";"
            + "r['" + DFB_CHANNEL_ID + "']=" + DFB_SPEED + ";"
            + "r['" + UEFA_CHANNEL_ID + "']=" + UEFA_SPEED + ";"
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

package app.ysamjo.extension.youtubetv;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Arrays;

/**
 * Injects a userscript into the leanback web app that the Cobalt engine loads from
 * youtube.com/tv.
 *
 * <p>Why reflection instead of a direct call: the pieces we need are present in the shipped
 * APK, but their names are not all stable. Cobalt's own {@code dev.cobalt.coat.CobaltActivity}
 * is unobfuscated, while the {@code WebContentsImpl} field holding the native pointer is
 * R8-renamed and changes between app versions. Resolving everything at runtime by shape
 * (the only {@code long} field of the WebContents object, the {@code GEN_JNI} method whose
 * name ends in {@code _evaluateJavaScript}) keeps one patch build working across versions.
 */
public final class CobaltScriptInjector {

    private static final String TAG = "ysamjo-tv-adblock";

    private static final long POLL_INTERVAL_MS = 250L;

    /** Interval used once the first injection has gone through. */
    private static final long REINJECT_INTERVAL_MS = 2000L;

    /** 240 * 250 ms = 60 s of polling before giving up on the first injection. */
    private static final int MAX_POLL_ATTEMPTS = 240;

    /** 300 * 2 s = 10 min of re-injection after the first success. */
    private static final int MAX_REINJECT_ATTEMPTS = 300;

    /**
     * How many re-injections stay on the fast interval. 120 * 250 ms = 30 s, which comfortably
     * covers app start plus the first video's player request.
     */
    private static final int FAST_REINJECT_ATTEMPTS = 120;

    private static boolean scheduled;

    /** Number of successful injections so far; drives the one-off diagnostics. */
    private static int injectionCount;

    private CobaltScriptInjector() {
    }

    /**
     * Entry point called from the patched {@code CobaltActivity.onCreate}.
     *
     * <p>The WebContents handle does not exist yet when onCreate runs, so poll on the main
     * looper until the page is up.
     *
     * <p>Polling alone is not enough. The handle becomes valid long before the leanback app
     * has finished loading, so the first script lands in a document that the very next
     * navigation throws away. Cobalt's own log shows this clearly: the injection succeeds
     * roughly 300 ms before the first navigation commits. Injecting once and stopping
     * therefore looks successful while doing nothing.
     *
     * <p>So keep re-injecting on a slower interval afterwards. The payload guards itself with
     * a flag, which makes repeats in the same document no-ops; a fresh document has no flag
     * and gets the hooks again.
     */
    public static void schedule(final Activity activity) {
        if (activity == null || scheduled) {
            return;
        }
        scheduled = true;

        final Handler handler = new Handler(Looper.getMainLooper());

        handler.postDelayed(new Runnable() {
            private int pollAttempts;
            private int reinjectAttempts;
            private boolean firstSuccess;

            @Override
            public void run() {
                boolean injectedNow = false;
                try {
                    injectedNow = inject(activity);
                } catch (Throwable throwable) {
                    if (pollAttempts < 5) {
                        Log.w(TAG, "injection attempt failed", throwable);
                    }
                }

                if (injectedNow && !firstSuccess) {
                    firstSuccess = true;
                    Log.i(TAG, "userscript injected");
                } else if (injectedNow && reinjectAttempts < 10) {
                    Log.i(TAG, "re-injected (" + reinjectAttempts + ")");
                }

                if (!firstSuccess) {
                    if (++pollAttempts >= MAX_POLL_ATTEMPTS) {
                        Log.w(TAG, "gave up injecting the userscript after "
                                + MAX_POLL_ATTEMPTS + " attempts");
                        return;
                    }
                    handler.postDelayed(this, POLL_INTERVAL_MS);
                    return;
                }

                // Stay on the fast interval for a while after the first success. That first
                // success is almost always into a document the next navigation throws away,
                // so nothing is actually hooked yet. Measured on the Google TV Streamer: the
                // first injection lands ~300 ms before the navigation commits, and the app's
                // first ad beacon follows within two seconds — before a 2 s re-injection ever
                // gets its turn. The payload guards itself, so the extra calls are no-ops.
                ++reinjectAttempts;
                if (reinjectAttempts >= MAX_REINJECT_ATTEMPTS) {
                    return;
                }

                handler.postDelayed(this, reinjectAttempts <= FAST_REINJECT_ATTEMPTS
                        ? POLL_INTERVAL_MS
                        : REINJECT_INTERVAL_MS);
            }
        }, POLL_INTERVAL_MS);
    }

    private static boolean inject(final Activity activity) throws Exception {
        final Method getActiveWebContents =
                activity.getClass().getMethod("getActiveWebContents");
        final Object webContents = getActiveWebContents.invoke(activity);
        if (webContents == null) {
            return false;
        }

        final Field pointerField = findNativePointerField(webContents);
        if (pointerField == null) {
            return false;
        }

        final long pointer = pointerField.getLong(webContents);
        if (pointer == 0L) {
            return false;
        }

        final Method evaluateJavaScript = findEvaluateJavaScript();
        if (evaluateJavaScript == null) {
            return false;
        }

        // The first injection lands before the document exists, so run the diagnostics a few
        // attempts later, when the leanback app is actually loaded.
        if (++injectionCount == 4) {
            Log.i(TAG, "diag: webContents=" + webContents.getClass().getName()
                    + " field=" + pointerField.getName()
                    + " pointer=" + pointer
                    + " jni=" + evaluateJavaScript.getName()
                    + " returnType=" + evaluateJavaScript.getReturnType().getName());
            probeReadBack(pointer, evaluateJavaScript);
        }

        evaluateJavaScript.invoke(null, pointer, USERSCRIPT, null);
        return true;
    }

    /**
     * Class names the callback interface has lived under.
     *
     * <p>The stock app re-vendors Cobalt under {@code cobalt.org.chromium.*}, and R8 collapses
     * the interface down to a single no-argument method, so nothing can be read back from it.
     * TizenTube Cobalt keeps the plain {@code org.chromium.*} namespace and the original
     * {@code onEvaluateJavaScriptResult(String)}, so the probe returns a real value there.
     */
    private static final String[] CALLBACK_CLASS_NAMES = {
            "org.chromium.content_public.browser.JavaScriptCallback",
            "cobalt.org.chromium.content_public.browser.JavaScriptCallback",
    };

    /**
     * Evaluates a trivial expression with a real {@code JavaScriptCallback} attached.
     *
     * <p>This is the only way to tell "the script did not run" apart from "the script ran but
     * its console output is not forwarded to logcat" — an injected script's {@code console.log}
     * is demonstrably dropped on the stock app, while the callback proves execution from the
     * engine's own side.
     */
    private static void probeReadBack(final long pointer, final Method evaluateJavaScript) {
        for (final String className : CALLBACK_CLASS_NAMES) {
            try {
                probeReadBack(pointer, evaluateJavaScript, className);
                return;
            } catch (final Throwable throwable) {
                Log.i(TAG, "read-back probe: " + className + " not usable ("
                        + throwable.getClass().getSimpleName() + ")");
            }
        }

        Log.w(TAG, "read-back probe failed: no JavaScriptCallback interface was usable");
    }

    private static void probeReadBack(final long pointer, final Method evaluateJavaScript,
                                      final String className) throws Exception {
        final Class<?> callbackClass = Class.forName(className);

        for (final Method declared : callbackClass.getDeclaredMethods()) {
            Log.i(TAG, "callback " + callbackClass.getName() + " method: " + declared.getName()
                    + Arrays.toString(declared.getParameterTypes()));
        }

        final Object callback = Proxy.newProxyInstance(
                callbackClass.getClassLoader(),
                new Class<?>[] {callbackClass},
                new InvocationHandler() {
                    @Override
                    public Object invoke(final Object proxy, final Method method,
                                         final Object[] arguments) {
                        Log.i(TAG, "js callback " + method.getName() + " -> "
                                + (arguments == null
                                        ? "(keine Argumente)"
                                        : Arrays.toString(arguments)));
                        return null;
                    }
                });

        evaluateJavaScript.invoke(null, pointer,
                "document.readyState + '|' + (40 + 2)", callback);
        Log.i(TAG, "read-back probe sent via " + className);
    }

    /**
     * The native pointer is a {@code long} field on the WebContents implementation. Its name
     * is obfuscated, so pick the field by type instead. Fields declared directly on the
     * concrete class are checked first, and a non-zero value is required.
     */
    private static Field findNativePointerField(final Object webContents) {
        Class<?> type = webContents.getClass();

        while (type != null && type != Object.class) {
            for (final Field field : type.getDeclaredFields()) {
                if (field.getType() != long.class) {
                    continue;
                }

                try {
                    field.setAccessible(true);
                    if (field.getLong(webContents) != 0L) {
                        return field;
                    }
                } catch (Throwable ignored) {
                    // Not readable, keep looking.
                }
            }

            type = type.getSuperclass();
        }

        return null;
    }

    /**
     * Finds the still-registered {@code WebContentsImpl.evaluateJavaScript} native in
     * jni_zero's generated glue. Only the Java wrapper was removed by R8, the native itself
     * is intact.
     */
    private static Method findEvaluateJavaScript() {
        try {
            final Class<?> genJni = Class.forName("org.jni_zero.GEN_JNI");

            for (final Method method : genJni.getDeclaredMethods()) {
                if (!method.getName().endsWith("_evaluateJavaScript")) {
                    continue;
                }

                final Class<?>[] parameters = method.getParameterTypes();
                if (parameters.length == 3 && parameters[0] == long.class) {
                    method.setAccessible(true);
                    return method;
                }
            }
        } catch (Throwable ignored) {
            // Engine not loaded yet, the caller retries.
        }

        return null;
    }

    /**
     * The payload body. Deliberately plain ES5 and free of double quotes so it survives being
     * embedded as a Java string literal and still runs in Cobalt's JS engine.
     *
     * <p>It hooks JSON.parse, which is where the leanback app turns InnerTube responses into
     * objects, and deletes the ad keys before the app ever sees them. The fetch hook is a
     * second line of defence and only engages when the engine exposes a Response constructor.
     */
    private static final String PAYLOAD_BODY =
            "var KEYS = ['adPlacements','playerAds','adSlots','adBreakHeartbeatParams',"
            + "'importantAdBreak','adBreakServiceMetadata'];"
            + "function strip(o){"
            + "if(!o || typeof o !== 'object') return o;"
            + "for (var i=0;i<KEYS.length;i++){"
            + "if (KEYS[i] in o){ try{ delete o[KEYS[i]]; }catch(e){} }"
            + "}"
            + "return o;"
            + "}"
            + "var parse = JSON.parse;"
            + "JSON.parse = function(){"
            + "var r = parse.apply(this, arguments);"
            + "try{ strip(r); }catch(e){}"
            + "return r;"
            + "};"
            + "var origFetch = window.fetch;"
            + "if (origFetch && typeof Response === 'function'){"
            + "window.fetch = function(){"
            + "return origFetch.apply(this, arguments).then(function(res){"
            + "try{"
            + "var u = (res && res.url) || '';"
            + "if (u.indexOf('/youtubei/') !== -1 || u.indexOf('get_midroll_info') !== -1){"
            + "return res.clone().text().then(function(t){"
            + "try{"
            + "var j = parse(t); strip(j);"
            + "return new Response(JSON.stringify(j), {status: res.status, "
            + "statusText: res.statusText, headers: res.headers});"
            + "}catch(e){ return res; }"
            + "}).catch(function(){ return res; });"
            + "}"
            + "}catch(e){}"
            + "return res;"
            + "});"
            + "};"
            + "}";

    /**
     * Keeps the chosen playback speed across videos and across app starts.
     *
     * <p>The symptom: every new video starts at 1x even though a speed was picked before.
     *
     * <p>The first suspicion — that TizenTube never stores the value — is wrong, and the way it
     * looked wrong is worth recording. In the shipped bundle (@foxreis/tizentube 1.15.2) the
     * literal {@code videoSpeed} appears five times and only two of those are reads, which
     * suggests nothing ever writes it. But the write is generic: TizenTube intercepts every
     * {@code setClientSettingEndpoint} and mirrors each {@code clientSettingEnum.item} into its
     * config through {@code Ia(item, value)} — the same function that logs
     * {@code "Setting key videoSpeed to 2.5"} and stores {@code localStorage['ytaf-configuration']}.
     * Counting literals misses that call site. The device log settles it: picking 2.5x produces
     * exactly that line, and {@code videoSpeed":2.5} then shows up in the leveldb file.
     *
     * <p>So the value is stored, and TizenTube's own handler does apply it:
     *
     * <pre>
     * document.querySelector('video').addEventListener('canplay', () =&gt; {
     *     document.getElementsByTagName('video')[0].playbackRate = configRead('videoSpeed');
     * });
     * </pre>
     *
     * <p>What is missing is a second attempt. The player overwrites the rate with 1x <em>after</em>
     * {@code canplay} has passed — observed on the device as {@code RATE 2.5} followed a moment
     * later by {@code RATE 1} with no menu on screen — and TizenTube never looks again. That one
     * overwrite is the whole bug.
     *
     * <p>The fix therefore only has to win the race and keep winning it:
     *
     * <ol>
     *   <li><b>Enforce.</b> Re-apply the stored value on {@code canplay}/{@code loadeddata} from a
     *       capture-phase listener on {@code document}, so the write lands in a zero-delay
     *       callback after the whole dispatch; repeats at 250 ms and 1 s cover the immediate
     *       aftermath, and a two-second interval re-applies whenever the rate has drifted, which
     *       catches resets that arrive seconds later.</li>
     *   <li><b>Persist.</b> Still needed for rates that do not come through TizenTube's own menu —
     *       YouTube's native speed list writes the client setting but never the config. Writing
     *       the config object is enough: TizenTube's module-level cache is refreshed on the next
     *       page load, and our own interval covers the current session.</li>
     * </ol>
     *
     * <p>Noticing the user's choice is the hard part, and three attempts failed on the device.
     * Watching {@code ratechange} alone does not work, because the player's reset to 1x is
     * indistinguishable from a user picking 1x. Gating that on "a key was pressed recently" does
     * not work either: the leanback app dispatches synthetic {@code keydown} events for its own
     * navigation, so a reset arrives with a key press behind it just as often as a real one.
     * Hooking the {@code clientSettingEndpoint} request is a third dead end — the setting never
     * appears in a request body we can see.
     *
     * <p>What does separate them is the menu. It is still on screen when a selection lands, so a
     * {@code ratechange} with the menu open is the user's and one without it is the player's. The
     * menu is recognised by its title or — language-independent — by the run of {@code 1.25x}-style
     * labels only it contains. Measured on the device: every user selection logged {@code menu=1},
     * every player reset {@code menu=0}.
     *
     * <p>1x is never written back. A wrongly stored 1 would silently disable the feature until the
     * user set a speed again, and TizenTube stores 1x itself when 1x is really chosen — so there is
     * nothing to gain and the worst failure mode to lose.
     */
    private static final String SPEED_PAYLOAD =
            "(function(){"
            + "if (window.__ysamjoSpeed) return; window.__ysamjoSpeed = true;"
            + "var KEY = 'ytaf-configuration';"
            + "var FALLBACK = 1;"
            + "var quietUntil = 0;"
            + "function readCfg(){"
            + "try{ var c = JSON.parse(window.localStorage[KEY] || '{}'); return c || {}; }"
            + "catch(e){ return {}; }"
            + "}"
            + "function desired(){"
            + "var v = readCfg().videoSpeed;"
            + "if (typeof v === 'string') v = parseFloat(v);"
            + "if (typeof v !== 'number' || !isFinite(v) || v <= 0 || v > 5) return FALLBACK;"
            + "return v;"
            + "}"
            + "function persist(v){"
            + "try{ var c = readCfg(); c.videoSpeed = v;"
            + "window.localStorage[KEY] = JSON.stringify(c);"
            + "console.log('YSAMJO_SPEED_PERSIST ' + v);"
            + "}catch(e){ console.log('YSAMJO_SPEED_PERSIST_FAIL ' + e); }"
            + "}"
            + "function apply(v){"
            + "var el = document.querySelector('video');"
            + "if (!el) return;"
            + "if (Math.abs(el.playbackRate - v) < 0.0005) return;"
            + "try{ el.playbackRate = v; console.log('YSAMJO_SPEED_APPLY ' + v); }catch(e){}"
            + "}"
            + "function enforce(){"
            + "var v = desired();"
            + "console.log('YSAMJO_SPEED_ENFORCE ' + v);"
            + "quietUntil = Date.now() + 1500;"
            + "setTimeout(function(){ apply(v); }, 0);"
            + "setTimeout(function(){ apply(v); }, 250);"
            + "setTimeout(function(){ apply(v); }, 1000);"
            + "}"
            + "function menuOpen(){"
            + "try{"
            + "var b = document.body ? document.body.textContent : '';"
            + "if (!b) return false;"
            + "if (b.indexOf('Wiedergabegeschwindigkeit') !== -1) return true;"
            + "if (b.indexOf('Playback speed') !== -1) return true;"
            + "var hits = b.match(/[0-9]\\.[0-9]{1,2}x/g);"
            + "return !!(hits && hits.length >= 4);"
            + "}catch(e){ return false; }"
            + "}"
            + "document.addEventListener('canplay', enforce, true);"
            + "document.addEventListener('loadeddata', enforce, true);"
            + "document.addEventListener('ratechange', function(ev){"
            + "var el = ev.target;"
            + "if (!el || !el.tagName || el.tagName !== 'VIDEO') return;"
            + "var v = el.playbackRate;"
            + "var open = menuOpen();"
            + "console.log('YSAMJO_SPEED_RATE ' + v + ' quiet=' + (Date.now() < quietUntil)"
            + " + ' menu=' + (open ? 1 : 0));"
            + "if (Date.now() < quietUntil) return;"
            + "if (!open) return;"
            + "if (typeof v !== 'number' || !isFinite(v) || v <= 0 || v > 5) return;"
            + "if (Math.abs(v - desired()) < 0.0005) return;"
            + "if (Math.abs(v - 1) < 0.0005) return;"
            + "persist(v);"
            + "}, true);"
            + "setInterval(function(){"
            + "if (Date.now() < quietUntil) return;"
            + "apply(desired());"
            + "}, 2000);"
            + "})();";

    /** The payload as it ships. Guarded, so a re-injection into the same document is a no-op. */
    private static final String FULL_PAYLOAD =
            "(function(){"
            + "if (window.__ysamjoTvAdblock) return; window.__ysamjoTvAdblock = true;"
            + PAYLOAD_BODY
            + "console.log('YSAMJO_ADBLOCK_ACTIVE');"
            + "})();"
            + SPEED_PAYLOAD;

    /**
     * Diagnostic build. Answers, in one run, the questions that decide the next move:
     *
     * <ol>
     *   <li><b>Does the script run?</b> Proven by a same-document navigation, which Cobalt logs
     *       as {@code Navigated to ...#ysamjo-...}. That is an engine-side observation, so it
     *       cannot be faked by a console line that never arrives — and it is now established
     *       that console output from an injected script is <em>not</em> forwarded, while the
     *       page's own console messages are.</li>
     *   <li><b>Which transport carries the ad data?</b> The page demonstrably uses
     *       {@code XMLHttpRequest}, but the shipping payload only hooks {@code JSON.parse} and
     *       {@code fetch}. The counters separate "the hook never fires" from "the hook fires
     *       but never sees an ad key".</li>
     *   <li><b>Does stripping actually suppress the ad?</b> This is the one question the
     *       counters alone cannot answer, so the script carries a switch: if the loaded URL
     *       contains {@code nostrip}, {@code strip()} counts the ad keys but leaves them in
     *       place. Patching the same bundle twice — once with {@code ?ysamjo=1}, once with
     *       {@code ?ysamjo=1&nostrip=1} — turns "ads went away" into a controlled A/B instead
     *       of a coincidence that could just be ad rotation. The mode letter is part of the
     *       reported string so the log says which half of the experiment it belongs to.</li>
     * </ol>
     *
     * <p>Counter order: JSON.parse calls, JSON.parse results carrying any ad key, results
     * carrying {@code adPlacements}, fetch calls, fetch calls to an InnerTube URL, XHR opens,
     * XHR opens to an InnerTube URL, results carrying {@code playerAds}.
     */
    private static final String DIAG_PAYLOAD =
            "(function(){"
            + "if (window.__ysamjoTvAdblock) return; window.__ysamjoTvAdblock = true;"
            + "var KEYS = ['adPlacements','playerAds','adSlots','adBreakHeartbeatParams',"
            + "'importantAdBreak','adBreakServiceMetadata'];"
            + "var C = [0,0,0,0,0,0,0,0];"
            + "var NOSTRIP = location.search.indexOf('nostrip') !== -1;"
            + "function strip(o){"
            + "if(!o || typeof o !== 'object') return false;"
            + "var hit = false;"
            + "for (var i=0;i<KEYS.length;i++){"
            + "if (KEYS[i] in o){"
            + "hit = true;"
            + "if (KEYS[i] === 'adPlacements') C[2]++;"
            + "if (KEYS[i] === 'playerAds') C[7]++;"
            + "if (!NOSTRIP){ try{ delete o[KEYS[i]]; }catch(e){} }"
            + "}"
            + "}"
            + "return hit;"
            + "}"
            + "function isAd(u){ u = '' + u;"
            + "return u.indexOf('/youtubei/') !== -1 || u.indexOf('get_midroll_info') !== -1; }"
            + "var P = JSON.parse;"
            + "JSON.parse = function(){"
            + "C[0]++;"
            + "var r = P.apply(this, arguments);"
            + "try{ if (strip(r)) C[1]++; }catch(e){}"
            + "return r;"
            + "};"
            + "var origFetch = window.fetch;"
            + "if (origFetch){"
            + "window.fetch = function(a){"
            + "C[3]++;"
            + "var u = '';"
            + "try{ u = (a && a.url) ? a.url : a; }catch(e){}"
            + "if (isAd(u)) C[4]++;"
            + "return origFetch.apply(this, arguments).then(function(res){"
            + "try{"
            + "if (isAd((res && res.url) || u) && res && res.clone"
            + " && typeof Response === 'function'){"
            + "return res.clone().text().then(function(t){"
            + "try{"
            + "var j = P(t);"
            + "if (strip(j)) C[1]++;"
            + "return new Response(JSON.stringify(j), {status: res.status, "
            + "statusText: res.statusText, headers: res.headers});"
            + "}catch(e){ return res; }"
            + "}).catch(function(){ return res; });"
            + "}"
            + "}catch(e){}"
            + "return res;"
            + "});"
            + "};"
            + "}"
            + "var xhrOpen = XMLHttpRequest.prototype.open;"
            + "XMLHttpRequest.prototype.open = function(m, u){"
            + "C[5]++;"
            + "try{ if (isAd(u)) C[6]++; }catch(e){}"
            + "return xhrOpen.apply(this, arguments);"
            + "};"
            + "var n = 0;"
            + "var timer = setInterval(function(){"
            + "var s = (NOSTRIP ? 'B-' : 'A-') + C.join('.');"
            + "if (s !== window.__ysamjoLast){"
            + "window.__ysamjoLast = s;"
            + "try{ location.hash = '#ysamjo-' + s; }catch(e){}"
            + "}"
            + "if (++n > 20) clearInterval(timer);"
            + "}, 3000);"
            + "})();";

    /**
     * What actually gets injected. {@link #DIAG_PAYLOAD} while the runtime behaviour is still
     * unproven, {@link #FULL_PAYLOAD} once it is.
     */
    private static final String USERSCRIPT = FULL_PAYLOAD;
}

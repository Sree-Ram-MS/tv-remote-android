package ms.sreeram.remote;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Vibrator;
import android.text.InputType;
import android.view.*;
import android.view.inputmethod.EditorInfo;
import android.widget.*;
import java.net.*;
import java.util.concurrent.*;

public class Main extends Activity {
    static final int PORT = 10030;
    static boolean dark = true;
    static int BG, SURF, HI, LO, ICON, TEXT, PRESS;
    static final int ACCENT = 0xFF2BB673, RED = 0xFFE5484D, BLUE = 0xFF3B82F6;

    final ExecutorService ex = Executors.newSingleThreadExecutor();
    volatile DatagramSocket sock;
    volatile String myIp = "", tvIp;
    TextView status;
    SharedPreferences sp;
    final Handler h = new Handler();

    static void palette() {
        if (dark) { BG = 0xFF1C1C1E; SURF = 0xFF222224; HI = 0x0FFFFFFF; LO = 0xCC000000; ICON = 0xFFB4B8BF; TEXT = 0xFF8C9097; PRESS = 0xFF19191B; }
        else      { BG = 0xFFF3F4F5; SURF = 0xFFF3F4F5; HI = 0xFFFFFFFF; LO = 0x40889A8E; ICON = 0xFF97A09A; TEXT = 0xFF5F6662; PRESS = 0xFFE7E9EA; }
    }

    // ---------------- lifecycle ----------------
    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        sp = getSharedPreferences("r", 0);
        tvIp = sp.getString("ip", "192.168.31.149");
        dark = sp.getBoolean("dark", true);
        palette();
        Window w = getWindow();
        w.setStatusBarColor(BG); w.setNavigationBarColor(BG);
        if (!dark && Build.VERSION.SDK_INT >= 23) w.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        setContentView(buildUi());
        ex.execute(() -> {
            try {
                DatagramSocket s = new DatagramSocket(null);
                s.setReuseAddress(true); s.setBroadcast(true); s.bind(new InetSocketAddress(PORT));
                sock = s;
            } catch (Exception e) {
                try { DatagramSocket s = new DatagramSocket(); s.setBroadcast(true); sock = s; } catch (Exception ignored) {}
            }
            myIp = localIp();
            doPair();
            listen();
        });
    }

    @Override protected void onDestroy() { super.onDestroy(); if (sock != null) sock.close(); ex.shutdown(); }

    // ---------------- UI ----------------
    View buildUi() {
        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(BG); sv.setFillViewport(true); sv.setVerticalScrollBarEnabled(false);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(dp(18), dp(10), dp(18), dp(24));
        sv.addView(root);

        // top bar
        LinearLayout top = row();
        top.addView(new Key(this, Key.CIRCLE, "tv", 0, () -> connectDialog()).small(), sq(44));
        LinearLayout tt = new LinearLayout(this); tt.setOrientation(LinearLayout.VERTICAL); tt.setGravity(Gravity.CENTER);
        TextView title = new TextView(this); title.setText("TV REMOTE"); title.setTextColor(dark ? 0xFFFFFFFF : 0xFF3A3F3C);
        title.setTextSize(17); title.setLetterSpacing(0.15f); title.setTypeface(Typeface.DEFAULT_BOLD);
        status = new TextView(this); status.setTextColor(TEXT); status.setTextSize(11); status.setText("Connecting…");
        tt.addView(title); tt.addView(status);
        top.addView(tt, new LinearLayout.LayoutParams(0, -2, 1));
        top.addView(new Key(this, Key.CIRCLE, "theme", 0, () -> { dark = !dark; sp.edit().putBoolean("dark", dark).apply(); recreate(); }).small(), sq(44));
        top.addView(space(8), new LinearLayout.LayoutParams(dp(8), 1));
        top.addView(new Key(this, Key.CIRCLE, "help", 0, () -> helpDialog()).small(), sq(44));
        root.addView(top, fill(-2, 0, 0));

        // row 1: mute | power | menu
        LinearLayout r1 = row(); r1.setGravity(Gravity.CENTER);
        r1.addView(new Key(this, Key.CIRCLE, "mute", 164, null), sq(56));
        r1.addView(space(0), new LinearLayout.LayoutParams(0, 1, 1));
        Key pw = new Key(this, Key.CIRCLE, "power", 26, null); pw.tint = RED; pw.rings = true;
        r1.addView(pw, sq(86));
        r1.addView(space(0), new LinearLayout.LayoutParams(0, 1, 1));
        r1.addView(new Key(this, Key.CIRCLE, "menu", 82, null), sq(56));
        root.addView(r1, fill(-2, 18, 0));

        // row 2: vol rocker | input + keyboard | ch rocker
        LinearLayout r2 = row(); r2.setGravity(Gravity.CENTER);
        r2.addView(new Rocker(this, "Vol", "plus", "minus", 24, 25), new LinearLayout.LayoutParams(dp(78), dp(176)));
        LinearLayout mid = new LinearLayout(this); mid.setOrientation(LinearLayout.VERTICAL); mid.setGravity(Gravity.CENTER_HORIZONTAL);
        Key in = new Key(this, Key.CIRCLE, "input", 178, null); in.ring = true;
        in.onLong = this::inputDialog;
        mid.addView(in, sq(84));
        TextView il = new TextView(this); il.setText("INPUT  ·  hold for list"); il.setTextColor(TEXT); il.setTextSize(10);
        il.setGravity(Gravity.CENTER); il.setPadding(0, dp(6), 0, dp(10));
        il.setOnClickListener(v -> inputDialog());
        mid.addView(il);
        LinearLayout kb = row(); kb.setGravity(Gravity.CENTER);
        kb.addView(new Key(this, Key.CIRCLE, "kbd", 0, () -> keyboardDialog()).small(), sq(48));
        kb.addView(space(0), new LinearLayout.LayoutParams(dp(14), 1));
        kb.addView(new Key(this, Key.CIRCLE, "more", 0, () -> helpDialog()).small(), sq(48));
        mid.addView(kb);
        r2.addView(mid, new LinearLayout.LayoutParams(0, -2, 1));
        r2.addView(new Rocker(this, "Ch", "up", "down", 166, 167), new LinearLayout.LayoutParams(dp(78), dp(176)));
        root.addView(r2, fill(-2, 18, 0));

        // d-pad
        root.addView(new DPad(this), new LinearLayout.LayoutParams(dp(250), dp(250)) {{ topMargin = dp(16); gravity = Gravity.CENTER_HORIZONTAL; }});

        // row 4: home | back | settings
        root.addView(trio("home", 3, "back", 4, "gear", 176), fill(dp(64), 18, 0));
        // row 5: rewind | play | ff
        root.addView(trio("rew", 89, "play", 85, "ff", 90), fill(dp(64), 14, 0));
        return sv;
    }

    LinearLayout trio(String a, int ka, String b, int kb, String c, int kc) {
        LinearLayout r = row();
        String[] ic = {a, b, c}; int[] k = {ka, kb, kc};
        for (int i = 0; i < 3; i++) {
            Key key = new Key(this, Key.PILL, ic[i], k[i], null);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -1, 1);
            lp.setMargins(dp(7), 0, dp(7), 0);
            r.addView(key, lp);
        }
        return r;
    }

    // ---------------- dialogs ----------------
    AlertDialog.Builder dlg() {
        return new AlertDialog.Builder(this, dark ? android.R.style.Theme_DeviceDefault_Dialog_Alert : android.R.style.Theme_DeviceDefault_Light_Dialog_Alert);
    }

    void connectDialog() {
        LinearLayout l = new LinearLayout(this); l.setOrientation(LinearLayout.VERTICAL); l.setPadding(dp(22), dp(8), dp(22), 0);
        TextView t = new TextView(this); t.setText("Phone IP: " + myIp + "\nPhone and TV must be on the same Wi-Fi."); t.setTextColor(TEXT);
        final EditText ip = field(tvIp, InputType.TYPE_CLASS_PHONE);
        l.addView(t); l.addView(ip, fill(dp(48), 12, 0));
        final AlertDialog d = dlg().setTitle("Connect to TV").setView(l)
            .setPositiveButton("Pair", (x, y) -> { setIp(ip.getText().toString().trim()); pair(); })
            .setNeutralButton("Find TV", (x, y) -> discover())
            .setNegativeButton("Cancel", null).create();
        d.show();
    }

    void inputDialog() {
        final String[] n = {"Source menu (TV Input)", "HDMI 1", "HDMI 2", "HDMI 3", "HDMI 4", "AV (Composite)", "Component", "VGA / PC", "TV / Antenna", "Live TV"};
        final int[] c = {178, 243, 244, 245, 246, 247, 249, 251, 242, 170};
        dlg().setTitle("Switch input").setItems(n, (d, i) -> key(c[i])).setNegativeButton("Close", null).show();
    }

    void keyboardDialog() {
        final EditText e = field("", InputType.TYPE_CLASS_TEXT);
        e.setHint("Type to send to TV"); e.setImeOptions(EditorInfo.IME_ACTION_SEND);
        FrameLayout f = new FrameLayout(this); f.setPadding(dp(22), dp(8), dp(22), 0);
        f.addView(e, new FrameLayout.LayoutParams(-1, dp(48)));
        final AlertDialog d = dlg().setTitle("Keyboard").setView(f)
            .setPositiveButton("Send", (x, y) -> typeText(e.getText().toString()))
            .setNeutralButton("⌫ Delete", (x, y) -> key(67))
            .setNegativeButton("Close", null).create();
        e.setOnEditorActionListener((v, a, ev) -> { typeText(e.getText().toString()); e.setText(""); return true; });
        d.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
        d.show();
    }

    static final Object[][] ALL = {
        {"— Basics —", -1}, {"Power", 26}, {"Home", 3}, {"Back", 4}, {"Menu", 82}, {"OK / Select", 23},
        {"Up", 19}, {"Down", 20}, {"Left", 21}, {"Right", 22},
        {"— Sound & channels —", -1}, {"Volume +", 24}, {"Volume −", 25}, {"Mute", 164}, {"Channel +", 166}, {"Channel −", 167},
        {"— Inputs —", -1}, {"Source menu", 178}, {"HDMI 1", 243}, {"HDMI 2", 244}, {"HDMI 3", 245}, {"HDMI 4", 246},
        {"AV (Composite)", 247}, {"Component", 249}, {"VGA / PC", 251}, {"TV / Antenna", 242}, {"Live TV", 170},
        {"— Playback —", -1}, {"Play / Pause", 85}, {"Stop", 86}, {"Rewind", 89}, {"Fast forward", 90}, {"Previous", 88}, {"Next", 87},
        {"Subtitles", 175}, {"Audio track", 222},
        {"— System —", -1}, {"Settings", 176}, {"Info", 165}, {"Guide / EPG", 172}, {"Recent apps", 187}, {"Search", 84},
        {"Sleep (screen off)", 223}, {"Wake up", 224}, {"Brightness +", 221}, {"Brightness −", 220},
        {"— Colour keys —", -1}, {"Red", 183}, {"Green", 184}, {"Yellow", 185}, {"Blue", 186},
        {"— Numbers —", -1}, {"0", 7}, {"1", 8}, {"2", 9}, {"3", 10}, {"4", 11}, {"5", 12}, {"6", 13}, {"7", 14}, {"8", 15}, {"9", 16},
        {"Delete", 67}, {"Enter", 66},
        {"— App buttons —", -1}, {"TV icon (top left): connect / change TV IP", -2}, {"Sun-moon: light or dark theme", -2},
        {"Keyboard: type text on TV", -3}, {"Hold Input: pick HDMI / AV / TV", -4}, {"Hold D-pad or Vol/Ch: repeats", -2}
    };

    void helpDialog() {
        ListView lv = new ListView(this);
        lv.setDivider(null);
        lv.setAdapter(new BaseAdapter() {
            public int getCount() { return ALL.length; }
            public Object getItem(int i) { return ALL[i]; }
            public long getItemId(int i) { return i; }
            public boolean isEnabled(int i) { return (Integer) ALL[i][1] != -1; }
            public View getView(int i, View cv, ViewGroup p) {
                TextView t = new TextView(Main.this);
                int code = (Integer) ALL[i][1];
                boolean hdr = code == -1;
                t.setText(hdr ? ((String) ALL[i][0]).replace("— ", "").replace(" —", "").toUpperCase() : (String) ALL[i][0]);
                t.setTextColor(hdr ? ACCENT : (dark ? 0xFFE6E6E6 : 0xFF333333));
                t.setTextSize(hdr ? 12 : 16);
                if (hdr) t.setTypeface(Typeface.DEFAULT_BOLD);
                t.setPadding(dp(24), dp(hdr ? 16 : 11), dp(24), dp(hdr ? 4 : 11));
                if (code >= 0) {
                    t.setCompoundDrawablePadding(dp(8));
                    t.setText(ALL[i][0] + "   ");
                    t.append(android.text.Html.fromHtml("<small><font color='#888888'>key " + code + "</font></small>"));
                }
                return t;
            }
        });
        final AlertDialog d = dlg().setTitle("All buttons · tap to send").setView(lv).setNegativeButton("Close", null).create();
        lv.setOnItemClickListener((p, v, i, id) -> {
            int code = (Integer) ALL[i][1];
            if (code >= 0) key(code);
            else if (code == -3) { d.dismiss(); keyboardDialog(); }
            else if (code == -4) { d.dismiss(); inputDialog(); }
        });
        d.show();
    }

    // ---------------- network ----------------
    void setIp(String ip) { if (ip.length() > 0) { tvIp = ip; sp.edit().putString("ip", ip).apply(); } }

    void key(int code) { buzz(); final String m = myIp + "&key=action;" + code + "&"; ex.execute(() -> raw(m, tvIp)); }

    void typeText(final String s) {
        ex.execute(() -> { for (char c : s.toCharArray()) { raw(myIp + "&key=editText;" + c, tvIp); sleep(40); } });
    }

    void pair() { ex.execute(this::doPair); }
    void doPair() {
        myIp = localIp();
        raw("POSTIP", "255.255.255.255");
        raw(myIp + "&TVIP=" + tvIp + "&TV Remote", tvIp);
        say("Paired with " + tvIp);
    }

    void listen() {
        new Thread(() -> {
            byte[] buf = new byte[4096];
            while (sock != null && !sock.isClosed()) {
                try {
                    DatagramPacket p = new DatagramPacket(buf, buf.length);
                    sock.receive(p);
                    String from = p.getAddress().getHostAddress();
                    if (!from.equals(myIp)) say("● Connected · " + from);
                } catch (Exception e) { break; }
            }
        }).start();
    }

    void discover() {
        say("Searching for TV…");
        new Thread(() -> {
            try {
                DatagramSocket d = new DatagramSocket();
                d.setBroadcast(true); d.setSoTimeout(2500);
                byte[] m = "POSTIP".getBytes();
                d.send(new DatagramPacket(m, m.length, InetAddress.getByName("255.255.255.255"), PORT));
                byte[] buf = new byte[2048];
                DatagramPacket r = new DatagramPacket(buf, buf.length);
                d.receive(r); d.close();
                final String ip = r.getAddress().getHostAddress();
                runOnUiThread(() -> { setIp(ip); pair(); });
            } catch (Exception e) { say("No TV found · using " + tvIp); }
        }).start();
    }

    void raw(String msg, String ip) {
        try {
            byte[] d = msg.getBytes("UTF-8");
            DatagramSocket s = sock != null ? sock : new DatagramSocket();
            s.send(new DatagramPacket(d, d.length, InetAddress.getByName(ip), PORT));
        } catch (Exception e) { say("Send failed · check Wi-Fi"); }
    }

    String localIp() {
        try (DatagramSocket s = new DatagramSocket()) {
            s.connect(InetAddress.getByName(tvIp), PORT);
            return s.getLocalAddress().getHostAddress();
        } catch (Exception e) { return "0.0.0.0"; }
    }

    // ---------------- helpers ----------------
    void say(String s) { runOnUiThread(() -> status.setText(s)); }
    void sleep(long ms) { try { Thread.sleep(ms); } catch (Exception ignored) {} }
    void buzz() { try { ((Vibrator) getSystemService(VIBRATOR_SERVICE)).vibrate(12); } catch (Exception ignored) {} }
    int dp(float v) { return (int) (v * getResources().getDisplayMetrics().density + .5f); }
    LinearLayout row() { LinearLayout l = new LinearLayout(this); l.setOrientation(LinearLayout.HORIZONTAL); l.setGravity(Gravity.CENTER_VERTICAL); return l; }
    View space(int w) { return new View(this); }
    LinearLayout.LayoutParams sq(int s) { return new LinearLayout.LayoutParams(dp(s), dp(s)); }
    LinearLayout.LayoutParams fill(int hgt, int top, int bot) { LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, hgt); lp.topMargin = dp(top); lp.bottomMargin = dp(bot); return lp; }
    EditText field(String t, int type) {
        EditText e = new EditText(this); e.setText(t); e.setInputType(type); e.setSingleLine(true);
        e.setTextColor(dark ? Color.WHITE : 0xFF222222); e.setHintTextColor(TEXT); e.setPadding(dp(14), 0, dp(14), 0);
        GradientDrawable g = new GradientDrawable(); g.setColor(dark ? 0xFF2C2C2E : 0xFFE9EBEC); g.setCornerRadius(dp(14)); e.setBackground(g);
        return e;
    }

    // ================= custom views =================
    static abstract class Neu extends View {
        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG), sh = new Paint(Paint.ANTI_ALIAS_FLAG), ip = new Paint(Paint.ANTI_ALIAS_FLAG);
        final Main m; final float d; final Handler h = new Handler();
        Neu(Main c) {
            super(c); m = c; d = c.getResources().getDisplayMetrics().density;
            setLayerType(LAYER_TYPE_SOFTWARE, null);
            ip.setStyle(Paint.Style.STROKE); ip.setStrokeCap(Paint.Cap.ROUND); ip.setStrokeJoin(Paint.Join.ROUND);
        }
        void raised(Canvas c, RectF r, float rad, boolean pressed) {
            float o = 5 * d, blur = 9 * d;
            if (!pressed) {
                sh.setMaskFilter(new BlurMaskFilter(blur, BlurMaskFilter.Blur.NORMAL));
                sh.setColor(HI); RectF a = new RectF(r); a.offset(-o * .7f, -o * .7f); c.drawRoundRect(a, rad, rad, sh);
                sh.setColor(LO); RectF b = new RectF(r); b.offset(o, o); c.drawRoundRect(b, rad, rad, sh);
            }
            p.setShader(null); p.setStyle(Paint.Style.FILL);
            p.setColor(pressed ? PRESS : SURF);
            c.drawRoundRect(r, rad, rad, p);
            if (dark) { // subtle top sheen
                p.setShader(new LinearGradient(0, r.top, 0, r.bottom, pressed ? 0x00000000 : 0x0CFFFFFF, 0x00000000, Shader.TileMode.CLAMP));
                c.drawRoundRect(r, rad, rad, p); p.setShader(null);
            }
        }
        void repeatStart(final Runnable r) {
            r.run();
            h.postDelayed(new Runnable() { public void run() { r.run(); h.postDelayed(this, 110); } }, 420);
        }
        void repeatStop() { h.removeCallbacksAndMessages(null); }
    }

    // icon drawing in unit box: u = half-size
    static void icon(Canvas c, Paint ip, String n, float cx, float cy, float u, int col) {
        ip.setColor(col); ip.setStrokeWidth(u * 0.17f); ip.setStyle(Paint.Style.STROKE);
        Path pa = new Path();
        switch (n) {
            case "power":
                c.drawArc(new RectF(cx - .75f * u, cy - .7f * u, cx + .75f * u, cy + .8f * u), -55, 290, false, ip);
                c.drawLine(cx, cy - .95f * u, cx, cy - .05f * u, ip); break;
            case "mute": case "vol":
                ip.setStyle(Paint.Style.FILL);
                pa.moveTo(cx - .9f * u, cy - .3f * u); pa.lineTo(cx - .45f * u, cy - .3f * u); pa.lineTo(cx + .05f * u, cy - .75f * u);
                pa.lineTo(cx + .05f * u, cy + .75f * u); pa.lineTo(cx - .45f * u, cy + .3f * u); pa.lineTo(cx - .9f * u, cy + .3f * u); pa.close();
                c.drawPath(pa, ip); ip.setStyle(Paint.Style.STROKE);
                if (n.equals("mute")) { c.drawLine(cx + .35f * u, cy - .3f * u, cx + .9f * u, cy + .3f * u, ip); c.drawLine(cx + .9f * u, cy - .3f * u, cx + .35f * u, cy + .3f * u, ip); }
                break;
            case "menu":
                for (int i = -1; i <= 1; i++) c.drawLine(cx - .7f * u, cy + i * .5f * u, cx + .7f * u, cy + i * .5f * u, ip); break;
            case "plus": c.drawLine(cx - .6f * u, cy, cx + .6f * u, cy, ip); c.drawLine(cx, cy - .6f * u, cx, cy + .6f * u, ip); break;
            case "minus": c.drawLine(cx - .6f * u, cy, cx + .6f * u, cy, ip); break;
            case "up": chev(c, ip, cx, cy, u, 0, -1); break;
            case "down": chev(c, ip, cx, cy, u, 0, 1); break;
            case "left": chev(c, ip, cx, cy, u, -1, 0); break;
            case "right": chev(c, ip, cx, cy, u, 1, 0); break;
            case "input":
                pa.moveTo(cx - .2f * u, cy - .45f * u); pa.lineTo(cx - .2f * u, cy - .8f * u); pa.lineTo(cx + .8f * u, cy - .8f * u);
                pa.lineTo(cx + .8f * u, cy + .8f * u); pa.lineTo(cx - .2f * u, cy + .8f * u); pa.lineTo(cx - .2f * u, cy + .45f * u);
                c.drawPath(pa, ip);
                c.drawLine(cx - .9f * u, cy, cx + .35f * u, cy, ip);
                c.drawLine(cx + .35f * u, cy, cx + .02f * u, cy - .33f * u, ip); c.drawLine(cx + .35f * u, cy, cx + .02f * u, cy + .33f * u, ip); break;
            case "home":
                pa.moveTo(cx - .85f * u, cy - .05f * u); pa.lineTo(cx, cy - .8f * u); pa.lineTo(cx + .85f * u, cy - .05f * u);
                pa.moveTo(cx - .6f * u, cy - .25f * u); pa.lineTo(cx - .6f * u, cy + .75f * u); pa.lineTo(cx + .6f * u, cy + .75f * u); pa.lineTo(cx + .6f * u, cy - .25f * u);
                c.drawPath(pa, ip); break;
            case "back":
                c.drawArc(new RectF(cx - .55f * u, cy - .55f * u, cx + .75f * u, cy + .75f * u), -90, 220, false, ip);
                c.drawLine(cx + .1f * u, cy - .55f * u, cx - .7f * u, cy - .55f * u, ip);
                c.drawLine(cx - .7f * u, cy - .55f * u, cx - .35f * u, cy - .9f * u, ip); c.drawLine(cx - .7f * u, cy - .55f * u, cx - .35f * u, cy - .2f * u, ip); break;
            case "kbd":
                c.drawRoundRect(new RectF(cx - .9f * u, cy - .55f * u, cx + .9f * u, cy + .6f * u), .2f * u, .2f * u, ip);
                ip.setStyle(Paint.Style.FILL);
                for (int r = 0; r < 2; r++) for (int i = 0; i < 4; i++) c.drawCircle(cx - .54f * u + i * .36f * u, cy - .2f * u + r * .3f * u, .08f * u, ip);
                ip.setStyle(Paint.Style.STROKE); c.drawLine(cx - .35f * u, cy + .33f * u, cx + .35f * u, cy + .33f * u, ip); break;
            case "more":
                ip.setStyle(Paint.Style.FILL); for (int i = -1; i <= 1; i++) c.drawCircle(cx + i * .55f * u, cy, .14f * u, ip); break;
            case "play":
                ip.setStyle(Paint.Style.FILL);
                pa.moveTo(cx - .8f * u, cy - .6f * u); pa.lineTo(cx + .05f * u, cy); pa.lineTo(cx - .8f * u, cy + .6f * u); pa.close(); c.drawPath(pa, ip);
                c.drawRect(cx + .25f * u, cy - .6f * u, cx + .45f * u, cy + .6f * u, ip); c.drawRect(cx + .65f * u, cy - .6f * u, cx + .85f * u, cy + .6f * u, ip); break;
            case "rew": case "ff":
                ip.setStyle(Paint.Style.FILL); float s = n.equals("ff") ? 1 : -1;
                for (int i = 0; i < 2; i++) { float x0 = cx + s * (-.85f + i * .8f) * u; pa.moveTo(x0, cy - .55f * u); pa.lineTo(x0 + s * .8f * u, cy); pa.lineTo(x0, cy + .55f * u); pa.close(); }
                c.drawPath(pa, ip); break;
            case "gear":
                c.drawCircle(cx, cy, .3f * u, ip);
                for (int i = 0; i < 8; i++) { double a = Math.PI * i / 4; c.drawLine(cx + (float) Math.cos(a) * .55f * u, cy + (float) Math.sin(a) * .55f * u, cx + (float) Math.cos(a) * .85f * u, cy + (float) Math.sin(a) * .85f * u, ip); }
                c.drawCircle(cx, cy, .58f * u, ip); break;
            case "tv":
                c.drawRoundRect(new RectF(cx - .85f * u, cy - .7f * u, cx + .85f * u, cy + .45f * u), .12f * u, .12f * u, ip);
                c.drawLine(cx - .4f * u, cy + .8f * u, cx + .4f * u, cy + .8f * u, ip); break;
            case "theme":
                c.drawCircle(cx, cy, .7f * u, ip); ip.setStyle(Paint.Style.FILL);
                c.drawArc(new RectF(cx - .7f * u, cy - .7f * u, cx + .7f * u, cy + .7f * u), 90, 180, true, ip); break;
            case "help":
                ip.setStyle(Paint.Style.FILL); ip.setTextSize(1.5f * u); ip.setTextAlign(Paint.Align.CENTER); ip.setTypeface(Typeface.DEFAULT_BOLD);
                c.drawText("?", cx, cy + .52f * u, ip); break;
        }
        ip.setStyle(Paint.Style.STROKE);
    }

    static void chev(Canvas c, Paint ip, float cx, float cy, float u, int dx, int dy) {
        float a = .45f * u, b = .25f * u;
        Path p = new Path();
        if (dy != 0) { p.moveTo(cx - a, cy - dy * b); p.lineTo(cx, cy + dy * b); p.lineTo(cx + a, cy - dy * b); }
        else { p.moveTo(cx - dx * b, cy - a); p.lineTo(cx + dx * b, cy); p.lineTo(cx - dx * b, cy + a); }
        c.drawPath(p, ip);
    }

    static class Key extends Neu {
        static final int CIRCLE = 0, PILL = 1;
        final int shape, code; final String ic; final Runnable act; Runnable onLong;
        int tint = 0; boolean ring, rings, down, isSmall, longFired;
        final Runnable longR = () -> { longFired = true; if (onLong != null) { m.buzz(); onLong.run(); } };
        Key(Main c, int shape, String ic, int code, Runnable act) { super(c); this.shape = shape; this.ic = ic; this.code = code; this.act = act; }
        Key small() { isSmall = true; return this; }
        @Override protected void onDraw(Canvas c) {
            float w = getWidth(), hh = getHeight(), pad = (isSmall ? 4 : 9) * d;
            RectF r = new RectF(pad, pad, w - pad, hh - pad);
            float rad = Math.min(r.width(), r.height()) / 2;
            if (rings && dark) { p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(d); p.setColor(0x10FFFFFF); for (int i = 1; i < 5; i++) c.drawCircle(w / 2, hh / 2, rad * (0.55f + i * 0.1f), p); }
            if (isSmall && !down) { p.setStyle(Paint.Style.FILL); p.setColor(dark ? 0xFF262628 : 0xFFE9EBEC); c.drawRoundRect(r, rad, rad, p); }
            else raised(c, r, rad, down);
            if (ring) {
                p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(3.2f * d);
                p.setShader(new SweepGradient(w / 2, hh / 2, new int[]{BLUE, ACCENT, 0xFF8BE36B, BLUE}, null));
                c.drawCircle(w / 2, hh / 2, rad * 0.78f, p); p.setShader(null);
            }
            float u = Math.min(r.width(), r.height()) * (isSmall ? .24f : ring ? .2f : .22f);
            int col = down ? ACCENT : (tint != 0 ? tint : ICON);
            icon(c, ip, ic, w / 2, hh / 2, u, col);
        }
        @Override public boolean onTouchEvent(MotionEvent e) {
            switch (e.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    down = true; longFired = false; invalidate();
                    if (onLong != null) h.postDelayed(longR, 450);
                    else if (code > 0 && rep()) repeatStart(() -> m.key(code));
                    return true;
                case MotionEvent.ACTION_UP:
                    if (down && !longFired && !(code > 0 && rep() && onLong == null)) { if (act != null) { m.buzz(); act.run(); } else if (code > 0) m.key(code); }
                case MotionEvent.ACTION_CANCEL:
                    down = false; repeatStop(); h.removeCallbacks(longR); invalidate(); return true;
            }
            return true;
        }
        boolean rep() { return code == 89 || code == 90; }
    }

    static class Rocker extends Neu {
        final String label, a, b; final int ka, kb; int down = 0;
        Rocker(Main c, String label, String a, String b, int ka, int kb) { super(c); this.label = label; this.a = a; this.b = b; this.ka = ka; this.kb = kb; }
        @Override protected void onDraw(Canvas c) {
            float w = getWidth(), hh = getHeight(), pad = 9 * d;
            RectF r = new RectF(pad, pad, w - pad, hh - pad);
            raised(c, r, r.width() / 2, false);
            if (down != 0) {
                p.setColor(PRESS); p.setStyle(Paint.Style.FILL);
                c.save(); c.clipRect(r.left, down < 0 ? r.top : r.centerY(), r.right, down < 0 ? r.centerY() : r.bottom);
                c.drawRoundRect(r, r.width() / 2, r.width() / 2, p); c.restore();
            }
            float u = r.width() * .17f;
            icon(c, ip, a, w / 2, r.top + r.width() * .55f, u, down < 0 ? ACCENT : ICON);
            icon(c, ip, b, w / 2, r.bottom - r.width() * .55f, u, down > 0 ? ACCENT : ICON);
            p.setColor(TEXT); p.setTextSize(15 * d); p.setTextAlign(Paint.Align.CENTER); p.setStyle(Paint.Style.FILL);
            c.drawText(label, w / 2, hh / 2 + 5 * d, p);
        }
        @Override public boolean onTouchEvent(MotionEvent e) {
            if (e.getAction() == MotionEvent.ACTION_DOWN) {
                down = e.getY() < getHeight() / 2f ? -1 : 1; final int k = down < 0 ? ka : kb;
                invalidate(); repeatStart(() -> m.key(k)); return true;
            }
            if (e.getAction() == MotionEvent.ACTION_UP || e.getAction() == MotionEvent.ACTION_CANCEL) { down = 0; repeatStop(); invalidate(); }
            return true;
        }
    }

    static class DPad extends Neu {
        int down = -1; // 0 up 1 right 2 down 3 left 4 ok
        DPad(Main c) { super(c); }
        @Override protected void onDraw(Canvas c) {
            float w = getWidth(), cx = w / 2, cy = getHeight() / 2, R = Math.min(w, getHeight()) / 2 - 12 * d, r = R * .36f;
            raised(c, new RectF(cx - R, cy - R, cx + R, cy + R), R, false);
            if (down >= 0 && down < 4) {
                p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(4 * d); p.setColor(ACCENT); p.setStrokeCap(Paint.Cap.ROUND);
                float st = new float[]{-120, -30, 60, 150}[down];
                c.drawArc(new RectF(cx - R + 3 * d, cy - R + 3 * d, cx + R - 3 * d, cy + R - 3 * d), st, 60, false, p);
            }
            // inner ok
            p.setStyle(Paint.Style.FILL);
            RectF ok = new RectF(cx - r, cy - r, cx + r, cy + r);
            sh.setMaskFilter(new BlurMaskFilter(8 * d, BlurMaskFilter.Blur.NORMAL));
            if (down != 4) { sh.setColor(LO); c.drawCircle(cx + 4 * d, cy + 4 * d, r, sh); sh.setColor(HI); c.drawCircle(cx - 3 * d, cy - 3 * d, r, sh); }
            p.setColor(down == 4 ? PRESS : SURF); c.drawOval(ok, p);
            p.setColor(down == 4 ? ACCENT : (dark ? 0xFFD0D3D8 : 0xFF6B716D)); p.setTextSize(r * .55f); p.setTextAlign(Paint.Align.CENTER);
            c.drawText("OK", cx, cy + r * .2f, p);
            float u = R * .1f, o = R * .7f;
            icon(c, ip, "up", cx, cy - o, u, down == 0 ? ACCENT : ICON);
            icon(c, ip, "right", cx + o, cy, u, down == 1 ? ACCENT : ICON);
            icon(c, ip, "down", cx, cy + o, u, down == 2 ? ACCENT : ICON);
            icon(c, ip, "left", cx - o, cy, u, down == 3 ? ACCENT : ICON);
        }
        @Override public boolean onTouchEvent(MotionEvent e) {
            if (e.getAction() == MotionEvent.ACTION_DOWN) {
                float dx = e.getX() - getWidth() / 2f, dy = e.getY() - getHeight() / 2f;
                float R = Math.min(getWidth(), getHeight()) / 2f - 12 * d;
                double dist = Math.hypot(dx, dy);
                if (dist > R + 10 * d) return false;
                if (dist < R * .38f) down = 4;
                else { double a = Math.toDegrees(Math.atan2(dy, dx)); // -180..180, 0 = right
                    down = (a >= -135 && a < -45) ? 0 : (a >= -45 && a < 45) ? 1 : (a >= 45 && a < 135) ? 2 : 3; }
                final int k = new int[]{19, 22, 20, 21, 23}[down];
                invalidate();
                if (down == 4) m.key(k); else repeatStart(() -> m.key(k));
                return true;
            }
            if (e.getAction() == MotionEvent.ACTION_UP || e.getAction() == MotionEvent.ACTION_CANCEL) { down = -1; repeatStop(); invalidate(); }
            return true;
        }
    }
}

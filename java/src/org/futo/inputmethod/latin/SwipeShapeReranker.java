/*
 * Copyright (C) 2026 gofercraft
 *
 * Licensed under the same terms as the FUTO Keyboard source it is added to.
 */

package org.futo.inputmethod.latin;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Re-ranks the swipe decoder's candidate list using the shape of the gesture.
 *
 * Each candidate word gets a linear score: a scaled copy of the decoder's own score plus
 * weighted features that compare the raw gesture with the word's key path (path length
 * against the word's ideal length, start and end point distance, headings, closeness of
 * middle letters, slowdowns and corners away from letters, and a few more). Weights were
 * fitted offline on MIT/CC0 human swipes (swipe.futo.org: FIT split of swipe-1, swipe-3/4/5)
 * and tested on swipe-2: top-1 90.25% to 93.07%.
 *
 * All coordinates are in key widths (pixels / (keyboard width / 10)) and times in ms.
 * Plain Java with no Android dependencies so it can be tested on a desktop JVM.
 */
public final class SwipeShapeReranker {
    private SwipeShapeReranker() {}

    private static final double STEP = 0.1;   // key widths between resampled points
    private static final int SPD_W = 3;        // +-0.3 key window for speed
    private static final int TURN_K = 4;       // +-0.4 key window for turning angle
    private static final int NEAR = 6;         // 0.6 key: "near an aligned letter"

    // Fitted weights, raw feature units.
    static final double DECODER_SCALE = 0.6401272188840087;

    /**
     * The re-ranked first word must beat the decoder's own first word by this much (held-out
     * swipe-2): the re-ranker's fixes win by a median of 2.05, its breaks by 0.44. On held-out swipe-2 this
     * keeps 788 of 812 fixes and cuts breaks from 155 to 118. 0.5 would halve them (74) for a third
     * of the net on rare words.
     */
    static final double MIN_OVERRIDE_LEAD = 0.15;

    /** The "Stricter swipe shape" setting's lead: halves swipe-2's breaks (74), same net there. */
    static final double STRICT_MIN_OVERRIDE_LEAD = 0.5;
    static final String[] FEATURES = {
            "start_d", "end_d", "start_ok", "end_ok", "mid_mean", "mid_max", "mid_ok", "log_len_ratio",
            "abs_log_len_ratio", "mid_speed", "unexpl_slow", "unexpl_corner", "missing_corner", "silent_frac",
            "crossed_absent", "crossed_present", "init_head", "final_head", "end_over", "abs_end_over",
            "start_over", "abs_start_over", "corner_over", "mid_dwell", "n_double", "double_dwell",
            "double_loop", "one_letter", "one_x_logarc", "one_x_dur", "two_x_logarc", "logn_keys_x_logarc"};
    static final double[] WEIGHTS = {
            -4.481866118333427, -1.083520066791775, -0.06475474761340654, 0.8461073512096102,
            -0.49081025548735924, -0.13880742643671737, 0.09652471267722838, -0.900892526181941,
            1.0253897000181809, 1.2792790292924359, -0.3024760007057711, -0.1835733813120415,
            0.004174942624240635, -0.06626395354796505, -0.2192269837492009, -0.08314010579876817,
            -0.8952530406438912, -0.15160906102751, -0.05535775115039408, 0.4272788065048873,
            0.08008789028623384, 0.01902152779682682, 0.3851646565202785, 1.0514232402240347,
            -0.4976773999982364, 0.08850553115976127, 0.6181115733590079, 5.237125792745754,
            -2.564986078754652, 0.6675296227263704, -0.21536993555511283, -0.30274489714003405};

    /**
     * Returns the candidate indices in their new order (best first).
     *
     * @param xs, ys, ts  the gesture's raw points (key widths, key widths, ms)
     * @param keys        letter ('a'..'z') to key centre {x, y} in key widths
     * @param words       the decoder's candidates, best first
     * @param scores      the decoder's scores for those candidates
     */
    public static int[] rerank(final float[] xs, final float[] ys, final float[] ts,
                               final Map<Character, float[]> keys,
                               final String[] words, final float[] scores) {
        return rerank(xs, ys, ts, keys, words, scores, strict ? STRICT_MIN_OVERRIDE_LEAD : MIN_OVERRIDE_LEAD);
    }

    private static volatile boolean strict = false;

    /** The "Stricter swipe shape" setting, set by the decoder before each re-rank. */
    public static void setStrict(final boolean on) { strict = on; }

    /** As above, with the lead the re-ranked first word needs over the decoder's own first word. */
    public static int[] rerank(final float[] xs, final float[] ys, final float[] ts,
                               final Map<Character, float[]> keys,
                               final String[] words, final float[] scores, final double minLead) {
        final int n = words.length;
        final Integer[] order = new Integer[n];
        for (int i = 0; i < n; i++) order[i] = i;
        final int[] out = new int[n];
        if (n < 2 || xs.length == 0 || keys.isEmpty()) {
            for (int i = 0; i < n; i++) out[i] = i;
            return out;
        }
        final Gesture g = new Gesture(xs, ys, ts, keys);
        final double[] s = new double[n];
        for (int i = 0; i < n; i++) s[i] = score(g, words[i], scores[i]);
        Arrays.sort(order, (a, b) -> s[a] != s[b] ? Double.compare(s[b], s[a]) : Integer.compare(a, b));
        // Overturn the decoder's own first word only on a clear lead; otherwise put it back first
        if (order[0] != 0 && s[order[0]] - s[0] < minLead) {
            int at = 0;
            while (order[at] != 0) at++;
            System.arraycopy(order, 0, order, 1, at);
            order[0] = 0;
        }
        for (int i = 0; i < n; i++) out[i] = order[i];
        return out;
    }

    /** The re-ranker's score for each word (higher is better), in the input order. */
    public static double[] scores(final float[] xs, final float[] ys, final float[] ts,
                                  final Map<Character, float[]> keys,
                                  final String[] words, final float[] scores) {
        final double[] s = new double[words.length];
        if (xs.length == 0 || keys.isEmpty()) {
            for (int i = 0; i < s.length; i++) s[i] = DECODER_SCALE * scores[i];
            return s;
        }
        final Gesture g = new Gesture(xs, ys, ts, keys);
        for (int i = 0; i < s.length; i++) s[i] = score(g, words[i], scores[i]);
        return s;
    }

    static double score(final Gesture g, final String word, final float decoderScore) {
        final double[] f = g.features(word);
        double s = DECODER_SCALE * decoderScore;
        if (f == null) return s;
        for (int i = 0; i < WEIGHTS.length; i++) s += WEIGHTS[i] * f[i];
        return s;
    }

    static String fold(final String word) {
        final StringBuilder sb = new StringBuilder();
        final String lower = word.toLowerCase(Locale.ROOT);
        for (int i = 0; i < lower.length(); ) {
            final int cp = lower.codePointAt(i);
            i += Character.charCount(cp);
            if (cp >= 'a' && cp <= 'z') { sb.append((char) cp); continue; }
            final String d = Normalizer.normalize(new String(Character.toChars(cp)), Normalizer.Form.NFD);
            final char base = d.isEmpty() ? 0 : d.charAt(0);
            if (base >= 'a' && base <= 'z') sb.append(base);
        }
        return sb.toString();
    }

    private static double angle(double ax, double ay, double bx, double by) {
        final double na = Math.hypot(ax, ay), nb = Math.hypot(bx, by);
        if (na < 1e-9 || nb < 1e-9) return Double.NaN;
        final double c = (ax * bx + ay * by) / (na * nb);
        return Math.toDegrees(Math.acos(Math.max(-1.0, Math.min(1.0, c))));
    }

    private static double median(final double[] v, final int from, final int to) {
        final double[] c = Arrays.copyOfRange(v, from, to);
        Arrays.sort(c);
        final int m = c.length;
        if (m == 0) return Double.NaN;
        return (m % 2 == 1) ? c[m / 2] : (c[m / 2 - 1] + c[m / 2]) / 2.0;
    }

    private static double interp(final double x, final double[] xp, final double[] fp) {
        final int m = xp.length;
        if (x <= xp[0]) return fp[0];
        if (x >= xp[m - 1]) return fp[m - 1];
        int lo = 0, hi = m - 1;
        while (hi - lo > 1) {
            final int mid = (lo + hi) >>> 1;
            if (xp[mid] <= x) lo = mid; else hi = mid;
        }
        final double w = (x - xp[lo]) / (xp[hi] - xp[lo]);
        return fp[lo] + w * (fp[hi] - fp[lo]);
    }

    /** The gesture resampled every STEP key widths, with the per-point measures the features need. */
    static final class Gesture {
        final Map<Character, double[]> keys = new HashMap<>();
        final char[] keyNames;
        final double arc, duration, startDwell;
        final int n;
        final double[] gx, gy, gt, nspeed, dwell, turn, dheading;
        final double dwellMedian;
        final List<Integer> corners = new ArrayList<>();
        final List<Integer> slowdowns = new ArrayList<>();
        final List<Character> crossed = new ArrayList<>();
        final char[] nearest;
        final double[] initDir, finalDir;

        Gesture(final float[] xs, final float[] ys, final float[] ts, final Map<Character, float[]> keyMap) {
            final List<Character> names = new ArrayList<>(keyMap.keySet());
            Collections.sort(names);
            keyNames = new char[names.size()];
            for (int k = 0; k < keyNames.length; k++) {
                keyNames[k] = names.get(k);
                final float[] c = keyMap.get(names.get(k));
                keys.put(keyNames[k], new double[]{c[0], c[1]});
            }
            final int m = xs.length;
            final double[] t = new double[m];
            double run = 0;
            for (int i = 0; i < m; i++) { run = Math.max(run, ts[i] - ts[0]); t[i] = run; }
            final double[] s = new double[m];
            for (int i = 1; i < m; i++) s[i] = s[i - 1] + Math.hypot(xs[i] - xs[i - 1], ys[i] - ys[i - 1]);
            arc = s[m - 1];
            duration = t[m - 1];
            double sd = duration;
            for (int i = 0; i < m; i++) {
                if (Math.hypot(xs[i] - xs[0], ys[i] - ys[0]) > 0.1) { sd = t[i]; break; }
            }
            startDwell = sd;

            n = Math.max(2, (int) Math.ceil(arc / STEP) + 1);
            // keep the last raw point of each tie in arc length
            final List<Integer> keep = new ArrayList<>();
            for (int i = 0; i < m; i++) {
                if (i == 0 || i == m - 1 || s[i + 1] > s[i]) keep.add(i);
            }
            double[] su = new double[keep.size()], xu = new double[keep.size()], yu = new double[keep.size()], tu = new double[keep.size()];
            for (int k = 0; k < keep.size(); k++) {
                final int i = keep.get(k);
                su[k] = s[i]; xu[k] = xs[i]; yu[k] = ys[i]; tu[k] = t[i];
            }
            if (su.length < 2) {
                su = new double[]{0.0, 1e-6}; xu = new double[]{xs[0], xs[0]};
                yu = new double[]{ys[0], ys[0]}; tu = new double[]{0.0, duration};
            }
            gx = new double[n]; gy = new double[n]; gt = new double[n];
            final double[] grid = new double[n];
            for (int j = 0; j < n; j++) {
                grid[j] = (n == 1) ? 0 : arc * j / (n - 1);
                gx[j] = interp(grid[j], su, xu);
                gy[j] = interp(grid[j], su, yu);
                gt[j] = interp(grid[j], su, tu);
            }
            gt[0] = startDwell;

            final double[] speed = new double[n];
            dwell = new double[n];
            for (int j = 0; j < n; j++) {
                final int lo = Math.max(0, j - SPD_W), hi = Math.min(n - 1, j + SPD_W);
                final double dt = Math.max(gt[hi] - gt[lo], 1.0);
                speed[j] = (grid[hi] - grid[lo]) / dt * 1000.0;
                dwell[j] = gt[hi] - gt[lo];
            }
            final double med = (n > 2 * SPD_W + 2) ? median(speed, SPD_W, n - SPD_W) : median(speed, 0, n);
            nspeed = new double[n];
            for (int j = 0; j < n; j++) nspeed[j] = speed[j] / Math.max(med, 1e-6);
            dwellMedian = median(dwell, 0, n);

            turn = new double[n];
            for (int j = 0; j < n; j++) {
                final int lo = Math.max(0, j - TURN_K), hi = Math.min(n - 1, j + TURN_K);
                final double ax = gx[j] - gx[lo], ay = gy[j] - gy[lo], bx = gx[hi] - gx[j], by = gy[hi] - gy[j];
                final double na = Math.hypot(ax, ay), nb = Math.hypot(bx, by);
                if (na < 1e-6 || nb < 1e-6) { turn[j] = 0.0; continue; }
                final double c = (ax * bx + ay * by) / Math.max(na * nb, 1e-9);
                turn[j] = Math.toDegrees(Math.acos(Math.max(-1.0, Math.min(1.0, c))));
            }
            dheading = new double[n];
            if (n > 2) {
                for (int j = 1; j < n - 1; j++) {
                    final double h0 = Math.atan2(gy[j] - gy[j - 1], gx[j] - gx[j - 1]);
                    final double h1 = Math.atan2(gy[j + 1] - gy[j], gx[j + 1] - gx[j]);
                    double dh = h1 - h0;
                    dh = ((dh + Math.PI) % (2 * Math.PI) + 2 * Math.PI) % (2 * Math.PI) - Math.PI;
                    dheading[j] = dh;
                }
            }
            // corners: turning peaks of 45 degrees or more, at least 0.8 key apart
            for (int j = TURN_K; j < n - TURN_K; j++) {
                if (turn[j] < 45.0) continue;
                double mx = Double.NEGATIVE_INFINITY;
                for (int k = Math.max(0, j - 8); k <= Math.min(n - 1, j + 8); k++) mx = Math.max(mx, turn[k]);
                if (turn[j] >= mx && (corners.isEmpty() || j - corners.get(corners.size() - 1) > 8)) corners.add(j);
            }
            // slowdowns: local speed minima below 0.6 of the median
            if (n > 2 * NEAR) {
                final List<Integer> sl = new ArrayList<>();
                for (int j = NEAR / 2; j < n - NEAR / 2; j++) {
                    if (nspeed[j] >= 0.6) continue;
                    double mn = Double.POSITIVE_INFINITY;
                    for (int k = Math.max(0, j - 3); k <= Math.min(n - 1, j + 3); k++) mn = Math.min(mn, nspeed[k]);
                    if (nspeed[j] <= mn && (sl.isEmpty() || j - sl.get(sl.size() - 1) > 6)) sl.add(j);
                }
                for (final int j : sl) if (j >= 5 && j <= n - 6) slowdowns.add(j);
            }
            // nearest key per point; keys passed within 0.35 key on straight stretches
            nearest = new char[n];
            final int[] bestIdx = new int[keyNames.length];
            final double[] bestD = new double[keyNames.length];
            Arrays.fill(bestD, Double.POSITIVE_INFINITY);
            for (int j = 0; j < n; j++) {
                double nd = Double.POSITIVE_INFINITY;
                for (int k = 0; k < keyNames.length; k++) {
                    final double[] c = keys.get(keyNames[k]);
                    final double d = Math.hypot(gx[j] - c[0], gy[j] - c[1]);
                    if (d < nd) { nd = d; nearest[j] = keyNames[k]; }
                    if (d < bestD[k]) { bestD[k] = d; bestIdx[k] = j; }
                }
            }
            for (int k = 0; k < keyNames.length; k++) {
                final int j = bestIdx[k];
                if (bestD[k] < 0.35 && j >= 3 && j <= n - 4 && turn[j] < 20) crossed.add(keyNames[k]);
            }
            double[] id = null, fd = null;
            for (int j = 0; j < n; j++) {
                if (Math.hypot(gx[j] - gx[0], gy[j] - gy[0]) >= 0.5) { id = new double[]{gx[j] - gx[0], gy[j] - gy[0]}; break; }
            }
            for (int j = n - 1; j >= 0; j--) {
                if (Math.hypot(gx[j] - gx[n - 1], gy[j] - gy[n - 1]) >= 0.5) { fd = new double[]{gx[n - 1] - gx[j], gy[n - 1] - gy[j]}; break; }
            }
            initDir = id; finalDir = fd;
        }

        /** Feature vector in FEATURES order, or null when the word has no letters on this layout. */
        double[] features(final String word) {
            final String letters = fold(word);
            final StringBuilder seqB = new StringBuilder();
            final List<Boolean> dbl = new ArrayList<>();
            for (int i = 0; i < letters.length(); i++) {
                final char ch = letters.charAt(i);
                if (!keys.containsKey(ch)) continue;
                if (seqB.length() > 0 && seqB.charAt(seqB.length() - 1) == ch) { dbl.set(dbl.size() - 1, true); continue; }
                seqB.append(ch); dbl.add(false);
            }
            final int L = seqB.length();
            if (L == 0) return null;
            final double[][] C = new double[L][];
            for (int l = 0; l < L; l++) C[l] = keys.get(seqB.charAt(l));

            // monotone alignment of letters to resampled points (minimum total distance)
            final double[][] acc = new double[L][n];
            for (int l = 0; l < L; l++) {
                double best = Double.POSITIVE_INFINITY;
                for (int j = 0; j < n; j++) {
                    final double d = Math.hypot(gx[j] - C[l][0], gy[j] - C[l][1]);
                    if (l == 0) acc[l][j] = d;
                    else { best = Math.min(best, acc[l - 1][j]); acc[l][j] = d + best; }
                }
            }
            final int[] idx = new int[L];
            idx[L - 1] = argmin(acc[L - 1], n);
            for (int l = L - 2; l >= 0; l--) idx[l] = argmin(acc[l], idx[l + 1] + 1);
            final double[] ad = new double[L];
            for (int l = 0; l < L; l++) ad[l] = Math.hypot(gx[idx[l]] - C[l][0], gy[idx[l]] - C[l][1]);

            final double[] f = new double[FEATURES.length];
            final int nLetters = letters.length();
            int nDouble = 0;
            for (final boolean b : dbl) if (b) nDouble++;
            final double startD = Math.hypot(gx[0] - C[0][0], gy[0] - C[0][1]);
            final double endD = Math.hypot(gx[n - 1] - C[L - 1][0], gy[n - 1] - C[L - 1][1]);
            double midMean = 0, midMax = 0, midOk = 1.0;
            if (L > 2) {
                double sum = 0, mx = Double.NEGATIVE_INFINITY; int ok = 0;
                for (int l = 1; l < L - 1; l++) {
                    sum += ad[l]; mx = Math.max(mx, ad[l]);
                    if (nearest[idx[l]] == seqB.charAt(l)) ok++;
                }
                midMean = sum / (L - 2); midMax = mx; midOk = (double) ok / (L - 2);
            }
            double ideal = 0;
            for (int l = 1; l < L; l++) ideal += Math.hypot(C[l][0] - C[l - 1][0], C[l][1] - C[l - 1][1]);
            final double logLen = Math.log((arc + 0.3) / (ideal + 0.3));
            double midSpeed = 0, midDwell = 0;
            final double md = Math.max(dwellMedian, 1.0);
            if (L > 2) {
                for (int l = 1; l < L - 1; l++) {
                    midSpeed += Math.log(Math.max(nspeed[idx[l]], 0.05));
                    midDwell += Math.log(Math.max(dwell[idx[l]], 1.0) / md);
                }
                midSpeed /= (L - 2); midDwell /= (L - 2);
            }
            int unexplSlow = 0, unexplCorner = 0;
            for (final int j : slowdowns) if (!explained(idx, j)) unexplSlow++;
            for (final int j : corners) if (!explained(idx, j)) unexplCorner++;
            int miss = 0, silent = 0;
            for (int l = 1; l < L - 1; l++) {
                final double it = angle(C[l][0] - C[l - 1][0], C[l][1] - C[l - 1][1], C[l + 1][0] - C[l][0], C[l + 1][1] - C[l][1]);
                final int j = idx[l];
                double local = Double.NEGATIVE_INFINITY;
                for (int k = Math.max(0, j - 5); k <= Math.min(n - 1, j + 5); k++) local = Math.max(local, turn[k]);
                if (!Double.isNaN(it) && it > 60 && local < 30) miss++;
                if (turn[j] < 20 && nspeed[j] > 0.8) silent++;
            }
            final double silentFrac = L > 2 ? (double) silent / (L - 2) : 0.0;
            final Set<Character> all = new HashSet<>(), middle = new HashSet<>();
            for (int l = 0; l < L; l++) { all.add(seqB.charAt(l)); if (l > 0 && l < L - 1) middle.add(seqB.charAt(l)); }
            int crossedAbsent = 0, crossedPresent = 0;
            for (final char k : crossed) { if (!all.contains(k)) crossedAbsent++; if (middle.contains(k)) crossedPresent++; }
            double initHead = Double.NaN, finalHead = Double.NaN;
            if (L > 1 && initDir != null) initHead = angle(initDir[0], initDir[1], C[1][0] - C[0][0], C[1][1] - C[0][1]);
            if (L > 1 && finalDir != null) finalHead = angle(finalDir[0], finalDir[1], C[L - 1][0] - C[L - 2][0], C[L - 1][1] - C[L - 2][1]);
            double endOver = 0, startOver = 0;
            if (L > 1) {
                double ux = C[L - 1][0] - C[L - 2][0], uy = C[L - 1][1] - C[L - 2][1];
                double nu = Math.max(Math.hypot(ux, uy), 1e-9);
                endOver = ((gx[n - 1] - C[L - 1][0]) * ux + (gy[n - 1] - C[L - 1][1]) * uy) / nu;
                double vx = C[1][0] - C[0][0], vy = C[1][1] - C[0][1];
                double nv = Math.max(Math.hypot(vx, vy), 1e-9);
                startOver = ((gx[0] - C[0][0]) * vx + (gy[0] - C[0][1]) * vy) / nv;
            }
            double ovSum = 0; int ovN = 0;
            for (int l = 1; l < L - 1; l++) {
                final double ax = C[l][0] - C[l - 1][0], ay = C[l][1] - C[l - 1][1];
                final double bx = C[l + 1][0] - C[l][0], by = C[l + 1][1] - C[l][1];
                final double it = angle(ax, ay, bx, by);
                if (!Double.isNaN(it) && it > 60) {
                    final double na = Math.hypot(ax, ay), nb = Math.hypot(bx, by);
                    final double bisx = ax / na - bx / nb, bisy = ay / na - by / nb;
                    final double nbis = Math.hypot(bisx, bisy);
                    if (nbis > 1e-6) {
                        ovSum += ((gx[idx[l]] - C[l][0]) * bisx + (gy[idx[l]] - C[l][1]) * bisy) / nbis;
                        ovN++;
                    }
                }
            }
            double doubleDwell = Double.NEGATIVE_INFINITY, doubleLoop = Double.NEGATIVE_INFINITY;
            boolean anyDouble = false;
            for (int l = 0; l < L; l++) {
                if (!dbl.get(l)) continue;
                anyDouble = true;
                final int j = idx[l];
                doubleDwell = Math.max(doubleDwell, Math.log(Math.max(dwell[j], 1.0) / md));
                double h = 0;
                for (int k = Math.max(0, j - 7); k <= Math.min(n - 1, j + 7); k++) h += dheading[k];
                doubleLoop = Math.max(doubleLoop, Math.abs(h));
            }
            if (!anyDouble) { doubleDwell = 0; doubleLoop = 0; }

            final double la = Math.log(arc + 0.2);
            final double one = (L == 1) ? 1.0 : 0.0;
            int i = 0;
            f[i++] = startD;
            f[i++] = endD;
            f[i++] = (nearest[0] == seqB.charAt(0)) ? 1.0 : 0.0;
            f[i++] = (nearest[n - 1] == seqB.charAt(L - 1)) ? 1.0 : 0.0;
            f[i++] = midMean;
            f[i++] = midMax;
            f[i++] = midOk;
            f[i++] = logLen;
            f[i++] = Math.abs(logLen);
            f[i++] = midSpeed;
            f[i++] = unexplSlow;
            f[i++] = unexplCorner;
            f[i++] = miss;
            f[i++] = silentFrac;
            f[i++] = crossedAbsent;
            f[i++] = crossedPresent;
            f[i++] = (Double.isNaN(initHead) ? 0.0 : initHead) / 90.0;
            f[i++] = (Double.isNaN(finalHead) ? 0.0 : finalHead) / 90.0;
            f[i++] = endOver;
            f[i++] = Math.abs(endOver);
            f[i++] = startOver;
            f[i++] = Math.abs(startOver);
            f[i++] = ovN > 0 ? ovSum / ovN : 0.0;
            f[i++] = midDwell;
            f[i++] = nDouble;
            f[i++] = doubleDwell;
            f[i++] = doubleLoop / (2 * Math.PI);
            f[i++] = one;
            f[i++] = one * la;
            f[i++] = one * Math.log1p(duration / 100.0);
            f[i++] = (L == 2) ? la : 0.0;
            f[i++] = Math.log(L) * la;
            return f;
        }

        private static int argmin(final double[] v, final int upTo) {
            int b = 0;
            for (int j = 1; j < upTo; j++) if (v[j] < v[b]) b = j;
            return b;
        }

        private static boolean explained(final int[] idx, final int j) {
            for (final int p : idx) if (Math.abs(p - j) <= NEAR) return true;
            return false;
        }
    }
}

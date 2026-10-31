package org.futo.inputmethod.latin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * re-ranks the tap suggestion strip with the tap positions.
 *
 * For every candidate in the strip, its letters are aligned to the taps (a tap on a key,
 * an extra tap, or a letter with no tap) and the alignment says how well the taps fit
 * the candidate's keys. A conditional logit over FUTO's own ranking, scores and that
 * fit picks a word; it replaces FUTO's choice only when it wins by a clear margin and is
 * not just the same word with a different case, apostrophe or hyphen.
 *
 * Fitted on half of the host tap cases (simulated noisy taps on real sentences plus the
 * tap-scale cases) and tested on the other half.
 */
public final class TapShapeReranker {
    private TapShapeReranker() {}

    // Features: futo_choice, is_typed, typed_x_valid, rel_logscore, log_rank, align_cost, z2_mean, z2_max, mismatch, edge_mismatch, gap_mismatch, ins, dels, len_diff, exact_keys
    private static final float[] MU = {0.07764884129334389f, 0.07949106435728798f, 0.0016528356461554354f, -6.24148784073346f, 1.843202106024244f, 17.68184060443951f, 1.4420270525330996f, 2.5952507543882306f, 0.18296546262181054f, 0.12509899796838952f, 0.001687269722117007f, 1.201749251058848f, 1.9741916600668021f, 0.7724424090079542f, 0.07575496711545746f};
    private static final float[] SD = {0.26761819682970256f, 0.27050367018885496f, 0.040621470452522686f, 7.022814100393983f, 0.7367860367292528f, 9.859202682452985f, 1.349491762006482f, 2.3614444328794173f, 0.45839667406572476f, 0.37466123316738836f, 0.041041721760731203f, 1.306679752904918f, 1.6381248233979808f, 1.8827835769869026f, 0.26460565489423077f};
    private static final float[] W = {0.29224098210715604f, -0.30557396321377245f, -0.09130973385350812f, 1.4726835936281053f, -1.3014653321989509f, -1.8071958287770749f, 0.04733610727888329f, -0.21037023506638636f, 0.2586050035003475f, 0.21942413923215168f, -0.15740669530570642f, -0.7092705787607365f, -0.8295359567340251f, -0.22949740124718204f, -0.10453818770676682f};
    private static final float MARGIN = 3.0f;

    // Touch-down spread around a key centre, in key widths and key heights
    private static final float SX = 0.32f, SY = 0.17f;
    private static final float INS = 4.0f, DEL = 4.0f, CAP = 40.0f;

    /**
     * @param tx, ty     tap positions in key widths / key heights (any origin shared with keys)
     * @param keys       letter key centres a..z in the same units
     * @param words      the strip, typed word first, in FUTO's order
     * @param scores     FUTO's scores for the strip entries
     * @param futoChoice index FUTO would commit (1 if it autocorrects, else 0)
     * @param typedValid whether FUTO found the typed word valid
     * @return the index to commit instead of futoChoice, or -1 to keep FUTO's choice
     */
    public static int choose(final float[] tx, final float[] ty, final Map<Character, float[]> keys,
                             final String[] words, final int[] scores, final int futoChoice,
                             final boolean typedValid) {
        return choose(tx, ty, keys, words, scores, futoChoice, typedValid, null);
    }

    /**
     * As above. If outScores is not null (same length as words) it receives every scored word's
     * score, NaN for words not scored (case duplicates, no letters, or an early exit).
     */
    public static int choose(final float[] tx, final float[] ty, final Map<Character, float[]> keys,
                             final String[] words, final int[] scores, final int futoChoice,
                             final boolean typedValid, final double[] outScores) {
        if (outScores != null) java.util.Arrays.fill(outScores, Double.NaN);
        final int n = Math.min(tx.length, ty.length);
        if (n == 0 || words.length < 2 || futoChoice < 0 || futoChoice >= words.length) return -1;

        // Merge case variants: the first spelling and rank stay, the first non-typed score counts
        final Map<String, Integer> firstIndex = new HashMap<>();
        final List<Integer> order = new ArrayList<>();
        final List<Double> logScore = new ArrayList<>();
        final List<Boolean> typed = new ArrayList<>();
        for (int i = 0; i < words.length; i++) {
            final String lw = words[i].toLowerCase(Locale.ROOT);
            Integer k = firstIndex.get(lw);
            if (k == null) {
                k = order.size();
                firstIndex.put(lw, k);
                order.add(i);
                logScore.add(null);
                typed.add(i == 0);
            } else if (i == 0) {
                typed.set(k, true);
            }
            if (i > 0 && logScore.get(k) == null) logScore.set(k, Math.log(Math.max(scores[i], 1)));
        }
        double top = Double.NEGATIVE_INFINITY;
        for (final Double s : logScore) if (s != null && s > top) top = s;
        if (top == Double.NEGATIVE_INFINITY) top = 0.0;

        final String futoLower = words[futoChoice].toLowerCase(Locale.ROOT);
        double best = Double.NEGATIVE_INFINITY, futoScore = Double.NaN;
        int bestIndex = -1;
        final float[] f = new float[W.length];
        for (int k = 0; k < order.size(); k++) {
            final int i = order.get(k);
            if (!geometry(tx, ty, n, keys, words[i], f)) continue;
            final boolean isTyped = typed.get(k);
            final boolean isFuto = words[i].toLowerCase(Locale.ROOT).equals(futoLower);
            final Double ls = logScore.get(k);
            // futo_choice, is_typed, typed_x_valid, rel_logscore, log_rank
            f[0] = isFuto ? 1f : 0f;
            f[1] = isTyped ? 1f : 0f;
            f[2] = isTyped && typedValid ? 1f : 0f;
            f[3] = ls != null ? (float) (ls - top) : -30f;
            f[4] = (float) Math.log(i + 1);
            double s = 0;
            for (int j = 0; j < W.length; j++) s += W[j] * (f[j] - MU[j]) / SD[j];
            if (outScores != null) outScores[i] = s;
            if (s > best) { best = s; bestIndex = i; }
            if (isFuto) futoScore = s;
        }
        if (bestIndex < 0 || Double.isNaN(futoScore)) return -1;
        if (words[bestIndex].toLowerCase(Locale.ROOT).equals(futoLower)) return -1;
        if (best - futoScore < MARGIN) return -1;
        if (bare(words[bestIndex]).equals(bare(words[futoChoice]))) return -1;
        return bestIndex;
    }

    private static String bare(final String w) {
        return w.toLowerCase(Locale.ROOT).replace("'", "").replace("-", "");
    }

    /** Fills f[5..14] with the alignment features; false when the word has no letter keys. */
    private static boolean geometry(final float[] tx, final float[] ty, final int n,
                                    final Map<Character, float[]> keys, final String word, final float[] f) {
        final List<float[]> letters = new ArrayList<>();
        final String lw = word.toLowerCase(Locale.ROOT);
        for (int i = 0; i < lw.length(); i++) {
            final float[] key = keys.get(lw.charAt(i));
            if (key != null) letters.add(key);
        }
        final int m = letters.size();
        if (m == 0) return false;
        final double[][] d = new double[n + 1][m + 1];
        final byte[][] back = new byte[n + 1][m + 1];
        for (int i = 0; i <= n; i++) {
            for (int j = 0; j <= m; j++) {
                if (i == 0 && j == 0) continue;
                double v = Double.POSITIVE_INFINITY;
                byte b = 0;
                if (i > 0 && j > 0) {
                    v = d[i - 1][j - 1] + Math.min(z2(tx[i - 1], ty[i - 1], letters.get(j - 1)), CAP);
                }
                if (i > 0 && d[i - 1][j] + INS < v) { v = d[i - 1][j] + INS; b = 1; }
                if (j > 0 && d[i][j - 1] + DEL < v) { v = d[i][j - 1] + DEL; b = 2; }
                d[i][j] = v;
                back[i][j] = b;
            }
        }
        int i = n, j = m, ins = 0, dels = 0, mism = 0, edge = 0, gap = 0, subs = 0;
        double sum = 0, max = 0;
        while (i > 0 || j > 0) {
            final byte b = back[i][j];
            if (b == 0) {
                final float x = tx[i - 1], y = ty[i - 1];
                final float[] key = letters.get(j - 1);
                final double z = z2(x, y, key);
                sum += z; max = subs == 0 ? z : Math.max(max, z); subs++;
                float[] near = null;
                double nd = Double.POSITIVE_INFINITY;
                for (final float[] k : keys.values()) {
                    final double dd = (x - k[0]) * (x - k[0]) + (y - k[1]) * (y - k[1]);
                    if (dd < nd) { nd = dd; near = k; }
                }
                if (near != key) {
                    mism++;
                    final float dx = Math.abs(x - near[0]), dy = Math.abs(y - near[1]);
                    if (dx > 0.3f || dy > 0.3f) edge++;
                    if (dy > 0.4f) gap++;
                }
                i--; j--;
            } else if (b == 1) { ins++; i--; } else { dels++; j--; }
        }
        f[5] = (float) d[n][m];
        f[6] = subs > 0 ? (float) (sum / subs) : 0f;
        f[7] = (float) max;
        f[8] = mism;
        f[9] = edge;
        f[10] = gap;
        f[11] = ins;
        f[12] = dels;
        f[13] = m - n;
        f[14] = mism == 0 && ins == 0 && dels == 0 ? 1f : 0f;
        return true;
    }

    private static double z2(final float x, final float y, final float[] key) {
        final double zx = (x - key[0]) / SX, zy = (y - key[1]) / SY;
        return zx * zx + zy * zy;
    }
}

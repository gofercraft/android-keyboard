package org.futo.inputmethod.latin;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Second stage after TapShapeReranker (TapShapeReranker): re-picks the tap word with
 * 09's tap fit plus how common each candidate is (main-dictionary frequency) and how well it
 * follows the previous words (FUTO's transformer, p(first token | context) / token count, the
 * value LanguageModel.firstTokenProbabilities returns).
 *
 * The chosen word is replaced only when another candidate wins by MARGIN, is not the same word
 * with a different case, apostrophe or hyphen, and neither word is a two-word split.
 *
 * Fitted on the host's simulated noisy taps on real sentences (sim_taps, after the earlier tap changes).
 * Two-fold check on sim_taps: 101 fixed, 9 broken of 5,998; tap-scale cases: 120 fixed, 26 broken.
 *
 */
public final class TapLmReranker {
    private TapLmReranker() {}

    // Features: chosen, typed, typed_valid_x, rel_lscore, log_rank, align_cost, z2_mean, z2_max, mismatch, edge_mismatch,
    // gap_mismatch, ins, dels, len_diff, exact_keys, logf, oov, typed_oov, rel_lm
    private static final double[] MU = {0.07913736647163025, 0.0786005690053143, 0.000979655376026625, -4.576117363799607, 1.8387345281662357, 18.25137179505025, 1.9864937845569426, 3.5313568841944885, 0.25416018036394866, 0.1727414246604756, 0.0019458908153953513, 0.948722422030168, 1.947930645767352, 0.999208223737184, 0.07225293896612808, 4.359337058671215, 0.07834559020881421, 0.03214074829566804, -5.702703904984793};
    private static final double[] SD = {0.2699530408042688, 0.26911432532584323, 0.0312841128040907, 4.792266088150192, 0.7317816798559685, 9.575573210849528, 1.2646243308223692, 2.1377422769118946, 0.5210098248688146, 0.4278797916193916, 0.044069313728132165, 1.117482118713733, 1.6275628742732169, 1.85493320535509, 0.258906261598088, 1.3849662336418012, 0.26871464277384677, 0.1763738102650452, 4.006264706371909};
    private static final double[] W = {0.05599459325572654, 0.04942234073185773, -0.04016288091962516, 0.6671256403804005, -1.4070574868386816, -1.9048858004831901, -0.13427239717252493, -0.07993445758379557, 0.6113115060422383, 0.010644101295164292, -0.22616406354141438, -1.2874475984150229, -0.6381109393489137, 0.2157134253150637, -0.12050106292666173, 2.291063146376187, 0.4500750100467581, -0.6976697163612086, 1.3481158679384515};
    /** Log score given to entries FUTO scores Integer.MAX_VALUE (whitelist, typed word), as in the fit. */
    private static final double SCORE_FILL = 14.823867668750161;
    public static final double MARGIN = 2.0;
    private static final double MIN_P = 1e-9;

    /**
     * @param tx, ty     tap positions in key pitches (TapShapeReranker's units)
     * @param keys       letter key centres a..z in the same units
     * @param words      the strip, typed word first
     * @param scores     FUTO's scores for the strip entries
     * @param typedWord  the word as typed
     * @param chosen     index space would commit (1 if it autocorrects, else 0)
     * @param typedValid whether FUTO found the typed word valid
     * @param freq       main-dictionary frequency per entry (any case), or a negative value if not in it
     * @param lmProb     language model probability per entry
     * @return the index to commit instead of chosen, or -1 to keep it
     */
    public static int choose(final float[] tx, final float[] ty, final Map<Character, float[]> keys,
                             final String[] words, final int[] scores, final String typedWord,
                             final int chosen, final boolean typedValid, final int[] freq, final float[] lmProb) {
        final int n = Math.min(tx.length, ty.length);
        if (n == 0 || words.length < 2 || chosen < 0 || chosen >= words.length) return -1;
        if (words[chosen].indexOf(' ') >= 0) return -1;
        final String typedLower = typedWord.toLowerCase(Locale.ROOT);
        final String chosenLower = words[chosen].toLowerCase(Locale.ROOT);

        // The first spelling of each word (ignoring case) is scored, at its own strip position
        final Map<String, Integer> seen = new HashMap<>();
        final double[][] f = new double[words.length][];
        final float[] g = new float[15];
        double topScore = Double.NEGATIVE_INFINITY, topLm = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < words.length; i++) {
            final String lw = words[i].toLowerCase(Locale.ROOT);
            if (seen.containsKey(lw)) continue;
            seen.put(lw, i);
            if (!TapShapeReranker.geometry(tx, ty, n, keys, words[i], g)) continue;
            final double[] x = new double[W.length];
            final boolean isTyped = lw.equals(typedLower);
            final boolean oov = freq[i] < 0;
            final double ls = scores[i] == Integer.MAX_VALUE ? SCORE_FILL : Math.log(Math.max(scores[i], 1));
            x[0] = lw.equals(chosenLower) ? 1 : 0;
            x[1] = isTyped ? 1 : 0;
            x[2] = isTyped && typedValid ? 1 : 0;
            x[3] = ls;
            x[4] = Math.log1p(i);
            for (int j = 5; j < 15; j++) x[j] = g[j];
            x[15] = Math.log1p(Math.max(freq[i], 0));
            x[16] = oov ? 1 : 0;
            x[17] = isTyped && oov ? 1 : 0;
            x[18] = Math.log(Math.max(lmProb[i], MIN_P));
            topScore = Math.max(topScore, ls);
            topLm = Math.max(topLm, x[18]);
            f[i] = x;
        }
        if (f[chosen] == null) return -1;

        double best = Double.NEGATIVE_INFINITY, chosenScore = Double.NaN;
        int bestIndex = -1;
        for (int i = 0; i < words.length; i++) {
            final double[] x = f[i];
            if (x == null) continue;
            x[3] -= topScore;
            x[18] -= topLm;
            double s = 0;
            for (int j = 0; j < W.length; j++) s += W[j] * (x[j] - MU[j]) / SD[j];
            if (i == chosen) chosenScore = s;
            if (s > best) { best = s; bestIndex = i; }
        }
        if (bestIndex < 0 || bestIndex == chosen || best - chosenScore < MARGIN) return -1;
        if (words[bestIndex].indexOf(' ') >= 0) return -1;
        if (bare(words[bestIndex]).equals(bare(words[chosen]))) return -1;
        return bestIndex;
    }

    private static String bare(final String w) {
        return w.toLowerCase(Locale.ROOT).replace("'", "").replace("-", "");
    }
}

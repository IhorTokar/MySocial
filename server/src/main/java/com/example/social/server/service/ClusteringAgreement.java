package com.example.social.server.service;

import java.util.HashMap;
import java.util.Map;

/**
 * Наскільки два розбиття тих самих користувачів на групи збігаються — знайдене алгоритмом і справжнє.
 * Номери груп не мають значення (лише те, хто з ким разом), тож обидві міри інваріантні до перейменування.
 *
 * <ul>
 *   <li><b>NMI</b> (нормована взаємна інформація): MI(U, V) / ((H(U) + H(V)) / 2); 1 — розбиття збігаються,
 *       0 — незалежні;</li>
 *   <li><b>ARI</b> (скоригований індекс Ренда): частка пар користувачів, щодо яких розбиття погоджуються
 *       («разом» / «окремо»), з поправкою на випадковий збіг; 1 — збіг, ≈ 0 — як випадкове розбиття.</li>
 * </ul>
 * Формули ті самі, що в scikit-learn (normalized_mutual_info_score з арифметичним середнім, adjusted_rand_score).
 */
public final class ClusteringAgreement {

    private ClusteringAgreement() {
    }

    /** Порівнюються лише користувачі, які є в обох розбиттях. */
    public static <A, B> double nmi(Map<Long, A> truth, Map<Long, B> found) {
        Table t = table(truth, found);
        if (t.n == 0) {
            return 0.0;
        }
        double hu = entropy(t.rows, t.n);
        double hv = entropy(t.cols, t.n);
        if (hu == 0 && hv == 0) {
            return 1.0;   // обидва — одна група: збігаються
        }
        double mi = 0;
        for (Map.Entry<Object, Map<Object, Integer>> row : t.cells.entrySet()) {
            for (Map.Entry<Object, Integer> cell : row.getValue().entrySet()) {
                double pij = cell.getValue() / (double) t.n;
                double pi = t.rows.get(row.getKey()) / (double) t.n;
                double pj = t.cols.get(cell.getKey()) / (double) t.n;
                mi += pij * Math.log(pij / (pi * pj));
            }
        }
        double denominator = (hu + hv) / 2;
        return denominator == 0 ? 0.0 : Math.max(0, Math.min(1, mi / denominator));
    }

    public static <A, B> double ari(Map<Long, A> truth, Map<Long, B> found) {
        Table t = table(truth, found);
        if (t.n < 2) {
            return 0.0;
        }
        double index = 0;
        for (Map<Object, Integer> row : t.cells.values()) {
            for (int count : row.values()) {
                index += pairs(count);
            }
        }
        double a = t.rows.values().stream().mapToDouble(ClusteringAgreement::pairs).sum();
        double b = t.cols.values().stream().mapToDouble(ClusteringAgreement::pairs).sum();
        double expected = a * b / pairs(t.n);
        double max = (a + b) / 2;
        if (max == expected) {
            return 1.0;   // вироджені розбиття (усі разом або всі окремо в обох)
        }
        return (index - expected) / (max - expected);
    }

    /**
     * Чистота: частка користувачів, що потрапили в знайдену групу разом з більшістю «своїх»
     * (для кожної знайденої групи береться найчисленніша справжня). Наочна, але не карає за дрібнення.
     */
    public static <A, B> double purity(Map<Long, A> truth, Map<Long, B> found) {
        Table t = table(truth, found);
        if (t.n == 0) {
            return 0.0;
        }
        Map<Object, Integer> best = new HashMap<>();
        t.cells.forEach((u, row) -> row.forEach((v, count) -> best.merge(v, count, Math::max)));
        return best.values().stream().mapToInt(Integer::intValue).sum() / (double) t.n;
    }

    private static double pairs(int n) {
        return n * (n - 1) / 2.0;
    }

    private static double entropy(Map<Object, Integer> counts, int n) {
        double h = 0;
        for (int c : counts.values()) {
            double p = c / (double) n;
            h -= p * Math.log(p);
        }
        return h;
    }

    /** Таблиця спряженості: скільки користувачів у групі i справжнього і групі j знайденого розбиття. */
    private static final class Table {
        final Map<Object, Map<Object, Integer>> cells = new HashMap<>();
        final Map<Object, Integer> rows = new HashMap<>();
        final Map<Object, Integer> cols = new HashMap<>();
        int n;
    }

    private static <A, B> Table table(Map<Long, A> truth, Map<Long, B> found) {
        Table t = new Table();
        truth.forEach((user, u) -> {
            B v = found.get(user);
            if (u == null || v == null) {
                return;
            }
            t.cells.computeIfAbsent(u, k -> new HashMap<>()).merge(v, 1, Integer::sum);
            t.rows.merge(u, 1, Integer::sum);
            t.cols.merge(v, 1, Integer::sum);
            t.n++;
        });
        return t;
    }
}
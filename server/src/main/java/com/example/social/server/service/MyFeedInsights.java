package com.example.social.server.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Обчислення для сторінки «Моя стрічка» без звернень до баз — окремо, щоб їх можна було перевірити тестами. */
public final class MyFeedInsights {

    private MyFeedInsights() {
    }

    /** Тег: частка серед моїх вподобань і серед усіх вподобань мережі. */
    public record TagShare(String tag, double mine, double network) {
    }

    /**
     * Найчастіші теги моїх вподобань (до limit) і частка тих самих тегів у вподобаннях усієї мережі.
     * Частки рахуються від усіх тегів вподобаних постів (пост із двома тегами дає два «голоси»).
     */
    public static List<TagShare> compareTags(Map<String, Long> mine, Map<String, Long> network, int limit) {
        long mineTotal = mine.values().stream().mapToLong(Long::longValue).sum();
        long networkTotal = network.values().stream().mapToLong(Long::longValue).sum();
        if (mineTotal == 0) {
            return List.of();
        }
        List<Map.Entry<String, Long>> sorted = new ArrayList<>(mine.entrySet());
        sorted.sort(Map.Entry.<String, Long>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey()));
        List<TagShare> result = new ArrayList<>();
        for (Map.Entry<String, Long> e : sorted.subList(0, Math.min(limit, sorted.size()))) {
            double n = networkTotal == 0 ? 0 : network.getOrDefault(e.getKey(), 0L) / (double) networkTotal;
            result.add(new TagShare(e.getKey(), round(e.getValue() / (double) mineTotal), round(n)));
        }
        return result;
    }

    /** Частки позитивних, нейтральних і негативних постів (поріг нейтральності — як у показнику ехо-камери). */
    public static Map<String, Double> sentimentShares(List<Double> sentiments) {
        int pos = 0;
        int neu = 0;
        int neg = 0;
        for (Double s : sentiments) {
            if (s == null) {
                continue;
            }
            switch (CommunityAnalysisService.polarity(s)) {
                case 1 -> pos++;
                case -1 -> neg++;
                default -> neu++;
            }
        }
        int total = pos + neu + neg;
        Map<String, Double> result = new LinkedHashMap<>();
        result.put("positive", total == 0 ? 0.0 : round(pos / (double) total));
        result.put("neutral", total == 0 ? 0.0 : round(neu / (double) total));
        result.put("negative", total == 0 ? 0.0 : round(neg / (double) total));
        result.put("posts", (double) total);
        return result;
    }

    /**
     * Короткі висновки простими словами.
     *
     * @param ownShare      частка постів стрічки зі своєї спільноти (null — спільнота не визначена)
     * @param randomShare   якою була б ця частка у стрічці з випадкових постів
     * @param outsideShare  налаштування «частка постів інших спільнот» (0 — розбавлення вимкнене)
     * @param topTagShare   частка найчастішого тегу серед моїх вподобань (null — вподобань немає)
     * @param topTag        цей тег
     * @param interests     скільки тем обрано
     */
    public static List<String> hints(Double ownShare, Double randomShare, double outsideShare,
                                     Double topTagShare, String topTag, Double topTagNetworkShare,
                                     int likes, int interests) {
        List<String> hints = new ArrayList<>();
        if (likes == 0 && interests == 0) {
            hints.add("Ви ще нічого не вподобали і не обрали теми, тож стрічка поки складається з популярних "
                    + "і нових постів. Оберіть теми або вподобайте кілька постів — і вона стане вашою.");
        } else if (likes == 0) {
            hints.add("Поки у вас немає вподобань, стрічка підбирає пости за обраними темами.");
        }
        if (ownShare != null && randomShare != null) {
            String own = percent(ownShare);
            // порівнюємо з випадковою стрічкою: для маленької спільноти й 20% — це вже набагато більше, ніж навмання
            double times = randomShare > 0 ? ownShare / randomShare : Double.POSITIVE_INFINITY;
            String vsRandom = "якби пости бралися навмання, таких було б " + percent(randomShare);
            if (ownShare >= 0.8) {
                hints.add("Майже вся ваша стрічка (" + own + ") — з вашої спільноти; " + vsRandom
                        + ". Це і є «фільтрувальна бульбашка»"
                        + (outsideShare > 0
                        ? "; розбавлення постами інших спільнот уже ввімкнене (" + percent(outsideShare) + ")."
                        : "; адміністратор може ввімкнути розбавлення постами інших спільнот."));
            } else if (ownShare >= 0.1 && times >= 3) {
                hints.add(own + " стрічки — з вашої спільноти; " + vsRandom + ". Стрічка помітно тяжіє до "
                        + "вашого кола, але інші спільноти теж трапляються.");
            } else {
                hints.add("Ваша стрічка різноманітна: зі своєї спільноти " + own + ", " + vsRandom + ".");
            }
        }
        if (topTagShare != null && topTagShare >= 0.5) {
            hints.add("Половина і більше того, що ви вподобуєте (" + percent(topTagShare) + "), — про #" + topTag
                    + (topTagNetworkShare != null ? "; у мережі загалом — " + percent(topTagNetworkShare) + "." : "."));
        }
        return hints;
    }

    /** 20%, а для малих часток — з десятими (0.3%), щоб не показувати «0%» там, де щось є. */
    static String percent(double share) {
        return share > 0 && share < 0.01
                ? String.format(Locale.ROOT, "%.1f%%", share * 100)
                : String.format(Locale.ROOT, "%.0f%%", share * 100);
    }

    static double round(double v) {
        return Math.round(v * 1000) / 1000.0;
    }
}

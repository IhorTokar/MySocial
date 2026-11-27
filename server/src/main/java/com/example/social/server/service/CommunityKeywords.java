package com.example.social.server.service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Ключові слова спільноти з текстів її постів (TF-IDF): слово важливе для спільноти, якщо воно часто
 * трапляється в її постах і рідко — в постах решти мережі. Так спільноту описують її власні слова,
 * а не лише теги, які ставлять автори.
 *
 * <p>Оцінка слова: (частка постів спільноти, де воно є) × ln(усі пости / пости мережі з цим словом).
 * Українські слова змінюються за відмінками («подорож», «подорожі», «подорожей»), тому слова
 * групуються за спрощеною основою — першими {@value #STEM} літерами; показується найчастіша форма.
 */
public final class CommunityKeywords {

    /** Довжина спрощеної основи слова. */
    static final int STEM = 6;
    /** Коротші слова — здебільшого службові (прийменники, займенники). */
    static final int MIN_WORD = 4;
    /** Слово має трапитись щонайменше у стількох постах спільноти — інакше це випадковість. */
    static final int MIN_POSTS = 2;

    private static final Pattern NOT_LETTER = Pattern.compile("[^\\p{L}'ʼ’]+");

    /** Поширені слова, що не описують тему (довші за 3 літери; коротші відкидаються й так). */
    static final Set<String> STOP_WORDS = Set.of(
            "також", "тільки", "лише", "дуже", "більше", "менше", "коли", "через", "після", "перед", "якщо",
            "вона", "воно", "вони", "мене", "мені", "тебе", "тобі", "його", "свій", "своя", "свої", "свого",
            "своїх", "своїм", "наша", "наші", "наших", "ваша", "ваші", "ваших", "весь", "всіх", "усіх",
            "всім", "усім", "кожен", "кожна", "який", "яких", "якого", "якій", "буде", "була", "було",
            "були", "бути", "можна", "треба", "тому", "отже", "навіть", "саме", "просто", "сьогодні",
            "завжди", "ніколи", "разом", "одна", "один", "одне", "цього", "цієї", "того", "такий", "така",
            "таке", "такі", "щось", "хтось", "десь", "тепер", "зараз", "поки", "потім", "інші", "інших",
            "іншого", "інша", "інший", "своє", "мого", "моїх", "наче", "ніби", "хоча", "біля", "навколо",
            "майже", "трохи", "багато", "мало", "кілька", "декілька", "перший", "перша", "друге", "нові",
            "новий", "нова", "нове", "нових", "день", "року", "тиждень", "думаю", "знаю", "здається");

    private CommunityKeywords() {
    }

    /** Частоти слів у всій мережі: у скількох постах трапляється кожна основа. */
    public record Index(Map<String, Integer> postsWithStem, int posts) {
    }

    public static Index index(Collection<String> allTexts) {
        Map<String, Integer> df = new HashMap<>();
        int n = 0;
        for (String text : allTexts) {
            n++;
            for (String stem : stems(text).keySet()) {
                df.merge(stem, 1, Integer::sum);
            }
        }
        return new Index(df, n);
    }

    /** Ключове слово й оцінка TF-IDF. */
    public record Keyword(String word, double score, int posts) {
    }

    /**
     * @param groupTexts тексти постів спільноти (вони ж мають бути серед текстів, з яких зроблено index)
     * @return до limit слів, від найважливішого
     */
    public static List<Keyword> top(Collection<String> groupTexts, Index index, int limit) {
        if (groupTexts.isEmpty() || index.posts() == 0) {
            return List.of();
        }
        Map<String, Integer> groupDf = new HashMap<>();
        Map<String, Map<String, Integer>> forms = new HashMap<>();
        for (String text : groupTexts) {
            for (Map.Entry<String, Map<String, Integer>> e : stems(text).entrySet()) {
                groupDf.merge(e.getKey(), 1, Integer::sum);
                Map<String, Integer> f = forms.computeIfAbsent(e.getKey(), k -> new HashMap<>());
                e.getValue().forEach((form, c) -> f.merge(form, c, Integer::sum));
            }
        }
        int groupPosts = groupTexts.size();
        List<Keyword> result = new ArrayList<>();
        for (Map.Entry<String, Integer> e : groupDf.entrySet()) {
            if (e.getValue() < MIN_POSTS) {
                continue;
            }
            int df = Math.max(e.getValue(), index.postsWithStem().getOrDefault(e.getKey(), 0));
            double idf = Math.log((double) index.posts() / df);
            double score = (double) e.getValue() / groupPosts * idf;
            if (score > 0) {
                result.add(new Keyword(mostFrequent(forms.get(e.getKey())), Math.round(score * 1000) / 1000.0,
                        e.getValue()));
            }
        }
        result.sort(Comparator.comparingDouble(Keyword::score).reversed().thenComparing(Keyword::word));
        return result.size() > limit ? new ArrayList<>(result.subList(0, limit)) : result;
    }

    /** Основа → {форма слова → скільки разів} для одного тексту. */
    static Map<String, Map<String, Integer>> stems(String text) {
        Map<String, Map<String, Integer>> result = new HashMap<>();
        if (text == null) {
            return result;
        }
        for (String raw : NOT_LETTER.split(text.toLowerCase(Locale.ROOT))) {
            String word = raw.replaceAll("^['ʼ’]+|['ʼ’]+$", "");
            if (word.length() < MIN_WORD || STOP_WORDS.contains(word)) {
                continue;
            }
            String stem = word.length() > STEM ? word.substring(0, STEM) : word;
            result.computeIfAbsent(stem, k -> new HashMap<>()).merge(word, 1, Integer::sum);
        }
        return result;
    }

    private static String mostFrequent(Map<String, Integer> forms) {
        // найчастіша форма; за рівної частоти — перша за абеткою
        Comparator<Map.Entry<String, Integer>> order = Map.Entry.comparingByValue();
        order = order.thenComparing(Map.Entry::getKey, Comparator.reverseOrder());
        return forms.entrySet().stream().max(order).map(Map.Entry::getKey).orElse("");
    }

    /** Унікальні основи тексту — для тестів. */
    static Set<String> stemSet(String text) {
        return new HashSet<>(stems(text).keySet());
    }
}

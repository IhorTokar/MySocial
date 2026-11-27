package com.example.social.server.service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Розбавлення стрічки постами інших спільнот: гарантує, що серед перших j постів щонайменше
 * round(share · j) — з чужих спільнот, рівномірно по всій стрічці.
 *
 * <p>Основний список (за гібридною оцінкою, можливо після MMR) лишається як є, доки квоту виконано;
 * коли ні — на цю позицію ставиться найрелевантніший ще не показаний пост іншої спільноти.
 * Пости інших спільнот, що вже є в основному списку, теж зараховуються до квоти, тож коли стрічка
 * сама по собі різноманітна, нічого не змінюється. На відміну від MMR, який розводить пости за змістом
 * у межах кількох десятків найкращих кандидатів, тут чужі пости беруться з усіх кандидатів.
 */
public final class CommunityDiversifier {

    private CommunityDiversifier() {
    }

    /**
     * @param main          основний порядок стрічки
     * @param outsideRanked кандидати з інших спільнот за спаданням релевантності
     * @param isOutside     чи пост з іншої спільноти
     * @param key           ідентифікатор поста (для пропуску повторів)
     * @param k             довжина результату (менша, якщо постів не вистачить)
     * @param share         частка постів інших спільнот, 0..1; 0 — повертається префікс main
     */
    public static <T> List<T> interleave(List<T> main, List<T> outsideRanked, Predicate<T> isOutside,
                                         Function<T, ?> key, int k, double share) {
        if (share <= 0) {
            return new ArrayList<>(main.subList(0, Math.min(k, main.size())));
        }
        List<T> result = new ArrayList<>();
        Set<Object> used = new HashSet<>();
        int[] mainAt = {0};
        int[] outsideAt = {0};
        int outside = 0;
        while (result.size() < k) {
            int position = result.size() + 1;
            int target = (int) Math.floor(share * position + 0.5);
            T next = null;
            if (outside < target) {
                next = nextUnused(outsideRanked, outsideAt, used, key);
            }
            if (next == null) {
                next = nextUnused(main, mainAt, used, key);
            }
            if (next == null) {
                next = nextUnused(outsideRanked, outsideAt, used, key);   // основний список вичерпано
            }
            if (next == null) {
                break;
            }
            used.add(key.apply(next));
            result.add(next);
            if (isOutside.test(next)) {
                outside++;
            }
        }
        return result;
    }

    private static <T> T nextUnused(List<T> list, int[] at, Set<Object> used, Function<T, ?> key) {
        while (at[0] < list.size()) {
            T item = list.get(at[0]++);
            if (!used.contains(key.apply(item))) {
                return item;
            }
        }
        return null;
    }
}
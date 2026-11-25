package com.example.social.server.service;

import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Допоміжна математика генератора: розподіл Пуассона й транслітерація логінів. */
class SeedGeneratorMathTest {

    @Test
    void poissonMeanMatchesLambda() {
        // λ ≤ 30 — точний алгоритм Кнута, λ > 30 — нормальне наближення; обидві гілки
        for (double lambda : new double[]{0.5, 3, 12, 50}) {
            Random rnd = new Random(1);
            int n = 20_000;
            double sum = 0;
            for (int i = 0; i < n; i++) {
                sum += SeedGeneratorService.poisson(lambda, rnd);
            }
            assertEquals(lambda, sum / n, lambda * 0.03 + 0.02, "середнє для λ = " + lambda);
        }
    }

    @Test
    void poissonOfNonPositiveLambdaIsZero() {
        Random rnd = new Random(1);
        assertEquals(0, SeedGeneratorService.poisson(0, rnd));
        assertEquals(0, SeedGeneratorService.poisson(-2, rnd));
    }

    @Test
    void translitFollowsUkrainianRules() {
        assertEquals("olena", SeedGeneratorService.translit("Олена"));
        assertEquals("shcherbak", SeedGeneratorService.translit("Щербак"));
        assertEquals("iurii", SeedGeneratorService.translit("Юрій"));
        assertEquals("ganna", SeedGeneratorService.translit("Ґанна"));
        assertEquals("mariana", SeedGeneratorService.translit("Мар'яна"));
        assertEquals("ivanpetrenko", SeedGeneratorService.translit("Іван Петренко"), "пробіли й знаки — прибираються");
    }
}
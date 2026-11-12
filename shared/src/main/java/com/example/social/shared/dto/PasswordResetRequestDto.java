package com.example.social.shared.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Заявка «Забув пароль» з екрана входу.
 *
 * @param login   ім'я користувача або email акаунта
 * @param contact як адміністратору зв'язатися з користувачем (телефон, email, Telegram…)
 * @param message довільний коментар
 */
public record PasswordResetRequestDto(@NotBlank @Size(max = 100) String login,
                                      @Size(max = 200) String contact,
                                      @Size(max = 1000) String message) {
}
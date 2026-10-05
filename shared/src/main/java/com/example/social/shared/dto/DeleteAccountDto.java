package com.example.social.shared.dto;

import jakarta.validation.constraints.NotBlank;

public class DeleteAccountDto {

    @NotBlank
    private String password;

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
}
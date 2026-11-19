package com.example.social.shared.dto;

import jakarta.validation.constraints.Size;

/** Ті самі межі, що й в AdminProfileUpdateDto (display_name — VARCHAR(100)). */
public class UpdateProfileDto {

    @Size(max = 100, message = "ім'я — щонайбільше 100 символів")
    private String displayName;

    @Size(max = 2000, message = "«Про себе» — щонайбільше 2000 символів")
    private String aboutMe;

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getAboutMe() {
        return aboutMe;
    }

    public void setAboutMe(String aboutMe) {
        this.aboutMe = aboutMe;
    }
}
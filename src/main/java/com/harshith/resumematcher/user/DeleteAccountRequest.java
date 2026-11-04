package com.harshith.resumematcher.user;

import jakarta.validation.constraints.NotBlank;

/** Deleting an account needs the password again, so a stolen token alone can't do it. */
public record DeleteAccountRequest(@NotBlank String password) {
}

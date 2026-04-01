package com.app.service.rest.usersServer.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(@NotBlank(message = "Username is mandatory")
                              @Size(min = 3, message = "Username too short")
                              String username,

                              @NotBlank(message = "Password is mandatory")
                              @Pattern(regexp = "^(?=.*[a-zA-Z])(?=.*\\d).+$",
                                      message = "Password must contain letters and digits")
                              String password,

                              @NotBlank(message = "Confirm password is mandatory")
                              String passwordConfirm) {
}

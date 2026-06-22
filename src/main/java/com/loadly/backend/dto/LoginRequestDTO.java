package com.loadly.backend.dto;

import lombok.Data;

@Data
public class LoginRequestDTO {
    private String contacto;
    private String password;
}
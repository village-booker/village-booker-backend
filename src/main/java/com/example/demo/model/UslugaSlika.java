package com.example.demo.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity @Data @Table(name="uslugaslika") @AllArgsConstructor @NoArgsConstructor
public class UslugaSlika {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;

    private String url;
    private int redosled;
    private LocalDateTime kreirana_at = LocalDateTime.now();


}

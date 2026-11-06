package com.app.service.rest.usersServer.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import jakarta.persistence.*; // ИМПОРТ ИЗМЕНЕН

@Entity
@Data
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "t_role")
public class Roles {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY) //Делегирование базе данных.База данных сама берет встроенный счетчик, увеличивает его на 1 и присваивает новой строке
    private Long id;

    private String name;
}

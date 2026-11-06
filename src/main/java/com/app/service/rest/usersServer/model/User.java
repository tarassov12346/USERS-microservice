package com.app.service.rest.usersServer.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import jakarta.persistence.*; // ИМПОРТ ИЗМЕНЕН
import java.util.Set;

@Entity
@Data
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "t_user")
public class User {
    /**
     * Использование Long вместо примитива long гарантирует дефолтное значение null.
     * Это критически важно для Hibernate, чтобы он точно отличал новые записи от существующих.
     */
    /**
     *  @Id Указывает, что это поле является уникальным идентификатором (Primary Key) сущности.
     * На его основе Hibernate будет строить кэширование, сессии и связывать таблицы.
     */

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)//Делегирование базе данных.
    private Long id;

    @Column(nullable = false, unique = true)
    private String username;
    //поле password хранит хэш BCrypt, а подтверждение (passwordConfirm) игнорируется БД
    @Column(nullable = false)
    private String password;

    @Transient
    private String passwordConfirm;
    //@ManyToMany — Говорит Hibernate, что это связь «Многие-ко-Многим».
    // У одного пользователя может быть много ролей (User, Admin),
    // и одновременно одна и та же роль (Admin) может быть назначена множеству разных пользователей.
    // В реляционных базах данных такая связь физически не может быть сделана напрямую в двух таблицах,
    // поэтому для неё всегда нужна третья, промежуточная таблица (связующая таблица)
    //fetch = FetchType.LAZY — (Теперь здесь всё идеально!)
    // Стратегия «ленивой» загрузки. Роли не будут вытягиваться из базы автоматически при каждом запросе пользователя.
    // Они подгрузятся, только если ты вызовешь метод с JOIN FETCH в репозитории.
    // Это спасает приложение от проблемы N+1
    // • cascade = {CascadeType.MERGE, CascadeType.DETACH} — Каскадное поведение.
    //	• MERGE: Если ты обновляешь данные пользователя, и у него изменился сет ролей, Hibernate обновит эти связи в связующей таблице.
    //	• DETACH: Если пользователь отвязывается от сессии Hibernate (становится detached), его роли тоже отвязываются от сессии, но остаются в базе.
    //• @JoinTable — Эта аннотация описывает ту самую третью (промежуточную) таблицу, которая будет связывать пользователей и роли.
    //• name = "t_user_roles" — Имя связующей таблицы в моей базе данных.
    //• joinColumns — Настройка колонки для текущей сущности (для класса User) внутри промежуточной таблицы.
    //• name = "user_id" — Имя внешнего ключа (Foreign Key) в таблице t_user_roles, который будет ссылаться на пользователя.
    //• referencedColumnName = "id" — На какое именно поле в таблице t_user ссылается этот ключ (на первичный ключ id).
    //• inverseJoinColumns — Настройка колонки для противоположной сущности (для класса Roles) внутри промежуточной таблицы.
    //• name = "roles_id" — Имя внешнего ключа в таблице t_user_roles, который будет ссылаться на роль.
    //• referencedColumnName = "id" — На какое поле в таблице ролей ссылается этот ключ.
    @ManyToMany(fetch = FetchType.LAZY, cascade =  {CascadeType.MERGE, CascadeType.DETACH})
    @JoinTable(name = "t_user_roles",
            joinColumns = @JoinColumn(name = "user_id", referencedColumnName = "id"),
            inverseJoinColumns = @JoinColumn(name = "roles_id", referencedColumnName = "id")
    )
    private Set<Roles> roles;

    public static User of(String username, String password) {
        User user = new User();
        user.setUsername(username);
        user.setPassword(password);
        return user;
    }

}

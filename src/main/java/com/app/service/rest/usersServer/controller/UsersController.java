package com.app.service.rest.usersServer.controller;

import com.app.service.rest.usersServer.model.User;
import com.app.service.rest.usersServer.userservice.UsersService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class UsersController {
    @Autowired
    UsersService usersService;

    @RequestMapping("/save")
    public boolean save(@RequestBody User newUser) {
        return usersService.saveUser(newUser);
    }

    @RequestMapping("/delete")
    public void doDelete(@RequestParam Long userId) {
        usersService.deleteUser(userId);
    }

    @RequestMapping("/findId")
    public User findId(@RequestParam Long userId){
        return usersService.findUserById(userId);
    }
}

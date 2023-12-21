package com.demo.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class DevController {

    static {
        System.out.println("+++++ static code block +++++");
        System.out.println("MainController.class ClassLoader= " + DevController.class.getClassLoader());
    }

    @GetMapping("/cl")
    public String test() {
        System.out.println("MainController.class ClassLoader= " + DevController.class.getClassLoader());
        return "show classloader";
    }
}

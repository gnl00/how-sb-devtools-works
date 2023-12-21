package com.demo;

import com.demo.config.DevConfig;
import com.demo.controller.DevController;
import org.apache.catalina.core.ApplicationContext;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.devtools.restart.classloader.RestartClassLoader;

@SpringBootApplication
public class Main {
    public static void main(String[] args) {
        System.out.println("Main.class ClassLoader= " + Main.class.getClassLoader());
        SpringApplication.run(Main.class, args);

        System.out.println("+++++ springboot class +++++");
        System.out.println("RestartClassLoader.class ClassLoader= " + RestartClassLoader.class.getClassLoader());
        System.out.println("SpringApplication.class ClassLoader= " + SpringApplication.class.getClassLoader());
        System.out.println("ApplicationContext.class ClassLoader= " + ApplicationContext.class.getClassLoader());
        System.out.println("BeanFactory.class ClassLoader= " + BeanFactory.class.getClassLoader());

        System.out.println("+++++ custom class +++++");
        System.out.println("DevController.class ClassLoader= " + DevController.class.getClassLoader());
        System.out.println("DevConfig.class ClassLoader= " + DevConfig.class.getClassLoader());
    }
}

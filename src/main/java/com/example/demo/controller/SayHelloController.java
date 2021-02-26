package com.example.demo.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.ModelAndView;

@RestController
public class SayHelloController {

    ModelAndView modelAndView=new ModelAndView();

    @GetMapping(value = "hello")
    public String hello(){
        modelAndView.setViewName("index");
        return "modelAndView";
    }
}

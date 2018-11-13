package com.majian.application.controller;

import com.majian.application.service.IMainService;
import com.majian.mvcframework.annotation.MjAutowired;
import com.majian.mvcframework.annotation.MjController;
import com.majian.mvcframework.annotation.MjRequestMapping;
import com.majian.mvcframework.annotation.MjRequestParam;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

@MjController
public class MainController {
    @MjAutowired
    private IMainService mainService;

    @MjRequestMapping("/query")
    public void query(HttpServletRequest request, HttpServletResponse response, @MjRequestParam("name") String name) {
        String s = mainService.get(name);
        try {
            response.getWriter().write(s);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @MjRequestMapping("/add")
    public void add(HttpServletRequest request, HttpServletResponse response, @MjRequestParam("a") Integer a, @MjRequestParam("b") Integer b) {
        try {
            response.getWriter().write(a + "+" + b + "=" + (a + b));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @MjRequestMapping("/delete")
    public void delete(HttpServletRequest request, HttpServletResponse response) {

    }


}

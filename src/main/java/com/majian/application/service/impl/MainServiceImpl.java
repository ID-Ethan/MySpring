package com.majian.application.service.impl;

import com.majian.application.service.IMainService;
import com.majian.mvcframework.annotation.MjService;

@MjService
public class MainServiceImpl implements IMainService {
    public String get(String name) {
        return "Hello "+name+"!";
    }
}

package com.lxbnjupt.hotfixdemo;

/**
 * Created by liuxiaobo on 2018/11/1.
 */

public class BugTest {

    public void getBug() {
        throw new NullPointerException();
    }
}

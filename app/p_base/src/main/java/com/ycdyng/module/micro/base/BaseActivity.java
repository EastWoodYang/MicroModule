package com.ycdyng.module.micro.base;

import android.app.Activity;
import android.os.Bundle;

public class BaseActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_base);

    }
}
package com.eastwood.demo.application;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // can't use [R.string.base] which from microModule ':p_base'.
//        getString(R.string.base);

    }
}

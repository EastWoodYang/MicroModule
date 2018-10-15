package com.eastwood.demo.application;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // can't use [R.string.home] which from microModule ':p_home'.
//        getString(R.string.home);

    }
}

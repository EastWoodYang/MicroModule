package com.ycdyng.module.micro.home;

import android.app.Activity;
import android.os.Bundle;
import android.widget.BaseAdapter;

public class HomeActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_home);

        getString(R.string.common);

        BaseAdapter.class.getName();

    }
}

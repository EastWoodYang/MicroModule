package com.eastwood.demo.library.base;

import android.content.Context;

/**
 * @author eastwood
 * createDate: 2018-11-09
 */
public class Base {

    public void get(Context context) {
        // can't use [R.string.test_code_check_common] which from microModule ':p_common'.
//        context.getString(R.string.test_code_check_common);
    }

}
